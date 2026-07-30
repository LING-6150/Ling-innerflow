from __future__ import annotations

import json
import subprocess
import sys
from collections import Counter
from pathlib import Path

import pytest
from pydantic import ValidationError

from innerflow_v2.reliability.execution_v13 import (
    FrozenActionExecutor,
    grade_response_action,
    load_frozen_action_executor,
)
from innerflow_v2.reliability.protocol_v13 import (
    EXPECTED_CATEGORY_COUNTS,
    HOLDOUT_COUNTS,
    MATRIX_BY_KEY,
    ConformancePredicate,
    InvalidationDecision,
    ObservedRendering,
    ResponseAction,
    SignedConformanceManifest,
    candidate_sha256,
    canonical_bytes,
    canonical_sha256,
    classify_rendering,
    initialize_invalidation_history,
    load_invalidation_history,
    public_selection_manifest,
    request_post_split_invalidation,
    select_candidate_pool,
    selection_manifest_sha256,
    signed_conformance_manifest_sha256,
    validate_candidate_registry,
    validate_candidate_pool,
    validate_exclusion_manifest,
)

ROOT = Path(__file__).resolve().parents[2]


def _executor() -> FrozenActionExecutor:
    return FrozenActionExecutor(
        behavior_by_action={
            action: f"frozen behavior for {action.value}" for action in ResponseAction
        }
    )


def test_v13_grader_accepts_bare_json_and_one_exact_outer_json_fence():
    expected = ResponseAction.ASK_PERMISSION
    bare = '{"response_action":"ASK_PERMISSION"}'
    fenced = f"```json\n{bare}\n```"
    assert grade_response_action(bare, expected=expected).correct
    assert grade_response_action(fenced, expected=expected).correct


@pytest.mark.parametrize(
    "raw",
    [
        "```\n{\"response_action\":\"ASK_PERMISSION\"}\n```",
        "prefix {\"response_action\":\"ASK_PERMISSION\"}",
        "```json\n```json\n{\"response_action\":\"ASK_PERMISSION\"}\n```\n```",
        '{"response_action":"ASK_PERMISSION","extra":"x"}',
        '{"response_action":["ASK_PERMISSION","GIVE_DIRECT_STEPS"]}',
        '{"response_action":"ASK_PERMISSION","response_action":"ASK_PERMISSION"}',
        '{"memory_action":"APPLY","response_action":"ASK_PERMISSION"}',
        '{"response_action":"NONE"}',
    ],
)
def test_v13_grader_rejects_every_unfrozen_wrapper_schema_and_enum(raw):
    grade = grade_response_action(raw, expected=ResponseAction.ASK_PERMISSION)
    assert not grade.correct


def test_frozen_executor_binds_every_action_and_is_deterministic():
    executor = _executor()
    assert executor.execute(ResponseAction.USE_UNPERSONALIZED_DEFAULT).startswith(
        "frozen behavior"
    )
    assert executor.sha256 == _executor().sha256
    with pytest.raises(ValidationError):
        FrozenActionExecutor(
            behavior_by_action={ResponseAction.ASK_PERMISSION: "partial"}
        )


def test_committed_executor_manifest_binds_real_downstream_directives():
    executor = load_frozen_action_executor(
        ROOT / "eval" / "m0" / "manifests" / "M0_V13_ACTION_EXECUTOR.json"
    )
    assert set(executor.behavior_by_action) == set(ResponseAction)
    assert "do not personalize" in executor.execute(
        ResponseAction.USE_UNPERSONALIZED_DEFAULT
    )
    assert (
        executor.sha256
        == "1abe404415d06ea546a1d6089785aa4c720d16bb437075aa51dc2a8a975ec0b1"
    )


def test_rfc8785_canonicalization_is_key_order_invariant():
    assert canonical_bytes({"z": 1, "a": 2}) == b'{"a":2,"z":1}'
    assert canonical_bytes({"a": 2, "z": 1}) == canonical_bytes({"z": 1, "a": 2})


def test_pool_enforces_all_36_frozen_slots_and_provenance(candidate_pool_v13):
    hashes = validate_candidate_pool(candidate_pool_v13)
    assert len(hashes) == 36
    assert len(set(hashes.values())) == 36
    assert Counter(
        (candidate.category, candidate.action_band)
        for candidate in candidate_pool_v13
    ) == Counter({key: 3 for key in MATRIX_BY_KEY})
    assert all(not candidate.old_item_overlap_hashes for candidate in candidate_pool_v13)


def test_registry_retains_reserve_records_and_matches_every_eligible_hash(
    candidate_pool_v13,
    registry_v13,
    authoring_inventory_v13,
):
    assert validate_candidate_registry(
        registry_v13,
        authoring_inventory=authoring_inventory_v13,
    ) == validate_candidate_pool(candidate_pool_v13)
    assert any(record.status == "reserve" for record in registry_v13.records)

    payload = registry_v13.model_dump(mode="json")
    payload["records"] = [
        record
        for record in payload["records"]
        if record["candidate_id"] != candidate_pool_v13[0].candidate_id
    ]
    incomplete = type(registry_v13).model_validate(payload)
    with pytest.raises(ValueError, match="precommitted inventory"):
        validate_candidate_registry(
            incomplete,
            authoring_inventory=authoring_inventory_v13,
        )

    payload["authoring_inventory_ids"] = [
        value
        for value in payload["authoring_inventory_ids"]
        if value != "rejected-audit-record"
    ]
    payload["authoring_inventory_sha256"] = canonical_sha256(
        sorted(payload["authoring_inventory_ids"])
    )
    self_rewritten = type(registry_v13).model_validate(payload)
    with pytest.raises(ValueError, match="independently signed"):
        validate_candidate_registry(
            self_rewritten,
            authoring_inventory=authoring_inventory_v13,
        )


def test_registry_rejects_signed_assignment_rebinding(
    candidate_pool_v13,
    registry_v13,
    authoring_inventory_v13,
):
    def registry_with_record_update(candidate_id, **updates):
        payload = registry_v13.model_dump(mode="json")
        record = next(
            value
            for value in payload["records"]
            if value["candidate_id"] == candidate_id
        )
        record.update(updates)
        return type(registry_v13).model_validate(payload)

    first_row = [
        candidate
        for candidate in candidate_pool_v13
        if (candidate.category, candidate.action_band) == ("correction", "detail")
    ]
    slot_reassigned = registry_with_record_update(
        first_row[0].candidate_id,
        situation_slot=first_row[1].situation_slot,
    )
    with pytest.raises(ValueError, match="signed authoring inventory"):
        validate_candidate_registry(
            slot_reassigned,
            authoring_inventory=authoring_inventory_v13,
        )

    mixed_row = next(
        candidate
        for candidate in candidate_pool_v13
        if (candidate.category, candidate.action_band, candidate.provenance_tier)
        == ("correction", "support", "E")
    )
    provenance_reassigned = registry_with_record_update(
        mixed_row.candidate_id,
        provenance_tier="P",
    )
    with pytest.raises(ValueError, match="signed authoring inventory"):
        validate_candidate_registry(
            provenance_reassigned,
            authoring_inventory=authoring_inventory_v13,
        )

    reserve_retargeted = registry_with_record_update(
        "rejected-audit-record",
        reserve_target_id=first_row[1].candidate_id,
    )
    with pytest.raises(ValueError, match="signed authoring inventory"):
        validate_candidate_registry(
            reserve_retargeted,
            authoring_inventory=authoring_inventory_v13,
        )


@pytest.mark.parametrize(
    ("field", "value"),
    (
        ("situation_slot", "tutorial_explanation"),
        ("provenance_tier", "P"),
        ("reserve_target_id", "correction-detail-1"),
    ),
)
def test_signed_inventory_hash_rejects_assignment_rebinding(
    authoring_inventory_v13,
    field,
    value,
):
    payload = authoring_inventory_v13.model_dump(mode="json")
    if field == "reserve_target_id":
        assignment = next(
            item for item in payload["assignments"] if item["role"] == "reserve"
        )
    else:
        assignment = next(
            item
            for item in payload["assignments"]
            if item["candidate_id"] == "correction-detail-0"
        )
    assignment[field] = value

    with pytest.raises(ValueError, match="assignment hash drift"):
        type(authoring_inventory_v13).model_validate(payload)


def test_required_no_memory_and_deletion_world_contracts_are_static(
    candidate_pool_v13,
):
    for candidate in candidate_pool_v13:
        assert candidate.worlds[0].probe == candidate.worlds[1].probe
        assert (
            candidate.worlds[0].non_memory_state
            == candidate.worlds[1].non_memory_state
        )
        if candidate.category in {
            "correction",
            "supersession",
            "context-exception",
        }:
            assert (
                candidate.worlds[0].gold_response_action
                != candidate.worlds[1].gold_response_action
            )
            assert candidate.symmetry_certificate is not None
        elif candidate.category == "no-memory":
            assert {
                world.gold_response_action for world in candidate.worlds
            } == {ResponseAction.USE_UNPERSONALIZED_DEFAULT}
        else:
            by_id = {world.world_id: world for world in candidate.worlds}
            assert (
                by_id["post_delete"].gold_response_action
                == ResponseAction.USE_UNPERSONALIZED_DEFAULT
            )


@pytest.mark.parametrize(
    ("category", "mutation", "message"),
    [
        (
            "correction",
            "missing_effective_claim",
            "effective claims must be non-empty",
        ),
        (
            "no-memory",
            "missing_irrelevant_claim",
            "two distinct irrelevant claims",
        ),
        (
            "deletion",
            "post_delete_retains_claim",
            "must remove",
        ),
        (
            "correction",
            "certificate_binding_drift",
            "certificate action bindings drift",
        ),
    ],
)
def test_paired_world_construct_cannot_be_bypassed(
    candidate_pool_v13,
    category,
    mutation,
    message,
):
    original = next(
        candidate for candidate in candidate_pool_v13
        if candidate.category == category
    )
    payload = original.model_dump(mode="json")
    if mutation == "missing_effective_claim":
        payload["worlds"][0]["effective_claim"] = None
    elif mutation == "missing_irrelevant_claim":
        payload["worlds"][0]["effective_claim"] = None
    elif mutation == "post_delete_retains_claim":
        post = next(
            world for world in payload["worlds"]
            if world["world_id"] == "post_delete"
        )
        post["effective_claim"] = "retained-after-delete"
    else:
        payload["symmetry_certificate"]["action_a_binding"] = (
            ResponseAction.USE_UNPERSONALIZED_DEFAULT.value
        )
    with pytest.raises(ValidationError, match=message):
        type(original).model_validate(payload)


def test_candidate_revalidation_rejects_old_overlap_and_near_duplicate(
    candidate_pool_v13,
):
    payload = candidate_pool_v13[0].model_dump(mode="json")
    payload["old_item_overlap_hashes"] = ["0" * 64]
    with pytest.raises(ValidationError, match="overlaps"):
        type(candidate_pool_v13[0]).model_validate(payload)

    duplicate = candidate_pool_v13[-1].model_copy(
        update={
            "underlying_event_fingerprint": (
                candidate_pool_v13[0].underlying_event_fingerprint
            )
        }
    )
    with pytest.raises(ValueError, match="duplicate/near-duplicate"):
        validate_candidate_pool([*candidate_pool_v13[:-1], duplicate])
    with pytest.raises(ValueError, match="frozen v1.2 corpus"):
        validate_candidate_pool(
            candidate_pool_v13,
            forbidden_candidate_hashes={candidate_sha256(candidate_pool_v13[0])},
        )
    with pytest.raises(ValueError, match="semantic fingerprint"):
        validate_candidate_pool(
            candidate_pool_v13,
            forbidden_overlap_fingerprints={
                candidate_pool_v13[0].semantic_overlap_fingerprint
            },
        )


def test_registry_requires_complete_audit_evidence(registry_v13):
    payload = registry_v13.model_dump(mode="json")
    del payload["records"][0]["audit_artifact_sha256"]
    with pytest.raises(ValidationError, match="audit_artifact_sha256"):
        type(registry_v13).model_validate(payload)


def test_exclusion_manifest_rejects_empty_and_identity_mismatched_lists(
    exclusion_manifest_v13,
    forbidden_hashes_v13,
    forbidden_fingerprints_v13,
):
    with pytest.raises(ValueError, match="non-empty"):
        validate_exclusion_manifest(
            exclusion_manifest_v13,
            forbidden_candidate_hashes=set(),
            forbidden_overlap_fingerprints=forbidden_fingerprints_v13,
        )
    with pytest.raises(ValueError, match="candidate-hash"):
        validate_exclusion_manifest(
            exclusion_manifest_v13,
            forbidden_candidate_hashes={
                *forbidden_hashes_v13,
                canonical_sha256("unfrozen extra hash"),
            },
            forbidden_overlap_fingerprints=forbidden_fingerprints_v13,
        )


def test_selection_split_and_gate_assignment_are_reproducible_and_frozen(
    candidate_pool_v13,
    registry_v13,
    authoring_inventory_v13,
    exclusion_manifest_v13,
    forbidden_hashes_v13,
    forbidden_fingerprints_v13,
    frozen_at_v13,
    beacon_v13,
):
    first = select_candidate_pool(
        registry_v13,
        authoring_inventory=authoring_inventory_v13,
        exclusion_manifest=exclusion_manifest_v13,
        forbidden_candidate_hashes=forbidden_hashes_v13,
        forbidden_overlap_fingerprints=forbidden_fingerprints_v13,
        pool_frozen_at=frozen_at_v13,
        beacon=beacon_v13,
    )
    second = select_candidate_pool(
        registry_v13.model_copy(
            update={"candidates": list(reversed(candidate_pool_v13))}
        ),
        authoring_inventory=authoring_inventory_v13,
        exclusion_manifest=exclusion_manifest_v13,
        forbidden_candidate_hashes=forbidden_hashes_v13,
        forbidden_overlap_fingerprints=forbidden_fingerprints_v13,
        pool_frozen_at=frozen_at_v13,
        beacon=beacon_v13,
    )
    assert selection_manifest_sha256(first) == selection_manifest_sha256(second)
    assert Counter(entry.category for entry in first.selected) == EXPECTED_CATEGORY_COUNTS
    assert Counter(entry.split for entry in first.selected) == {
        "visible": 16,
        "holdout": 8,
    }
    assert Counter(
        entry.category for entry in first.selected if entry.split == "holdout"
    ) == HOLDOUT_COUNTS

    candidates = {candidate.candidate_id: candidate for candidate in candidate_pool_v13}
    assert Counter(
        candidates[entry.candidate_id].provenance_tier for entry in first.selected
    ) == {"E": 16, "P": 8}
    for key, row in MATRIX_BY_KEY.items():
        if row.action_pair is None:
            continue
        gate_actions = []
        for entry in first.selected:
            if (entry.category, entry.action_band) != key:
                continue
            candidate = candidates[entry.candidate_id]
            gate_actions.append(
                next(
                    world.gold_response_action
                    for world in candidate.worlds
                    if world.world_id == entry.gate_world_id
                )
            )
        assert Counter(gate_actions) == Counter(row.action_pair)


def test_beacon_before_24_hour_boundary_is_rejected(
    registry_v13,
    authoring_inventory_v13,
    exclusion_manifest_v13,
    forbidden_hashes_v13,
    forbidden_fingerprints_v13,
    frozen_at_v13,
    beacon_v13,
):
    early = beacon_v13.model_copy(
        update={"pulse_timestamp": frozen_at_v13}
    )
    with pytest.raises(ValueError, match="at least 24 hours"):
        select_candidate_pool(
            registry_v13,
            authoring_inventory=authoring_inventory_v13,
            exclusion_manifest=exclusion_manifest_v13,
            forbidden_candidate_hashes=forbidden_hashes_v13,
            forbidden_overlap_fingerprints=forbidden_fingerprints_v13,
            pool_frozen_at=frozen_at_v13,
            beacon=early,
        )


def test_public_manifest_contains_no_holdout_ids_or_item_hashes(selection_v13):
    public = public_selection_manifest(selection_v13)
    serialized = str(public)
    for entry in selection_v13.selected:
        if entry.split == "holdout":
            assert entry.candidate_id not in serialized
            assert entry.candidate_sha256 not in serialized
    assert public["holdout"]["count"] == 8


def test_gold_applicability_and_rendering_are_independent(candidate_pool_v13):
    claim = candidate_pool_v13[0].tracked_claims[0]
    assert (
        classify_rendering("Nothing from the frozen vocabulary.", claim)
        == ObservedRendering.NOT_DETECTED_UNDER_FROZEN_LEXICON
    )
    assert (
        classify_rendering(f"Currently: {claim.surface_forms[0]}", claim)
        == ObservedRendering.ACTIVE_ASSERTION
    )
    assert (
        classify_rendering(f"Used to: {claim.surface_forms[0]}", claim)
        == ObservedRendering.HISTORICAL_OR_NEGATED
    )
    assert (
        classify_rendering(
            f"Currently and used to: {claim.surface_forms[0]}", claim
        )
        == ObservedRendering.AMBIGUOUS
    )


def test_post_split_invalidation_is_binding_and_allows_only_one_signed_predicate(
    selection_v13,
    tmp_path,
):
    manifest = SignedConformanceManifest(
        pool_sha256=selection_v13.pool_sha256,
        seed_sha256="1" * 64,
        selection_sha256=selection_manifest_sha256(selection_v13),
        invalidation_history_path=str(tmp_path / "invalidation-history.json"),
        signed_by=["owner", "reviewer"],
        allowed_invalidation_predicates=set(ConformancePredicate),
    )
    history_path = Path(manifest.invalidation_history_path)
    initialize_invalidation_history(manifest)
    with pytest.raises(FileExistsError):
        initialize_invalidation_history(manifest)
    rejected = request_post_split_invalidation(
        manifest,
        predicate="selection_was_inconvenient",
        evidence_sha256="2" * 64,
    )
    assert rejected.decision == InvalidationDecision.REJECT_BINDING_SELECTION
    allowed = request_post_split_invalidation(
        manifest,
        predicate=ConformancePredicate.DETERMINISTIC_PAIR_CHECK.value,
        evidence_sha256="2" * 64,
    )
    assert allowed.decision == InvalidationDecision.REFREEZE_ALLOWED
    incident = request_post_split_invalidation(
        manifest,
        predicate=ConformancePredicate.DETERMINISTIC_PAIR_CHECK.value,
        evidence_sha256="2" * 64,
    )
    assert incident.decision == InvalidationDecision.TERMINATE_HYPOTHESIS
    assert incident.old_pool_sha256 == manifest.pool_sha256
    assert incident.old_seed_sha256 == manifest.seed_sha256
    assert incident.old_selection_sha256 == manifest.selection_sha256
    assert len(incident.incident_sha256) == 64
    history = load_invalidation_history(history_path)
    assert len(history.incidents) == 3
    switched = manifest.model_copy(
        update={
            "invalidation_history_path": str(
                tmp_path / "second-invalidation-history.json"
            )
        }
    )
    assert signed_conformance_manifest_sha256(switched) != (
        signed_conformance_manifest_sha256(manifest)
    )
    with pytest.raises(FileNotFoundError):
        request_post_split_invalidation(
            switched,
            predicate=ConformancePredicate.DETERMINISTIC_PAIR_CHECK.value,
            evidence_sha256="2" * 64,
        )


def test_candidate_hash_changes_when_frozen_content_changes(candidate_pool_v13):
    original = candidate_pool_v13[0]
    payload = original.model_dump(mode="json")
    payload["provenance_reference"] = "different-source"
    payload["provenance_artifact"]["source_reference"] = "different-source"
    changed = type(original).model_validate(payload)
    assert candidate_sha256(original) != candidate_sha256(changed)


def test_local_cli_validates_and_selects_without_model_or_network(
    registry_v13,
    authoring_inventory_v13,
    exclusion_manifest_v13,
    forbidden_hashes_v13,
    forbidden_fingerprints_v13,
    frozen_at_v13,
    beacon_v13,
    tmp_path,
):
    registry_path = tmp_path / "registry.json"
    inventory_path = tmp_path / "authoring-inventory.json"
    beacon_path = tmp_path / "beacon.json"
    selection_path = tmp_path / "selection.json"
    public_path = tmp_path / "public.json"
    forbidden_hashes = tmp_path / "old-hashes.txt"
    forbidden_fingerprints = tmp_path / "old-fingerprints.txt"
    exclusion_manifest_path = tmp_path / "exclusion-manifest.json"
    registry_path.write_text(
        json.dumps(registry_v13.model_dump(mode="json")),
        encoding="utf-8",
    )
    inventory_path.write_text(
        authoring_inventory_v13.model_dump_json(),
        encoding="utf-8",
    )
    beacon_path.write_text(beacon_v13.model_dump_json(), encoding="utf-8")
    forbidden_hashes.write_text(
        "\n".join(sorted(forbidden_hashes_v13)) + "\n",
        encoding="utf-8",
    )
    forbidden_fingerprints.write_text(
        "\n".join(sorted(forbidden_fingerprints_v13)) + "\n",
        encoding="utf-8",
    )
    exclusion_manifest_path.write_text(
        exclusion_manifest_v13.model_dump_json(),
        encoding="utf-8",
    )
    script = ROOT / "scripts" / "check_m0_v13_conformance.py"

    validation = subprocess.run(
        [
            sys.executable,
            str(script),
            "validate-pool",
            "--registry",
            str(registry_path),
            "--authoring-inventory",
            str(inventory_path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    assert json.loads(validation.stdout)["eligible_candidates"] == 36

    subprocess.run(
        [
            sys.executable,
            str(script),
            "select",
            "--registry",
            str(registry_path),
            "--authoring-inventory",
            str(inventory_path),
            "--pool-frozen-at",
            frozen_at_v13.isoformat(),
            "--beacon",
            str(beacon_path),
            "--output",
            str(selection_path),
            "--public-output",
            str(public_path),
            "--forbidden-hashes",
            str(forbidden_hashes),
            "--forbidden-fingerprints",
            str(forbidden_fingerprints),
            "--exclusion-manifest",
            str(exclusion_manifest_path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    assert len(json.loads(selection_path.read_text())["selected"]) == 24
    assert json.loads(public_path.read_text())["holdout"]["count"] == 8
