import json
from collections import Counter
from copy import deepcopy
from datetime import date
from pathlib import Path

import pytest

from innerflow_v2.reliability.baselines import FaithfulSummaryPolicy, MemoryMessage
from innerflow_v2.reliability.client import Completion
from innerflow_v2.reliability.protocol_v13 import (
    CandidateV13,
    canonical_sha256,
    load_authoring_inventory,
    load_candidate_pool,
    load_candidate_registry,
    validate_authored_candidate_inventory,
    validate_candidate_registry,
)


V2_ROOT = Path(__file__).resolve().parents[2]
INVENTORY_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "manifests"
    / "M0_V13_SIGNED_AUTHORING_INVENTORY.json"
)
CANDIDATES_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "candidates"
    / "M0_V13_AUTHORED_CANDIDATES.json"
)
AUDIT_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "audits"
    / "M0_V13_AUTOMATED_CONFORMANCE_AUDIT.json"
)
REGISTRY_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "registries"
    / "M0_V13_CANDIDATE_REGISTRY_DRAFT.json"
)


def _load_payload() -> list[dict]:
    return json.loads(CANDIDATES_PATH.read_text(encoding="utf-8"))


def test_authored_corpus_fills_every_signed_primary_and_reserve_assignment() -> None:
    inventory = load_authoring_inventory(INVENTORY_PATH)
    candidates = load_candidate_pool(CANDIDATES_PATH)

    hashes = validate_authored_candidate_inventory(
        candidates,
        authoring_inventory=inventory,
    )
    assert len(candidates) == len(hashes) == 48
    assert Counter(assignment.role for assignment in inventory.assignments) == {
        "primary": 36,
        "reserve": 12,
    }
    assert Counter(candidate.category for candidate in candidates) == {
        "correction": 12,
        "supersession": 8,
        "context-exception": 12,
        "no-memory": 8,
        "deletion": 8,
    }


def test_authored_worlds_contain_real_compression_length_event_sequences() -> None:
    candidates = load_candidate_pool(CANDIDATES_PATH)

    for candidate in candidates:
        assert all(
            len(world.setup_memory_events) >= 20 for world in candidate.worlds
        )
        assert all(
            world.setup_memory_events[-1].sequence_index
            == len(world.setup_memory_events) - 1
            for world in candidate.worlds
        )
        assert all(
            len({event.content for event in world.setup_memory_events})
            == len(world.setup_memory_events)
            for world in candidate.worlds
        )
        if candidate.category == "deletion":
            pre, post = candidate.worlds
            assert len(pre.setup_memory_events) == 20
            assert len(post.setup_memory_events) == 21
            assert {"summary", "wiki"} <= set(
                candidate.deletion_storage_locations
            )


def test_correction_and_supersession_encode_different_lifecycle_constructs() -> None:
    candidates = load_candidate_pool(CANDIDATES_PATH)

    for candidate in candidates:
        if candidate.category not in {"correction", "supersession"}:
            continue
        world = candidate.worlds[0]
        prior = world.setup_memory_events[0].content
        effective = next(
            event.content
            for event in world.setup_memory_events
            if event.operation in {"correct", "supersede"}
        )
        if candidate.category == "correction":
            assert "recorded as a factual instruction" in prior
            assert "was mistaken" in effective
            assert "should not be treated as true" in effective
        else:
            assert "Through last month" in prior
            assert "formerly valid preference" in effective
            assert "Starting today" in effective


def test_reviewed_probe_leaks_are_absent_and_reserve_templates_differ() -> None:
    inventory = load_authoring_inventory(INVENTORY_PATH)
    candidates = {
        candidate.candidate_id: candidate
        for candidate in load_candidate_pool(CANDIDATES_PATH)
    }
    reviewed = {
        "m0v13-context-exception-tone-public-private-scope-primary",
        "m0v13-context-exception-tone-public-private-scope-reserve",
        "m0v13-context-exception-support-venting-planning-scope-primary",
        "m0v13-no-memory-information-unrelated-scheduling-primary",
    }
    for candidate_id in reviewed:
        probe = candidates[candidate_id].worlds[0].probe.lower()
        assert "will be posted publicly" not in probe
        assert "venting, not planning" not in probe
        assert "no stored preference" not in probe

    for candidate_id in {
        "m0v13-context-exception-support-venting-planning-scope-primary",
        "m0v13-context-exception-support-venting-planning-scope-reserve",
    }:
        candidate = candidates[candidate_id]
        probe = candidate.worlds[0].probe.lower()
        task_description = candidate.worlds[0].non_memory_state[
            "task_description"
        ].lower()
        assert "venting" in probe or "talking through" in probe
        assert "plan" in probe
        assert (
            "venting" in task_description
            or "talking through" in task_description
        )
        assert "plan" in task_description
        assert "welcome" in probe or "acceptable" in probe

    for assignment in inventory.assignments:
        if assignment.role != "reserve":
            continue
        reserve = candidates[assignment.candidate_id]
        primary = candidates[assignment.reserve_target_id]
        assert reserve.worlds[0].probe != primary.worlds[0].probe
        assert reserve.worlds[0].probe.startswith("For the current task")
        assert primary.worlds[0].probe.startswith("Select the response action")
        assert [
            event.content for event in reserve.worlds[0].setup_memory_events
        ] != [
            event.content for event in primary.worlds[0].setup_memory_events
        ]


def test_candidate_histories_do_not_reuse_exact_filler_utterances() -> None:
    candidates = load_candidate_pool(CANDIDATES_PATH)
    content_owners: dict[str, set[str]] = {}
    for candidate in candidates:
        for event in candidate.worlds[0].setup_memory_events:
            if "-filler-" not in event.event_id:
                continue
            content_owners.setdefault(event.content, set()).add(
                candidate.candidate_id
            )
    assert all(len(owners) == 1 for owners in content_owners.values())


def test_every_authored_world_executes_the_faithful_compression_path() -> None:
    class SummaryBackend:
        def complete(self, *, operation, prompt, temperature, max_tokens):
            assert operation == "memory.compression.summary"
            return Completion("frozen offline summary", "offline-request")

    candidates = load_candidate_pool(CANDIDATES_PATH)
    for candidate in candidates:
        for world in candidate.worlds:
            policy = FaithfulSummaryPolicy(
                SummaryBackend(),
                today=date(2026, 7, 28),
            )
            for event in world.setup_memory_events:
                policy.add_message(
                    MemoryMessage(
                        role=event.role,
                        content=event.content,
                        timestamp=1_700_000_000_000 + event.sequence_index,
                        source_ids=(event.event_id,),
                    )
                )
            assert policy.trace.compression_applied == 1
            assert policy.wiki.compression_count == 1


def test_draft_registry_binds_all_authored_content_and_audit_entries() -> None:
    inventory = load_authoring_inventory(INVENTORY_PATH)
    candidates = load_candidate_pool(CANDIDATES_PATH)
    registry = load_candidate_registry(REGISTRY_PATH)
    audit = json.loads(AUDIT_PATH.read_text(encoding="utf-8"))

    hashes = validate_candidate_registry(
        registry,
        authoring_inventory=inventory,
        authored_candidates=candidates,
    )
    assert len(hashes) == 36
    assert Counter(record.status for record in registry.records) == {
        "eligible": 36,
        "reserve": 12,
    }
    assert audit["status"] == (
        "AUTOMATED_CONFORMANCE_COMPLETE_PENDING_WHOLE_POOL_REVIEW"
    )
    assert audit["candidate_count"] == 48
    assert audit["signed_inventory_sha256"] == inventory.sha256
    for entry in audit["entries"]:
        recorded_hash = entry.pop("audit_artifact_sha256")
        assert canonical_sha256(entry) == recorded_hash
        if entry["derived_deletion_evidence"] is not None:
            evidence = entry["derived_deletion_evidence"]
            assert entry["derived_deletion_target"] is True
            assert evidence == {
                "compression_applied": True,
                "summary_contains_target": True,
                "summary_sources_target_event": True,
                "wiki_field_contains_target": True,
                "rendered_context_contains_target": True,
                "operations": [
                    "memory.compression.summary",
                    "memory.wiki.first_extract",
                    "memory.reflection",
                ],
                "passed": True,
            }


@pytest.mark.parametrize(
    "mutation",
    ("slot", "provenance", "reserve_duplicate"),
)
def test_authored_corpus_rejects_inventory_or_reserve_rebinding(mutation: str) -> None:
    inventory = load_authoring_inventory(INVENTORY_PATH)
    payload = _load_payload()
    changed = deepcopy(payload)

    if mutation == "slot":
        candidate = next(
            value
            for value in changed
            if value["candidate_id"]
            == "m0v13-correction-detail-project-update-primary"
        )
        candidate["situation_slot"] = "tutorial_explanation"
    elif mutation == "provenance":
        candidate = next(
            value
            for value in changed
            if value["candidate_id"]
            == "m0v13-correction-support-routine-setback-primary"
        )
        candidate["provenance_tier"] = "P"
        candidate["provenance_artifact"]["origin_type"] = "product_extension"
    else:
        assignments = {
            assignment.candidate_id: assignment
            for assignment in inventory.assignments
        }
        reserve_id = next(
            candidate_id
            for candidate_id, assignment in assignments.items()
            if assignment.role == "reserve"
        )
        target_id = assignments[reserve_id].reserve_target_id
        reserve = next(
            candidate for candidate in changed if candidate["candidate_id"] == reserve_id
        )
        target = next(
            candidate for candidate in changed if candidate["candidate_id"] == target_id
        )
        reserve["underlying_event_fingerprint"] = target[
            "underlying_event_fingerprint"
        ]

    candidates = [CandidateV13.model_validate(candidate) for candidate in changed]
    with pytest.raises(ValueError):
        validate_authored_candidate_inventory(
            candidates,
            authoring_inventory=inventory,
        )
