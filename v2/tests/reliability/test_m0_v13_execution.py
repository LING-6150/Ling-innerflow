from __future__ import annotations

import hashlib
import os
from collections import defaultdict
from datetime import date, datetime, timezone
from pathlib import Path

import pytest
from pydantic import ValidationError

from innerflow_v2.reliability.execution_v13 import (
    AttemptV13,
    FrozenActionExecutor,
    FrozenRunManifestV13,
    IncidentDisposition,
    RequestCell,
    RunIncident,
    WorldResultV13,
    build_checkpoint,
    build_request_order,
    build_resume_manifest,
    detect_applicable_claim_ids,
    evaluate_gate_worlds,
    initialize_run_incident_history,
    next_resume_replicate,
    public_checkpoint_status,
    record_model_response,
    record_provider_failure,
    request_order_sha256,
    resolve_run_incident,
    validate_complete_replicate,
    validate_result_audit,
    validate_resume_manifest,
)
from innerflow_v2.reliability.client import Completion
from innerflow_v2.reliability.protocol_v13 import (
    ConformancePredicate,
    ResponseAction,
    SignedConformanceManifest,
    candidate_sha256,
    canonical_sha256,
    selection_manifest_sha256,
    signed_conformance_manifest_sha256,
)
from innerflow_v2.reliability.runner_v13 import run_complete_replicate


ORDER_SEED = 6150


class _CompleteV13Backend:
    def complete(self, *, operation, prompt, temperature, max_tokens):
        if operation in {
            "memory.wiki.first_extract",
            "memory.wiki.merge",
        }:
            return Completion(
                content=(
                    '{"emotionPattern":null,"coreStruggles":null,'
                    '"effectiveCoping":null,"languageStyle":null,'
                    '"triggerUpdates":[],"conflicts":[],'
                    '"newProgressNote":null,"changeLogEntry":null}'
                ),
                request_id="formation-request",
            )
        if operation == "memory.compression.summary":
            return Completion("frozen summary", "summary-request")
        if operation == "memory.reflection":
            return Completion("frozen reflection", "reflection-request")
        return Completion(
            '{"response_action":"ASK_PERMISSION"}',
            "response-request",
        )

    def embed(self, texts):
        return [[1.0, 0.0] for _ in texts]


def _signed_manifest(
    selection,
    *,
    history_path: str | None = None,
) -> SignedConformanceManifest:
    history_path = history_path or (
        f"/private/tmp/innerflow-v13-{os.getpid()}-invalidation.json"
    )
    return SignedConformanceManifest(
        pool_sha256=selection.pool_sha256,
        seed_sha256=hashlib.sha256(selection.beacon.seed_bytes).hexdigest(),
        selection_sha256=selection_manifest_sha256(selection),
        invalidation_history_path=history_path,
        signed_by=["owner", "independent-reviewer"],
        allowed_invalidation_predicates=set(ConformancePredicate),
    )


def _run_manifest(
    selection,
    signed_manifest=None,
) -> FrozenRunManifestV13:
    signed_manifest = signed_manifest or _signed_manifest(selection)
    return FrozenRunManifestV13(
        source_commit="1" * 40,
        selection_sha256=selection_manifest_sha256(selection),
        signed_conformance_manifest_sha256=(
            signed_conformance_manifest_sha256(signed_manifest)
        ),
        candidate_pool_sha256=selection.pool_sha256,
        incident_history_path=str(
            Path(signed_manifest.invalidation_history_path).with_name(
                Path(signed_manifest.invalidation_history_path).stem
                + "-run-incidents.json"
            )
        ),
        run_date=date(2026, 8, 3),
        model_id="provider/model-v1",
        provider_model_version="provider/model-v1-20260801",
        provider="test-provider",
        provider_base_url="https://provider.test/v1",
        provider_model_metadata_sha256=canonical_sha256("provider-metadata"),
        embedding_model_id="provider/embedding-v1",
        formation_temperature=0.2,
        response_temperature=0.4,
        token_limits={
            "summary": 2048,
            "wiki": 4096,
            "reflection": 2048,
            "response": 1024,
        },
        prompt_hashes={
            stage: canonical_sha256(f"{stage}-prompt")
            for stage in ("summary", "wiki", "reflection", "response")
        },
        ontology_sha256=canonical_sha256("ontology"),
        normalizer_sha256=canonical_sha256("normalizer"),
        executor_sha256=canonical_sha256("executor"),
        dependency_lock_sha256=canonical_sha256("lock"),
        implementation_source_hashes={
            "runner.py": canonical_sha256("runner")
        },
        order_seed=ORDER_SEED,
        retry_attempts=3,
        retry_backoff_seconds=2.0,
        request_timeout_seconds=120.0,
    )


def _records(
    selection,
    registry,
    *,
    replicates=(1,),
    signed_manifest=None,
) -> list[WorldResultV13]:
    candidates = {
        candidate.candidate_id: candidate for candidate in registry.candidates
    }
    records = []
    signed_manifest = signed_manifest or _signed_manifest(selection)
    run_manifest = _run_manifest(selection, signed_manifest)
    incident_path = Path(run_manifest.incident_history_path)
    if not incident_path.exists():
        initialize_run_incident_history(run_manifest)
    for replicate in replicates:
        for cell in build_request_order(
            selection,
            registry,
            signed_manifest,
            replicate=replicate,
            execution_attempt_id=canonical_sha256(
                {"replicate": replicate, "execution_epoch": "primary"}
            ),
            order_seed=ORDER_SEED,
        ):
            candidate = candidates[cell.candidate_id]
            world = next(
                value for value in candidate.worlds if value.world_id == cell.world_id
            )
            rendered_context = ""
            if (
                cell.policy == "B-full"
                and candidate.category == "context-exception"
                and world.applicable_claim_id is not None
            ):
                claim = next(
                    value
                    for value in candidate.tracked_claims
                    if value.claim_id == world.applicable_claim_id
                )
                rendered_context = f"Currently: {claim.surface_forms[0]}"
            records.append(
                record_model_response(
                    cell,
                    raw_response=(
                        '{"response_action":"'
                        + cell.expected_action.value
                        + '"}'
                    ),
                    request_id=f"req-{replicate}-{cell.sequence}",
                    rendered_context=rendered_context,
                )
            )
    return records


def _incorrect(record: WorldResultV13) -> WorldResultV13:
    wrong_action = next(
        action for action in ResponseAction if action != record.response_action
    )
    attempts = [
        *record.attempts[:-1],
        record.attempts[-1].model_copy(
            update={
                "raw_response": (
                    '{"response_action":"' + wrong_action.value + '"}'
                )
            }
        ),
    ]
    return WorldResultV13.model_validate(
        {
            **record.model_dump(mode="json"),
            "correct": False,
            "response_action": wrong_action.value,
            "attempts": [attempt.model_dump(mode="json") for attempt in attempts],
        }
    )


def test_complete_replicate_contains_every_gate_and_counter_world(
    selection_v13,
    registry_v13,
):
    order = build_request_order(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        replicate=1,
        execution_attempt_id=canonical_sha256("attempt-1"),
        order_seed=ORDER_SEED,
    )
    assert len(order) == 24 * 2 * 3
    grouped = defaultdict(set)
    for cell in order:
        grouped[(cell.candidate_id, cell.policy)].add(cell.world_id)
    assert all(len(worlds) == 2 for worlds in grouped.values())

    records = _records(selection_v13, registry_v13)
    validate_complete_replicate(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        records,
        replicate=1,
        run_manifest=_run_manifest(selection_v13),
    )


def test_request_order_is_frozen_and_hash_changes_with_replicate(
    selection_v13,
    registry_v13,
):
    first = build_request_order(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        replicate=1,
        execution_attempt_id=canonical_sha256("attempt-1"),
        order_seed=ORDER_SEED,
    )
    repeated = build_request_order(
        selection_v13,
        registry_v13.model_copy(
            update={"candidates": list(reversed(registry_v13.candidates))}
        ),
        _signed_manifest(selection_v13),
        replicate=1,
        execution_attempt_id=canonical_sha256("attempt-1"),
        order_seed=ORDER_SEED,
    )
    second_replicate = build_request_order(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        replicate=2,
        execution_attempt_id=canonical_sha256("attempt-2"),
        order_seed=ORDER_SEED,
    )
    assert request_order_sha256(first) == request_order_sha256(repeated)
    assert request_order_sha256(first) != request_order_sha256(second_replicate)


def test_v13_runner_executes_every_policy_and_world_in_frozen_order(
    selection_v13,
    registry_v13,
    signed_conformance_v13,
) -> None:
    run_manifest = _run_manifest(selection_v13, signed_conformance_v13)
    initialize_run_incident_history(run_manifest)
    backend = _CompleteV13Backend()
    executor = FrozenActionExecutor(
        behavior_by_action={
            action: f"Execute {action.value}." for action in ResponseAction
        }
    )

    records = run_complete_replicate(
        selection_v13,
        registry_v13,
        signed_conformance_v13,
        replicate=1,
        backend=backend,
        embedding_backend=backend,
        executor=executor,
        run_manifest=run_manifest,
    )

    assert len(records) == 24 * 3 * 2
    assert all(record.status in {"complete", "wrong_output"} for record in records)
    validate_complete_replicate(
        selection_v13,
        registry_v13,
        signed_conformance_v13,
        records,
        replicate=1,
        run_manifest=run_manifest,
    )


def test_execution_rejects_a_reduced_selection_even_with_a_new_run_manifest(
    selection_v13,
    registry_v13,
):
    reduced = selection_v13.model_copy(
        update={"selected": selection_v13.selected[:-1]}
    )
    with pytest.raises(ValueError, match="authoritative selection"):
        build_request_order(
            reduced,
            registry_v13,
            _signed_manifest(selection_v13),
            replicate=1,
            execution_attempt_id=canonical_sha256("attempt-1"),
            order_seed=ORDER_SEED,
        )


def test_execution_rejects_structurally_valid_selection_reassignment(
    selection_v13,
    registry_v13,
):
    signed = _signed_manifest(selection_v13)
    selected = list(selection_v13.selected)

    visible = next(entry for entry in selected if entry.split == "visible")
    holdout = next(
        entry for entry in selected
        if entry.category == visible.category and entry.split == "holdout"
    )
    split_swapped = [
        entry.model_copy(
            update={
                "split": (
                    holdout.split
                    if entry.candidate_id == visible.candidate_id
                    else visible.split
                )
            }
        )
        if entry.candidate_id in {visible.candidate_id, holdout.candidate_id}
        else entry
        for entry in selected
    ]

    required_pair = next(
        (
            left,
            right,
        )
        for left in selected
        for right in selected
        if left.candidate_id < right.candidate_id
        and (left.category, left.action_band)
        == (right.category, right.action_band)
        and left.gate_world_id != right.gate_world_id
    )
    gate_swapped = []
    for entry in selected:
        if entry.candidate_id == required_pair[0].candidate_id:
            gate_swapped.append(
                entry.model_copy(
                    update={
                        "gate_world_id": required_pair[1].gate_world_id,
                        "counter_world_ids": [required_pair[0].gate_world_id],
                    }
                )
            )
        elif entry.candidate_id == required_pair[1].candidate_id:
            gate_swapped.append(
                entry.model_copy(
                    update={
                        "gate_world_id": required_pair[0].gate_world_id,
                        "counter_world_ids": [required_pair[1].gate_world_id],
                    }
                )
            )
        else:
            gate_swapped.append(entry)

    selected_ids = {entry.candidate_id for entry in selected}
    candidates_by_id = {
        candidate.candidate_id: candidate
        for candidate in registry_v13.candidates
    }
    replaced_entry, replacement_target = next(
        (entry, candidate)
        for candidate in registry_v13.candidates
        for entry in selected
        if candidate.candidate_id not in selected_ids
        and (entry.category, entry.action_band)
        == (candidate.category, candidate.action_band)
        and candidates_by_id[entry.candidate_id].provenance_tier
        == candidate.provenance_tier
    )
    replacement = replaced_entry.model_copy(
        update={
            "candidate_id": replacement_target.candidate_id,
            "candidate_sha256": candidate_sha256(replacement_target),
            "gate_world_id": replacement_target.worlds[0].world_id,
            "counter_world_ids": [replacement_target.worlds[1].world_id],
        }
    )
    candidate_replaced = [
        replacement
        if entry.candidate_id == replaced_entry.candidate_id
        else entry
        for entry in selected
    ]

    for reassigned in (split_swapped, gate_swapped, candidate_replaced):
        changed = selection_v13.model_copy(update={"selected": reassigned})
        with pytest.raises(ValueError, match="authoritative selection"):
            build_request_order(
                changed,
                registry_v13,
                signed,
                replicate=1,
                execution_attempt_id=canonical_sha256("attempt-1"),
                order_seed=ORDER_SEED,
            )


@pytest.mark.parametrize(
    "field",
    ["probe", "effective_claim", "gold", "tracked_claim"],
)
def test_selected_candidate_content_drift_is_rejected(
    selection_v13,
    registry_v13,
    field,
):
    selected_id = selection_v13.selected[0].candidate_id
    original = next(
        value for value in registry_v13.candidates
        if value.candidate_id == selected_id
    )
    payload = original.model_dump(mode="json")
    if field == "probe":
        for world in payload["worlds"]:
            world["probe"] += " changed after selection"
    elif field == "effective_claim":
        claim_id = payload["worlds"][0]["applicable_claim_id"]
        payload["worlds"][0]["effective_claim"] += "-changed"
        claim = next(
            value for value in payload["tracked_claims"]
            if value["claim_id"] == claim_id
        )
        claim["canonical_value"] = payload["worlds"][0]["effective_claim"]
    elif field == "gold":
        payload["worlds"][0]["gold_response_action"], payload["worlds"][1][
            "gold_response_action"
        ] = (
            payload["worlds"][1]["gold_response_action"],
            payload["worlds"][0]["gold_response_action"],
        )
        payload["symmetry_certificate"]["action_a_binding"] = payload["worlds"][0][
            "gold_response_action"
        ]
        payload["symmetry_certificate"]["action_b_binding"] = payload["worlds"][1][
            "gold_response_action"
        ]
    else:
        payload["tracked_claims"][0]["surface_forms"][0] += " changed"
    changed = type(original).model_validate(payload)
    changed_registry = registry_v13.model_copy(
        update={
            "candidates": [
                changed if value.candidate_id == selected_id else value
                for value in registry_v13.candidates
            ]
        }
    )
    with pytest.raises(ValueError, match="drift|balanced"):
        build_request_order(
            selection_v13,
            changed_registry,
            _signed_manifest(selection_v13),
            replicate=1,
            execution_attempt_id=canonical_sha256("attempt-1"),
            order_seed=ORDER_SEED,
        )
    records = _records(selection_v13, registry_v13)
    with pytest.raises(ValueError, match="drift|balanced"):
        build_checkpoint(
            selection_v13,
            changed_registry,
            _signed_manifest(selection_v13),
            records,
            run_manifest=_run_manifest(selection_v13),
        )


def test_partial_replicate_and_provider_failure_cannot_be_checkpointed(
    selection_v13,
    registry_v13,
):
    records = _records(selection_v13, registry_v13)
    with pytest.raises(ValueError, match="partial"):
        build_checkpoint(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            records[:-1],
            run_manifest=_run_manifest(selection_v13),
        )

    first_cell = build_request_order(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        replicate=1,
        execution_attempt_id=records[0].execution_attempt_id,
        order_seed=ORDER_SEED,
    )[0]
    failed = record_provider_failure(
        first_cell,
        request_ids=["req-fail-1", "req-fail-2", "req-fail-3"],
        error_type="TimeoutError",
    )
    with pytest.raises(ValueError, match="provider failure"):
        build_checkpoint(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            [failed, *records[1:]],
            run_manifest=_run_manifest(selection_v13),
        )


def test_replicate_cells_cannot_merge_across_execution_attempts(
    selection_v13,
    registry_v13,
    tmp_path,
):
    signed = _signed_manifest(
        selection_v13,
        history_path=str(tmp_path / "attempt-invalidation.json"),
    )
    run_manifest = _run_manifest(selection_v13, signed)
    records = _records(
        selection_v13,
        registry_v13,
        signed_manifest=signed,
    )
    replacement_attempt = canonical_sha256("replacement-attempt")
    mixed = [
        *records[:-1],
        records[-1].model_copy(
            update={"execution_attempt_id": replacement_attempt}
        ),
    ]
    with pytest.raises(ValueError, match="one execution attempt"):
        build_checkpoint(
            selection_v13,
            registry_v13,
            signed,
            mixed,
            run_manifest=run_manifest,
        )

    original_attempt = records[0].execution_attempt_id
    assert (
        resolve_run_incident(
            run_manifest,
            RunIncident.PARTIAL_REPLICATE_INTERRUPTION,
            current_run_manifest_sha256=run_manifest.sha256,
            execution_attempt_id=original_attempt,
        )
        == IncidentDisposition.VOID_PARTIAL_REPLICATE
    )
    with pytest.raises(ValueError, match="voided after interruption"):
        build_checkpoint(
            selection_v13,
            registry_v13,
            signed,
            records,
            run_manifest=run_manifest,
        )


def test_provider_failure_is_missing_but_complete_invalid_response_is_wrong():
    cell = RequestCell(
        candidate_id="case",
        category="correction",
        split="visible",
        world_id="world_a",
        is_gate_world=True,
        expected_action=ResponseAction.ASK_PERMISSION,
        policy="B-summary",
        replicate=1,
        execution_attempt_id=canonical_sha256("attempt-1"),
        sequence=1,
    )
    missing = record_provider_failure(
        cell,
        request_ids=["req-1", "req-2", "req-3"],
        error_type="TimeoutError",
    )
    assert missing.correct is None
    with pytest.raises(ValidationError):
        WorldResultV13.model_validate(
            {**missing.model_dump(mode="json"), "correct": False}
        )

    wrong = record_model_response(
        cell,
        raw_response='{"response_action":"NONE"}',
        request_id="req-invalid",
        rendered_context="",
    )
    assert wrong.status == "wrong_output"
    assert wrong.correct is False


def test_result_audit_rejects_manual_grade_extra_evidence_and_retry_bypass():
    cell = RequestCell(
        candidate_id="case",
        category="correction",
        split="visible",
        world_id="world_a",
        is_gate_world=True,
        expected_action=ResponseAction.ASK_PERMISSION,
        policy="B-summary",
        replicate=1,
        execution_attempt_id=canonical_sha256("attempt-1"),
        sequence=1,
    )
    valid = record_model_response(
        cell,
        raw_response='{"response_action":"ASK_PERMISSION"}',
        request_id="req-final",
        rendered_context="",
        prior_failures=[
            AttemptV13(
                attempt=1,
                request_id="req-first",
                outcome="provider_failure",
                error_type="TimeoutError",
            )
        ],
    )
    with pytest.raises(ValidationError, match="Extra inputs"):
        WorldResultV13.model_validate(
            {
                **valid.model_dump(mode="json"),
                "context_detected_claim_ids": ["claim-spoofed"],
            }
        )
    with pytest.raises(ValidationError, match="final response attempt"):
        WorldResultV13.model_validate(
            {**valid.model_dump(mode="json"), "request_id": None}
        )
    with pytest.raises(ValidationError, match="consecutive"):
        WorldResultV13.model_validate(
            {
                **valid.model_dump(mode="json"),
                "attempts": [
                    {
                        "attempt": 2,
                        "request_id": "selective-retry",
                        "outcome": "response",
                        "raw_response": '{"response_action":"ASK_PERMISSION"}',
                    }
                ],
                "request_id": "selective-retry",
            }
        )
    forged = valid.model_copy(
        update={
            "attempts": [
                valid.attempts[-1].model_copy(
                    update={"raw_response": "not frozen JSON"}
                )
            ]
        }
    )
    with pytest.raises(ValueError, match="frozen grader replay"):
        validate_result_audit(
            forged,
            expected_action=cell.expected_action,
            retry_attempts=3,
        )
    over_limit = record_model_response(
        cell,
        raw_response='{"response_action":"ASK_PERMISSION"}',
        request_id="req-four",
        rendered_context="",
        prior_failures=[
            AttemptV13(
                attempt=index,
                request_id=f"req-{index}",
                outcome="provider_failure",
                error_type="TimeoutError",
            )
            for index in range(1, 4)
        ],
    )
    with pytest.raises(ValueError, match="exceeds frozen retry"):
        validate_result_audit(
            over_limit,
            expected_action=cell.expected_action,
            retry_attempts=3,
        )


def test_checkpoint_accepts_only_consecutive_complete_replicates_and_hides_records(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2),
    )
    checkpoint = build_checkpoint(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        records,
        run_manifest=_run_manifest(selection_v13),
    )
    assert checkpoint.completed_replicates == 2
    assert next_resume_replicate(checkpoint) == 3
    public = public_checkpoint_status(checkpoint)
    assert set(public) == {
        "checkpoint_sha256",
        "completed_replicates",
        "aggregate_status",
    }
    serialized = str(public)
    for entry in selection_v13.selected:
        if entry.split == "holdout":
            assert entry.candidate_id not in serialized

    only_second = [record for record in records if record.replicate == 2]
    with pytest.raises(ValueError, match="consecutive"):
        build_checkpoint(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            only_second,
            run_manifest=_run_manifest(selection_v13),
        )


def test_resume_manifest_binds_checkpoint_model_and_next_replicate(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2),
    )
    checkpoint = build_checkpoint(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        records,
        run_manifest=_run_manifest(selection_v13),
    )
    run_manifest = _run_manifest(selection_v13)
    resume = build_resume_manifest(
        checkpoint,
        run_manifest,
        interrupted_at=datetime(2026, 8, 3, tzinfo=timezone.utc),
        interruption_reason="worker interruption",
    )
    assert resume.next_replicate == 3
    assert len(resume.request_ids) == len(records)
    validate_resume_manifest(resume, checkpoint, run_manifest)

    changed_model = run_manifest.model_copy(
        update={"model_id": "provider/model-v2"}
    )
    with pytest.raises(ValueError, match="drift"):
        validate_resume_manifest(resume, checkpoint, changed_model)


def test_run_cannot_switch_to_a_second_incident_history_path(
    selection_v13,
    registry_v13,
    tmp_path,
):
    signed = _signed_manifest(
        selection_v13,
        history_path=str(tmp_path / "authoritative-invalidation.json"),
    )
    run_manifest = _run_manifest(selection_v13, signed)
    records = _records(
        selection_v13,
        registry_v13,
        signed_manifest=signed,
    )
    switched = signed.model_copy(
        update={
            "invalidation_history_path": str(
                tmp_path / "second-invalidation.json"
            )
        }
    )
    with pytest.raises(ValueError, match="signed conformance identity drift"):
        build_checkpoint(
            selection_v13,
            registry_v13,
            switched,
            records,
            run_manifest=run_manifest,
        )

    changed_run = run_manifest.model_copy(
        update={
            "incident_history_path": str(
                tmp_path / "second-run-history.json"
            )
        }
    )
    with pytest.raises(ValueError, match="does not match"):
        resolve_run_incident(
            changed_run,
            RunIncident.API_FAILURE,
            current_run_manifest_sha256=run_manifest.sha256,
            external_outage_evidence=True,
            evidence_sha256=canonical_sha256("provider-status-page"),
        )


def test_counterworld_results_never_change_the_g0_denominator(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2, 3),
    )
    run_manifest = _run_manifest(selection_v13)
    baseline = evaluate_gate_worlds(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        records,
        run_manifest=run_manifest,
    )
    entry_by_id = {
        entry.candidate_id: entry for entry in selection_v13.selected
    }
    mutated = [
        _incorrect(record)
        if record.world_id
        != entry_by_id[record.candidate_id].gate_world_id
        else record
        for record in records
    ]
    after = evaluate_gate_worlds(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        mutated,
        run_manifest=run_manifest,
    )
    assert (baseline.decision, baseline.reason, baseline.counts) == (
        after.decision,
        after.reason,
        after.counts,
    )


def test_two_replicates_cannot_be_mistaken_for_an_official_gate_run(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2),
    )
    with pytest.raises(ValueError, match="official gate"):
        evaluate_gate_worlds(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            records,
            run_manifest=_run_manifest(selection_v13),
        )


def test_gate_rejects_gate_only_shifted_and_extra_cells(
    selection_v13,
    registry_v13,
):
    run_manifest = _run_manifest(selection_v13)
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2, 3),
    )
    gate_by_id = {
        entry.candidate_id: entry.gate_world_id
        for entry in selection_v13.selected
    }
    gate_only = [
        record for record in records
        if record.world_id == gate_by_id[record.candidate_id]
    ]
    with pytest.raises(ValueError, match="partial"):
        evaluate_gate_worlds(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            gate_only,
            run_manifest=run_manifest,
        )

    shifted = [
        record.model_copy(update={"replicate": record.replicate + 1})
        for record in records
    ]
    with pytest.raises(ValueError, match="official gate"):
        evaluate_gate_worlds(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            shifted,
            run_manifest=run_manifest,
        )

    extra = records[0].model_copy(update={"candidate_id": "extra-cell"})
    with pytest.raises(ValueError, match="partial"):
        evaluate_gate_worlds(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            [*records, extra],
            run_manifest=run_manifest,
        )


def test_v13_gate_preserves_same_world_path_a_and_original_thresholds(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2, 3),
    )
    visible = [entry for entry in selection_v13.selected if entry.split == "visible"]
    holdout = [entry for entry in selection_v13.selected if entry.split == "holdout"]
    failures = [*visible[:5], *holdout[:2]]
    assert len({entry.category for entry in failures}) >= 2
    failure_ids = {entry.candidate_id for entry in failures}
    mutated = [
        _incorrect(record)
        if record.candidate_id in failure_ids
        and record.policy == "B-summary"
        and record.world_id
        == next(
            entry.gate_world_id
            for entry in selection_v13.selected
            if entry.candidate_id == record.candidate_id
        )
        else record
        for record in records
    ]
    decision = evaluate_gate_worlds(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        mutated,
        run_manifest=_run_manifest(selection_v13),
    )
    assert decision.decision == "GO"
    assert decision.reason == "GO_PATH_A"
    assert decision.counts["Path A majority paired cases"] == "7/24"


def test_missing_gate_cell_is_api_inconclusive_not_a_smaller_denominator(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2, 3),
    )
    entry = selection_v13.selected[0]
    missing_index = next(
        index
        for index, record in enumerate(records)
        if record.candidate_id == entry.candidate_id
        and record.world_id == entry.gate_world_id
    )
    with pytest.raises(ValueError, match="partial"):
        evaluate_gate_worlds(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            [*records[:missing_index], *records[missing_index + 1 :]],
            run_manifest=_run_manifest(selection_v13),
        )


def test_gate_rejects_a_tampered_grade(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2, 3),
    )
    entry = selection_v13.selected[0]
    index = next(
        index
        for index, record in enumerate(records)
        if record.candidate_id == entry.candidate_id
        and record.world_id == entry.gate_world_id
    )
    tampered = records[index].model_copy(update={"correct": False})
    with pytest.raises(ValueError, match="grade drift"):
        evaluate_gate_worlds(
            selection_v13,
            registry_v13,
            _signed_manifest(selection_v13),
            [*records[:index], tampered, *records[index + 1 :]],
            run_manifest=_run_manifest(selection_v13),
        )


def test_path_b_uses_frozen_lexicon_for_the_gate_world_only(
    selection_v13,
    registry_v13,
):
    records = _records(
        selection_v13,
        registry_v13,
        replicates=(1, 2, 3),
    )
    entry = next(
        value for value in selection_v13.selected
        if value.category == "context-exception"
    )
    candidate = next(
        value for value in registry_v13.candidates
        if value.candidate_id == entry.candidate_id
    )
    gate_world = next(
        world for world in candidate.worlds
        if world.world_id == entry.gate_world_id
    )
    other_world = next(
        world for world in candidate.worlds
        if world.world_id != entry.gate_world_id
    )
    other_claim = next(
        claim for claim in candidate.tracked_claims
        if claim.claim_id == other_world.applicable_claim_id
    )
    gate_claim = next(
        claim for claim in candidate.tracked_claims
        if claim.claim_id == gate_world.applicable_claim_id
    )
    assert detect_applicable_claim_ids(
        candidate,
        gate_world.world_id,
        f"Currently: {gate_claim.surface_forms[0]}",
    ) == (gate_claim.claim_id,)
    assert detect_applicable_claim_ids(
        candidate,
        gate_world.world_id,
        "",
    ) == ()
    assert detect_applicable_claim_ids(
        candidate,
        gate_world.world_id,
        f"Currently: {other_claim.surface_forms[0]}",
    ) == ()
    target_indexes = [
        index for index, record in enumerate(records)
        if record.candidate_id == entry.candidate_id
        and record.world_id == entry.gate_world_id
        and record.policy == "B-full"
    ]
    wrong_world_context = f"Currently: {other_claim.surface_forms[0]}"
    mutated = list(records)
    for index in target_indexes:
        mutated[index] = mutated[index].model_copy(
            update={"rendered_context": wrong_world_context}
        )
    decision = evaluate_gate_worlds(
        selection_v13,
        registry_v13,
        _signed_manifest(selection_v13),
        mutated,
        run_manifest=_run_manifest(selection_v13),
    )
    assert decision.counts["Path B majority paired cases"] == "0/10"
    assert gate_world.applicable_claim_id != other_world.applicable_claim_id


def test_termination_matrix_blocks_result_driven_restarts_and_m1(
    selection_v13,
    tmp_path,
):
    history_index = 0

    def fresh_run():
        nonlocal history_index
        history_index += 1
        signed = _signed_manifest(
            selection_v13,
            history_path=str(tmp_path / f"invalidation-{history_index}.json"),
        )
        run_manifest = _run_manifest(selection_v13, signed)
        initialize_run_incident_history(run_manifest)
        return run_manifest

    run = fresh_run()
    assert (
        resolve_run_incident(
            run,
            RunIncident.STOP_COMMON_FLOOR,
            current_run_manifest_sha256=run.sha256,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
    run = fresh_run()
    assert (
        resolve_run_incident(
            run,
            RunIncident.STOP_NO_HEADROOM_PATH,
            current_run_manifest_sha256=run.sha256,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
    run = fresh_run()
    assert (
        resolve_run_incident(
            run,
            RunIncident.MODEL_VARIANCE_AFTER_FIVE,
            current_run_manifest_sha256=run.sha256,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
    run = fresh_run()
    assert (
        resolve_run_incident(
            run,
            RunIncident.SEMANTIC_CHANGE_REQUEST,
            current_run_manifest_sha256=run.sha256,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
    run = fresh_run()
    assert (
        resolve_run_incident(
            run,
            RunIncident.GO_PATH,
            current_run_manifest_sha256=run.sha256,
        )
        == IncidentDisposition.AUTHORIZE_M1
    )


def test_api_evidence_controls_replay_only_and_second_failure_terminates(
    selection_v13,
    tmp_path,
):
    signed = _signed_manifest(
        selection_v13,
        history_path=str(tmp_path / "no-evidence-invalidation.json"),
    )
    run_manifest = _run_manifest(selection_v13, signed)
    run_hash = run_manifest.sha256
    initialize_run_incident_history(run_manifest)
    assert (
        resolve_run_incident(
            run_manifest,
            RunIncident.API_FAILURE,
            current_run_manifest_sha256=run_hash,
            external_outage_evidence=False,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
    changed_manifest = canonical_sha256("changed run manifest")
    with pytest.raises(ValueError, match="does not match"):
        resolve_run_incident(
            run_manifest,
            RunIncident.API_FAILURE,
            current_run_manifest_sha256=changed_manifest,
            external_outage_evidence=True,
            evidence_sha256=canonical_sha256("provider-status-page-3"),
        )
    replay_signed = _signed_manifest(
        selection_v13,
        history_path=str(tmp_path / "replay-invalidation.json"),
    )
    replay_run = _run_manifest(selection_v13, replay_signed)
    initialize_run_incident_history(replay_run)
    with pytest.raises(FileExistsError):
        initialize_run_incident_history(replay_run)
    assert (
        resolve_run_incident(
            replay_run,
            RunIncident.API_FAILURE,
            current_run_manifest_sha256=replay_run.sha256,
            external_outage_evidence=True,
            evidence_sha256=canonical_sha256("provider-status-page"),
        )
        == IncidentDisposition.REPLAY_IDENTICAL_MANIFEST
    )
    assert (
        resolve_run_incident(
            replay_run,
            RunIncident.API_FAILURE,
            current_run_manifest_sha256=replay_run.sha256,
            external_outage_evidence=True,
            evidence_sha256=canonical_sha256("provider-status-page-2"),
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )


def test_only_one_nonsemantic_conformance_repair_is_permitted(
    selection_v13,
    tmp_path,
):
    signed = _signed_manifest(
        selection_v13,
        history_path=str(tmp_path / "repair-invalidation.json"),
    )
    run_manifest = _run_manifest(selection_v13, signed)
    run_hash = run_manifest.sha256
    initialize_run_incident_history(run_manifest)
    assert (
        resolve_run_incident(
            run_manifest,
            RunIncident.IMPLEMENTATION_NONCONFORMANCE,
            current_run_manifest_sha256=run_hash,
            changes_frozen_semantics=False,
        )
        == IncidentDisposition.VOID_AND_CONFORMANCE_REPAIR
    )
    assert (
        resolve_run_incident(
            run_manifest,
            RunIncident.IMPLEMENTATION_NONCONFORMANCE,
            current_run_manifest_sha256=run_hash,
            changes_frozen_semantics=False,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
    semantic_signed = _signed_manifest(
        selection_v13,
        history_path=str(tmp_path / "semantic-invalidation.json"),
    )
    semantic_run = _run_manifest(selection_v13, semantic_signed)
    initialize_run_incident_history(semantic_run)
    assert (
        resolve_run_incident(
            semantic_run,
            RunIncident.IMPLEMENTATION_NONCONFORMANCE,
            current_run_manifest_sha256=semantic_run.sha256,
            changes_frozen_semantics=True,
        )
        == IncidentDisposition.TERMINATE_NO_M1
    )
