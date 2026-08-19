from __future__ import annotations

import hashlib
import json
import random
import re
from dataclasses import dataclass
from datetime import date, datetime
from enum import StrEnum
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from innerflow_v2.reliability.gate import Cell, G0Decision, evaluate_g0
from innerflow_v2.reliability.protocol_v13 import (
    EXPECTED_CATEGORY_COUNTS,
    POLICIES,
    CandidateRegistryV13,
    CandidateV13,
    ObservedRendering,
    ResponseAction,
    SelectedCandidate,
    SelectionManifestV13,
    SignedConformanceManifest,
    candidate_sha256,
    canonical_sha256,
    classify_rendering,
    pool_sha256,
    registry_sha256,
    selection_manifest_sha256,
    signed_conformance_manifest_sha256,
    validate_authoritative_selection,
    validate_selection_manifest,
)


OUTER_JSON_FENCE = re.compile(
    r"\A```json[ \t]*\r?\n(?P<body>[\s\S]*?)\r?\n```[ \t]*\Z"
)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


@dataclass(frozen=True)
class ActionGradeV13:
    correct: bool
    action: ResponseAction | None
    reason: str


class DuplicateKeyError(ValueError):
    pass


def normalize_response_transport(raw: str) -> str:
    value = raw.strip()
    match = OUTER_JSON_FENCE.fullmatch(value)
    if match:
        return match.group("body")
    return value


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKeyError(key)
        result[key] = value
    return result


def grade_response_action(
    raw: str,
    *,
    expected: ResponseAction,
) -> ActionGradeV13:
    try:
        value = json.loads(
            normalize_response_transport(raw),
            object_pairs_hook=_unique_object,
        )
    except (json.JSONDecodeError, TypeError, DuplicateKeyError):
        return ActionGradeV13(False, None, "invalid_json")
    if not isinstance(value, dict) or set(value) != {"response_action"}:
        return ActionGradeV13(False, None, "invalid_schema")
    try:
        action = ResponseAction(value["response_action"])
    except (ValueError, TypeError):
        return ActionGradeV13(False, None, "invalid_enum")
    if action != expected:
        return ActionGradeV13(False, action, "wrong_action")
    return ActionGradeV13(True, action, "correct")


class FrozenActionExecutor(StrictModel):
    behavior_by_action: dict[ResponseAction, str]

    @model_validator(mode="after")
    def _complete_ontology(self) -> "FrozenActionExecutor":
        if set(self.behavior_by_action) != set(ResponseAction):
            raise ValueError("executor must bind every and only frozen action")
        if any(not value.strip() for value in self.behavior_by_action.values()):
            raise ValueError("executor behavior templates must be non-empty")
        return self

    @property
    def sha256(self) -> str:
        return canonical_sha256(
            {
                action.value: self.behavior_by_action[action]
                for action in sorted(ResponseAction, key=lambda value: value.value)
            }
        )

    def execute(self, action: ResponseAction) -> str:
        return self.behavior_by_action[action]


def load_frozen_action_executor(path: str | Path) -> FrozenActionExecutor:
    return FrozenActionExecutor.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


@dataclass(frozen=True)
class RequestCell:
    candidate_id: str
    category: str
    split: str
    world_id: str
    is_gate_world: bool
    expected_action: ResponseAction
    policy: str
    replicate: int
    execution_attempt_id: str
    sequence: int

    @property
    def key(self) -> tuple[str, str, str, int]:
        return self.candidate_id, self.world_id, self.policy, self.replicate


class AttemptV13(StrictModel):
    attempt: int = Field(ge=1)
    request_id: str = Field(min_length=1)
    provider_request_id: str | None = None
    outcome: Literal["response", "provider_failure"]
    raw_response: str | None = None
    error_type: str | None = None

    @model_validator(mode="after")
    def _attempt_evidence(self) -> "AttemptV13":
        if self.outcome == "response":
            if self.raw_response is None or self.error_type is not None:
                raise ValueError("response attempt must retain raw response only")
        elif self.raw_response is not None or not self.error_type:
            raise ValueError("provider failure attempt must retain error type only")
        return self


class FormationCallV13(StrictModel):
    attempt: int = Field(ge=1)
    operation: str = Field(min_length=1)
    provider_request_id: str | None = None
    prompt_sha256: str
    raw_response: str

    @model_validator(mode="after")
    def _prompt_hash(self) -> "FormationCallV13":
        if not re.fullmatch(r"[0-9a-f]{64}", self.prompt_sha256):
            raise ValueError("formation prompt hash must be lowercase SHA-256")
        return self


class WorldResultV13(StrictModel):
    candidate_id: str
    category: str
    split: Literal["visible", "holdout"]
    world_id: str
    policy: Literal["B-summary", "B-full", "B-none"]
    replicate: int = Field(ge=1, le=5)
    execution_attempt_id: str
    sequence: int = Field(ge=1)
    status: Literal["complete", "wrong_output", "provider_failure"]
    correct: bool | None
    response_action: ResponseAction | None = None
    request_id: str | None = None
    attempts: list[AttemptV13] = Field(min_length=1)
    formation_calls: list[FormationCallV13] = Field(default_factory=list)
    rendered_context: str | None = None

    @model_validator(mode="after")
    def _status_contract(self) -> "WorldResultV13":
        if not re.fullmatch(r"[0-9a-f]{64}", self.execution_attempt_id):
            raise ValueError("execution attempt id must be lowercase SHA-256")
        if [attempt.attempt for attempt in self.attempts] != list(
            range(1, len(self.attempts) + 1)
        ):
            raise ValueError("attempt ledger must be consecutive from one")
        if len({attempt.request_id for attempt in self.attempts}) != len(
            self.attempts
        ):
            raise ValueError("attempt request ids must be unique")
        if self.status == "provider_failure":
            if (
                self.correct is not None
                or self.response_action is not None
                or self.rendered_context is not None
                or any(attempt.outcome != "provider_failure" for attempt in self.attempts)
            ):
                raise ValueError("provider failure must remain a missing cell")
        elif self.correct is None:
            raise ValueError("complete/wrong-output cells need a boolean grade")
        elif self.rendered_context is None:
            raise ValueError("completed result must retain rendered context")
        elif self.status == "complete" and self.response_action is None:
            raise ValueError("complete model response must contain a frozen action")
        elif self.status == "wrong_output" and (
            self.correct is not False or self.response_action is not None
        ):
            raise ValueError("wrong-output cell must be incorrect without an action")
        if self.status != "provider_failure":
            final = self.attempts[-1]
            if (
                final.outcome != "response"
                or self.request_id != final.request_id
                or any(
                    attempt.outcome != "provider_failure"
                    for attempt in self.attempts[:-1]
                )
            ):
                raise ValueError("completed result must bind its final response attempt")
        return self

    @property
    def key(self) -> tuple[str, str, str, int]:
        return self.candidate_id, self.world_id, self.policy, self.replicate


def validate_execution_identity(
    manifest: SelectionManifestV13,
    registry: CandidateRegistryV13,
    signed_manifest: SignedConformanceManifest,
) -> dict[str, CandidateV13]:
    validate_authoritative_selection(manifest, signed_manifest)
    by_id = {candidate.candidate_id: candidate for candidate in registry.candidates}
    validate_selection_manifest(manifest, by_id)
    if registry_sha256(registry) != manifest.registry_sha256:
        raise ValueError("candidate registry drift after selection")
    hashes = {
        candidate.candidate_id: candidate_sha256(candidate)
        for candidate in registry.candidates
    }
    if pool_sha256(hashes) != manifest.pool_sha256:
        raise ValueError("candidate pool drift after selection")
    selected_hashes = {
        entry.candidate_id: entry.candidate_sha256 for entry in manifest.selected
    }
    if any(
        hashes.get(candidate_id) != value
        for candidate_id, value in selected_hashes.items()
    ):
        raise ValueError("selected candidate content drift")
    return by_id


def build_request_order(
    manifest: SelectionManifestV13,
    registry: CandidateRegistryV13,
    signed_manifest: SignedConformanceManifest,
    *,
    replicate: int,
    execution_attempt_id: str,
    order_seed: int,
) -> list[RequestCell]:
    if not re.fullmatch(r"[0-9a-f]{64}", execution_attempt_id):
        raise ValueError("execution attempt id must be lowercase SHA-256")
    by_id = validate_execution_identity(manifest, registry, signed_manifest)
    work: list[RequestCell] = []
    for entry in manifest.selected:
        candidate = by_id.get(entry.candidate_id)
        if candidate is None:
            raise ValueError("selection references an unavailable candidate")
        worlds = [entry.gate_world_id, *entry.counter_world_ids]
        if set(worlds) != {world.world_id for world in candidate.worlds}:
            raise ValueError("selection worlds drift from candidate")
        for world_id in worlds:
            expected_action = next(
                world.gold_response_action
                for world in candidate.worlds
                if world.world_id == world_id
            )
            for policy in POLICIES:
                work.append(
                    RequestCell(
                        candidate_id=entry.candidate_id,
                        category=entry.category,
                        split=entry.split,
                        world_id=world_id,
                        is_gate_world=world_id == entry.gate_world_id,
                        expected_action=expected_action,
                        policy=policy,
                        replicate=replicate,
                        execution_attempt_id=execution_attempt_id,
                        sequence=0,
                    )
                )
    random.Random(order_seed + replicate).shuffle(work)
    return [
        RequestCell(
            candidate_id=cell.candidate_id,
            category=cell.category,
            split=cell.split,
            world_id=cell.world_id,
            is_gate_world=cell.is_gate_world,
            expected_action=cell.expected_action,
            policy=cell.policy,
            replicate=cell.replicate,
            execution_attempt_id=cell.execution_attempt_id,
            sequence=index,
        )
        for index, cell in enumerate(work, start=1)
    ]


def request_order_sha256(order: list[RequestCell]) -> str:
    return canonical_sha256(
        [
            {
                "candidate_id": cell.candidate_id,
                "world_id": cell.world_id,
                "expected_action": cell.expected_action.value,
                "policy": cell.policy,
                "replicate": cell.replicate,
                "execution_attempt_id": cell.execution_attempt_id,
                "sequence": cell.sequence,
            }
            for cell in order
        ]
    )


def record_model_response(
    cell: RequestCell,
    *,
    raw_response: str,
    request_id: str,
    provider_request_id: str | None = None,
    rendered_context: str,
    prior_failures: list[AttemptV13] | None = None,
    formation_calls: list[FormationCallV13] | None = None,
) -> WorldResultV13:
    attempts = list(prior_failures or ())
    attempts.append(
        AttemptV13(
            attempt=len(attempts) + 1,
            request_id=request_id,
            provider_request_id=provider_request_id,
            outcome="response",
            raw_response=raw_response,
        )
    )
    grade = grade_response_action(raw_response, expected=cell.expected_action)
    return WorldResultV13(
        candidate_id=cell.candidate_id,
        category=cell.category,
        split=cell.split,
        world_id=cell.world_id,
        policy=cell.policy,
        replicate=cell.replicate,
        execution_attempt_id=cell.execution_attempt_id,
        sequence=cell.sequence,
        status="complete" if grade.action is not None else "wrong_output",
        correct=grade.correct,
        response_action=grade.action,
        request_id=request_id,
        attempts=attempts,
        formation_calls=list(formation_calls or ()),
        rendered_context=rendered_context,
    )


def record_provider_failure(
    cell: RequestCell,
    *,
    request_ids: list[str],
    error_type: str,
    provider_request_ids: list[str | None] | None = None,
    formation_calls: list[FormationCallV13] | None = None,
) -> WorldResultV13:
    provider_ids = provider_request_ids or [None] * len(request_ids)
    if len(provider_ids) != len(request_ids):
        raise ValueError("provider request-id ledger length drift")
    return WorldResultV13(
        candidate_id=cell.candidate_id,
        category=cell.category,
        split=cell.split,
        world_id=cell.world_id,
        policy=cell.policy,
        replicate=cell.replicate,
        execution_attempt_id=cell.execution_attempt_id,
        sequence=cell.sequence,
        status="provider_failure",
        correct=None,
        request_id=None,
        attempts=[
            AttemptV13(
                attempt=index,
                request_id=request_id,
                provider_request_id=provider_ids[index - 1],
                outcome="provider_failure",
                error_type=error_type,
            )
            for index, request_id in enumerate(request_ids, start=1)
        ],
        formation_calls=list(formation_calls or ()),
        rendered_context=None,
    )


def validate_result_audit(
    result: WorldResultV13,
    *,
    expected_action: ResponseAction,
    retry_attempts: int,
) -> None:
    if len(result.attempts) > retry_attempts:
        raise ValueError("attempt ledger exceeds frozen retry policy")
    if result.status == "provider_failure":
        if len(result.attempts) != retry_attempts:
            raise ValueError("provider failure did not exhaust frozen retries")
        return
    final_raw = result.attempts[-1].raw_response
    assert final_raw is not None
    grade = grade_response_action(final_raw, expected=expected_action)
    expected_status = "complete" if grade.action is not None else "wrong_output"
    if (
        result.status != expected_status
        or result.correct != grade.correct
        or result.response_action != grade.action
    ):
        raise ValueError("stored result does not match frozen grader replay")


def validate_complete_replicate(
    manifest: SelectionManifestV13,
    registry: CandidateRegistryV13,
    signed_manifest: SignedConformanceManifest,
    records: list[WorldResultV13],
    *,
    replicate: int,
    run_manifest: FrozenRunManifestV13,
) -> None:
    validate_run_identity(manifest, signed_manifest, run_manifest)
    replicate_records = [
        record for record in records if record.replicate == replicate
    ]
    execution_attempt_ids = {
        record.execution_attempt_id for record in replicate_records
    }
    if len(execution_attempt_ids) != 1:
        raise ValueError("replicate cells must share one execution attempt identity")
    execution_attempt_id = next(iter(execution_attempt_ids))
    incident_history = load_run_incident_history(
        run_manifest.incident_history_path
    )
    if incident_history.bound_run_manifest_sha256 != run_manifest.sha256:
        raise ValueError("run incident history identity drift")
    voided_attempts = {
        event.execution_attempt_id
        for event in incident_history.events
        if event.disposition == IncidentDisposition.VOID_PARTIAL_REPLICATE
    }
    if execution_attempt_id in voided_attempts:
        raise ValueError("replicate execution attempt was voided after interruption")
    expected_order = build_request_order(
        manifest,
        registry,
        signed_manifest,
        replicate=replicate,
        execution_attempt_id=execution_attempt_id,
        order_seed=run_manifest.order_seed,
    )
    expected = {cell.key: cell for cell in expected_order}
    actual = {record.key: record for record in replicate_records}
    if len(actual) != len(replicate_records):
        raise ValueError("duplicate result cell")
    if set(actual) != set(expected):
        raise ValueError("partial or extra replicate cells")
    request_ids = [
        attempt.request_id
        for result in actual.values()
        for attempt in result.attempts
    ]
    if len(set(request_ids)) != len(request_ids):
        raise ValueError("request ids must be unique across the replicate")
    for key, result in actual.items():
        if result.status == "provider_failure":
            raise ValueError("provider failure leaves the replicate incomplete")
        expected_cell = expected[key]
        if result.sequence != expected_cell.sequence:
            raise ValueError("request order drift")
        if (result.category, result.split) != (
            expected_cell.category,
            expected_cell.split,
        ):
            raise ValueError("result metadata drift")
        if result.status == "complete" and result.correct != (
            result.response_action == expected_cell.expected_action
        ):
            raise ValueError("result grade drift")
        validate_result_audit(
            result,
            expected_action=expected_cell.expected_action,
            retry_attempts=run_manifest.retry_attempts,
        )


class SealedCheckpoint(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    selection_sha256: str
    run_manifest_sha256: str
    completed_replicates: int
    order_hashes: dict[int, str]
    records: list[WorldResultV13]

    @property
    def sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


def build_checkpoint(
    manifest: SelectionManifestV13,
    registry: CandidateRegistryV13,
    signed_manifest: SignedConformanceManifest,
    records: list[WorldResultV13],
    *,
    run_manifest: FrozenRunManifestV13,
) -> SealedCheckpoint:
    validate_run_identity(manifest, signed_manifest, run_manifest)
    replicate_ids = sorted({record.replicate for record in records})
    if replicate_ids != list(range(1, len(replicate_ids) + 1)):
        raise ValueError("checkpoints require consecutive complete replicates")
    order_hashes = {}
    for replicate in replicate_ids:
        validate_complete_replicate(
            manifest,
            registry,
            signed_manifest,
            records,
            replicate=replicate,
            run_manifest=run_manifest,
        )
        order_hashes[replicate] = request_order_sha256(
            build_request_order(
                manifest,
                registry,
                signed_manifest,
                replicate=replicate,
                execution_attempt_id=next(
                    record.execution_attempt_id
                    for record in records
                    if record.replicate == replicate
                ),
                order_seed=run_manifest.order_seed,
            )
        )
    all_request_ids = [
        attempt.request_id for record in records for attempt in record.attempts
    ]
    if len(set(all_request_ids)) != len(all_request_ids):
        raise ValueError("request ids must be unique across the frozen run")
    return SealedCheckpoint(
        selection_sha256=selection_manifest_sha256(manifest),
        run_manifest_sha256=run_manifest.sha256,
        completed_replicates=len(replicate_ids),
        order_hashes=order_hashes,
        records=records,
    )


def public_checkpoint_status(checkpoint: SealedCheckpoint) -> dict[str, Any]:
    return {
        "checkpoint_sha256": checkpoint.sha256,
        "completed_replicates": checkpoint.completed_replicates,
        "aggregate_status": "complete-checkpoint",
    }


def next_resume_replicate(checkpoint: SealedCheckpoint | None) -> int:
    return 1 if checkpoint is None else checkpoint.completed_replicates + 1


class FrozenRunManifestV13(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    source_commit: str
    selection_sha256: str
    signed_conformance_manifest_sha256: str
    candidate_pool_sha256: str
    incident_history_path: str = Field(min_length=1)
    run_date: date
    model_id: str = Field(min_length=1)
    provider_model_version: str = Field(min_length=1)
    provider: str = Field(min_length=1)
    provider_base_url: str = Field(min_length=1)
    provider_model_metadata_sha256: str
    embedding_model_id: str = Field(min_length=1)
    formation_temperature: float = Field(gt=0)
    response_temperature: float = Field(gt=0)
    token_limits: dict[str, int]
    prompt_hashes: dict[str, str]
    ontology_sha256: str
    normalizer_sha256: str
    executor_sha256: str
    dependency_lock_sha256: str
    implementation_source_hashes: dict[str, str]
    order_seed: int
    initial_replicates: Literal[3] = 3
    variance_replicates: Literal[5] = 5
    compression_threshold_rounds: Literal[10] = 10
    keep_recent_rounds: Literal[4] = 4
    retry_attempts: int = Field(ge=1)
    retry_backoff_seconds: float = Field(ge=0)
    request_timeout_seconds: float = Field(gt=0)

    @property
    def sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))

    @model_validator(mode="after")
    def _hashes_are_sha256(self) -> "FrozenRunManifestV13":
        for field_name in (
            "source_commit",
            "selection_sha256",
            "signed_conformance_manifest_sha256",
            "candidate_pool_sha256",
            "provider_model_metadata_sha256",
            "ontology_sha256",
            "normalizer_sha256",
            "executor_sha256",
            "dependency_lock_sha256",
        ):
            expected = r"[0-9a-f]{40}" if field_name == "source_commit" else r"[0-9a-f]{64}"
            if not re.fullmatch(expected, getattr(self, field_name)):
                label = "Git commit" if field_name == "source_commit" else "SHA-256"
                raise ValueError(f"{field_name} must be lowercase {label}")
        if not self.token_limits or any(
            value <= 0 for value in self.token_limits.values()
        ):
            raise ValueError("all frozen token limits must be positive")
        required_stages = {"summary", "wiki", "reflection", "response"}
        if set(self.token_limits) != required_stages:
            raise ValueError("token limits must bind every and only frozen stage")
        if set(self.prompt_hashes) != required_stages or any(
            not re.fullmatch(r"[0-9a-f]{64}", value)
            for value in self.prompt_hashes.values()
        ):
            raise ValueError("prompt hashes must bind every frozen stage")
        if not self.implementation_source_hashes or any(
            not re.fullmatch(r"[0-9a-f]{64}", value)
            for value in self.implementation_source_hashes.values()
        ):
            raise ValueError("implementation source hashes must be SHA-256")
        if not Path(self.incident_history_path).is_absolute():
            raise ValueError("run incident history path must be absolute")
        return self


def validate_run_identity(
    selection: SelectionManifestV13,
    signed_manifest: SignedConformanceManifest,
    run_manifest: FrozenRunManifestV13,
) -> None:
    validate_authoritative_selection(selection, signed_manifest)
    if run_manifest.selection_sha256 != selection_manifest_sha256(selection):
        raise ValueError("run manifest selection drift")
    if (
        run_manifest.signed_conformance_manifest_sha256
        != signed_conformance_manifest_sha256(signed_manifest)
    ):
        raise ValueError("run manifest signed conformance identity drift")
    if run_manifest.candidate_pool_sha256 != selection.pool_sha256:
        raise ValueError("run manifest candidate pool drift")


class ResumeManifestV13(StrictModel):
    interrupted_at: datetime
    interruption_reason: str = Field(min_length=1)
    provider: str
    model_id: str
    provider_model_version: str
    request_ids: list[str]
    checkpoint_sha256: str
    selection_sha256: str
    run_manifest_sha256: str
    order_hashes: dict[int, str]
    next_replicate: int

    @model_validator(mode="after")
    def _resume_is_a_complete_checkpoint_boundary(self) -> "ResumeManifestV13":
        if self.interrupted_at.tzinfo is None:
            raise ValueError("resume interruption timestamp must be timezone-aware")
        for value in (
            self.checkpoint_sha256,
            self.selection_sha256,
            self.run_manifest_sha256,
            *self.order_hashes.values(),
        ):
            if not re.fullmatch(r"[0-9a-f]{64}", value):
                raise ValueError("resume hashes must be lowercase SHA-256")
        return self


def build_resume_manifest(
    checkpoint: SealedCheckpoint,
    run_manifest: FrozenRunManifestV13,
    *,
    interrupted_at: datetime,
    interruption_reason: str,
) -> ResumeManifestV13:
    if checkpoint.selection_sha256 != run_manifest.selection_sha256:
        raise ValueError("checkpoint selection differs from frozen run manifest")
    if checkpoint.run_manifest_sha256 != run_manifest.sha256:
        raise ValueError("checkpoint run manifest drift")
    return ResumeManifestV13(
        interrupted_at=interrupted_at,
        interruption_reason=interruption_reason,
        provider=run_manifest.provider,
        model_id=run_manifest.model_id,
        provider_model_version=run_manifest.provider_model_version,
        request_ids=[
            record.request_id
            for record in checkpoint.records
            if record.request_id is not None
        ],
        checkpoint_sha256=checkpoint.sha256,
        selection_sha256=checkpoint.selection_sha256,
        run_manifest_sha256=run_manifest.sha256,
        order_hashes=checkpoint.order_hashes,
        next_replicate=checkpoint.completed_replicates + 1,
    )


def validate_resume_manifest(
    resume: ResumeManifestV13,
    checkpoint: SealedCheckpoint,
    run_manifest: FrozenRunManifestV13,
) -> None:
    expected = build_resume_manifest(
        checkpoint,
        run_manifest,
        interrupted_at=resume.interrupted_at,
        interruption_reason=resume.interruption_reason,
    )
    if resume != expected:
        raise ValueError("resume manifest drift")


@dataclass(frozen=True)
class _GoldProjection:
    narrow_context_memory_id: str | None


@dataclass(frozen=True)
class GateScenarioProjection:
    case_id: str
    category: str
    split: str
    gold: _GoldProjection


def detect_applicable_claim_ids(
    candidate: CandidateV13,
    world_id: str,
    rendered_context: str,
) -> tuple[str, ...]:
    if candidate.category != "context-exception":
        return ()
    world = next(
        (value for value in candidate.worlds if value.world_id == world_id),
        None,
    )
    if world is None or world.applicable_claim_id is None:
        return ()
    claim = next(
        (
            value for value in candidate.tracked_claims
            if value.claim_id == world.applicable_claim_id
        ),
        None,
    )
    if claim is None:
        raise ValueError("gate world applicable claim is unavailable")
    rendering = classify_rendering(rendered_context, claim)
    if rendering == ObservedRendering.NOT_DETECTED_UNDER_FROZEN_LEXICON:
        return ()
    return (claim.claim_id,)


def evaluate_gate_worlds(
    manifest: SelectionManifestV13,
    registry: CandidateRegistryV13,
    signed_manifest: SignedConformanceManifest,
    records: list[WorldResultV13],
    *,
    run_manifest: FrozenRunManifestV13,
) -> G0Decision:
    by_candidate = validate_execution_identity(
        manifest,
        registry,
        signed_manifest,
    )
    entry_by_id = {entry.candidate_id: entry for entry in manifest.selected}
    replicate_ids = sorted({record.replicate for record in records})
    if replicate_ids not in ([1, 2, 3], [1, 2, 3, 4, 5]):
        raise ValueError("official gate requires consecutive replicates from one")
    for replicate in replicate_ids:
        validate_complete_replicate(
            manifest,
            registry,
            signed_manifest,
            records,
            replicate=replicate,
            run_manifest=run_manifest,
        )
    expected_gate_keys = {
        (entry.candidate_id, entry.gate_world_id, policy, replicate)
        for entry in manifest.selected
        for policy in POLICIES
        for replicate in replicate_ids
    }
    gate_records = [
        record
        for record in records
        if record.candidate_id in entry_by_id
        and record.world_id == entry_by_id[record.candidate_id].gate_world_id
    ]
    if (
        {record.key for record in gate_records} != expected_gate_keys
        or len(gate_records) != len(expected_gate_keys)
    ):
        return G0Decision(
            "INCONCLUSIVE",
            "INCONCLUSIVE_API_FAILURE",
            ["one or more gate-world cells are unavailable"],
        )
    for record in gate_records:
        expected_action = next(
            world.gold_response_action
            for world in by_candidate[record.candidate_id].worlds
            if world.world_id == record.world_id
        )
        if record.status == "complete" and record.correct != (
            record.response_action == expected_action
        ):
            raise ValueError("gate-world grade drift")
    scenarios = []
    applicable_claim_by_candidate: dict[str, str | None] = {}
    for entry in manifest.selected:
        candidate = by_candidate[entry.candidate_id]
        gate_world = next(
            world
            for world in candidate.worlds
            if world.world_id == entry.gate_world_id
        )
        current_claim = gate_world.applicable_claim_id
        applicable_claim_by_candidate[entry.candidate_id] = current_claim
        scenarios.append(
            GateScenarioProjection(
                case_id=entry.candidate_id,
                category=entry.category,
                split=entry.split,
                gold=_GoldProjection(
                    current_claim if entry.category == "context-exception" else None
                ),
            )
        )
    cells = []
    for record in gate_records:
        detected: tuple[str, ...] = ()
        if (
            record.policy == "B-full"
            and record.category == "context-exception"
        ):
            assert record.rendered_context is not None
            detected = detect_applicable_claim_ids(
                by_candidate[record.candidate_id],
                record.world_id,
                record.rendered_context,
            )
        cells.append(
            Cell(
                case_id=record.candidate_id,
                policy=record.policy,
                replicate=record.replicate,
                correct=record.correct,
                context_source_ids=detected,
            )
        )
    return evaluate_g0(scenarios, cells)  # type: ignore[arg-type]


class RunIncident(StrEnum):
    STOP_COMMON_FLOOR = "STOP_COMMON_FLOOR"
    STOP_NO_HEADROOM_PATH = "STOP_NO_HEADROOM_PATH"
    MODEL_VARIANCE_AFTER_FIVE = "MODEL_VARIANCE_AFTER_FIVE"
    API_FAILURE = "API_FAILURE"
    IMPLEMENTATION_NONCONFORMANCE = "IMPLEMENTATION_NONCONFORMANCE"
    PARTIAL_REPLICATE_INTERRUPTION = "PARTIAL_REPLICATE_INTERRUPTION"
    SEMANTIC_CHANGE_REQUEST = "SEMANTIC_CHANGE_REQUEST"
    GO_PATH = "GO_PATH"


class IncidentDisposition(StrEnum):
    AUTHORIZE_M1 = "AUTHORIZE_M1"
    TERMINATE_NO_M1 = "TERMINATE_NO_M1"
    REPLAY_IDENTICAL_MANIFEST = "REPLAY_IDENTICAL_MANIFEST"
    VOID_AND_CONFORMANCE_REPAIR = "VOID_AND_CONFORMANCE_REPAIR"
    VOID_PARTIAL_REPLICATE = "VOID_PARTIAL_REPLICATE"


class RunIncidentEvent(StrictModel):
    incident: RunIncident
    run_manifest_sha256: str
    attempted_run_manifest_sha256: str
    disposition: IncidentDisposition
    evidence_sha256: str | None = None
    execution_attempt_id: str | None = None

    @model_validator(mode="after")
    def _event_hashes(self) -> "RunIncidentEvent":
        for value in (
            self.run_manifest_sha256,
            self.attempted_run_manifest_sha256,
        ):
            if not re.fullmatch(r"[0-9a-f]{64}", value):
                raise ValueError("incident event manifest hashes must be SHA-256")
        if self.evidence_sha256 is not None and not re.fullmatch(
            r"[0-9a-f]{64}", self.evidence_sha256
        ):
            raise ValueError("incident evidence hash must be SHA-256")
        if self.execution_attempt_id is not None and not re.fullmatch(
            r"[0-9a-f]{64}", self.execution_attempt_id
        ):
            raise ValueError("incident execution attempt id must be SHA-256")
        attempt_bound = self.incident in {
            RunIncident.PARTIAL_REPLICATE_INTERRUPTION,
            RunIncident.API_FAILURE,
        }
        if attempt_bound != (self.execution_attempt_id is not None):
            raise ValueError(
                "partial/API incidents must bind exactly one execution attempt"
            )
        if (
            self.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION
            and self.disposition
            != IncidentDisposition.VOID_PARTIAL_REPLICATE
        ):
            raise ValueError("partial interruption must void its attempt")
        if (
            self.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION
            and self.evidence_sha256 is None
        ):
            raise ValueError("partial interruption must bind its artifact hash")
        return self


class RunIncidentHistory(StrictModel):
    bound_run_manifest_sha256: str
    events: list[RunIncidentEvent] = Field(default_factory=list)

    @model_validator(mode="after")
    def _events_match_bound_run(self) -> "RunIncidentHistory":
        if not re.fullmatch(r"[0-9a-f]{64}", self.bound_run_manifest_sha256):
            raise ValueError("incident history must bind a run-manifest SHA-256")
        if any(
            event.run_manifest_sha256 != self.bound_run_manifest_sha256
            for event in self.events
        ):
            raise ValueError("incident event drifts from bound run manifest")
        if (
            sum(
                event.disposition
                == IncidentDisposition.REPLAY_IDENTICAL_MANIFEST
                for event in self.events
            )
            > 1
            or sum(
                event.disposition
                == IncidentDisposition.VOID_AND_CONFORMANCE_REPAIR
                for event in self.events
            )
            > 1
        ):
            raise ValueError("incident history exceeds one replay or repair")
        partial_attempts: set[str] = set()
        classified_attempts: set[str] = set()
        for event in self.events:
            attempt_id = event.execution_attempt_id
            if event.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION:
                if attempt_id in partial_attempts:
                    raise ValueError("execution attempt was voided more than once")
                partial_attempts.add(attempt_id)  # type: ignore[arg-type]
            elif event.incident == RunIncident.API_FAILURE:
                if attempt_id not in partial_attempts:
                    raise ValueError(
                        "API failure must classify a prior partial attempt"
                    )
                if attempt_id in classified_attempts:
                    raise ValueError(
                        "execution attempt API failure was classified twice"
                    )
                classified_attempts.add(attempt_id)  # type: ignore[arg-type]
        return self

    @property
    def history_sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


def initialize_run_incident_history(
    run_manifest: FrozenRunManifestV13,
) -> RunIncidentHistory:
    history_path = Path(run_manifest.incident_history_path)
    history = RunIncidentHistory(
        bound_run_manifest_sha256=run_manifest.sha256
    )
    with history_path.open("x", encoding="utf-8") as stream:
        stream.write(history.model_dump_json(indent=2) + "\n")
    return history


def load_run_incident_history(path: str | Path) -> RunIncidentHistory:
    return RunIncidentHistory.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


def _persist_run_incident_history(
    path: str | Path,
    history: RunIncidentHistory,
) -> None:
    history_path = Path(path)
    temporary = history_path.with_name(f".{history_path.name}.tmp")
    temporary.write_text(
        history.model_dump_json(indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(history_path)


def resolve_run_incident(
    run_manifest: FrozenRunManifestV13,
    incident: RunIncident,
    *,
    current_run_manifest_sha256: str,
    external_outage_evidence: bool = False,
    evidence_sha256: str | None = None,
    changes_frozen_semantics: bool = False,
    execution_attempt_id: str | None = None,
) -> IncidentDisposition:
    if current_run_manifest_sha256 != run_manifest.sha256:
        raise ValueError("incident request does not match supplied run manifest")
    history_path = Path(run_manifest.incident_history_path)
    history = load_run_incident_history(history_path)
    terminal_history = any(
        event.disposition
        in {
            IncidentDisposition.AUTHORIZE_M1,
            IncidentDisposition.TERMINATE_NO_M1,
        }
        for event in history.events
    )
    if terminal_history:
        raise ValueError("terminal run cannot accept another incident")
    elif incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION:
        if execution_attempt_id is None or not re.fullmatch(
            r"[0-9a-f]{64}", execution_attempt_id
        ):
            raise ValueError("partial interruption must identify its attempt")
        if any(
            event.execution_attempt_id == execution_attempt_id
            for event in history.events
        ):
            raise ValueError("execution attempt already has an incident")
        disposition = IncidentDisposition.VOID_PARTIAL_REPLICATE
    elif current_run_manifest_sha256 != history.bound_run_manifest_sha256:
        disposition = IncidentDisposition.TERMINATE_NO_M1
    elif incident == RunIncident.GO_PATH:
        disposition = IncidentDisposition.AUTHORIZE_M1
    elif incident == RunIncident.API_FAILURE:
        if execution_attempt_id is None or not re.fullmatch(
            r"[0-9a-f]{64}", execution_attempt_id
        ):
            raise ValueError("API failure must identify its failed attempt")
        partial_events = [
            event
            for event in history.events
            if event.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION
            and event.execution_attempt_id == execution_attempt_id
        ]
        if len(partial_events) != 1:
            raise ValueError("API failure must classify one prior partial attempt")
        if any(
            event.incident == RunIncident.API_FAILURE
            and event.execution_attempt_id == execution_attempt_id
            for event in history.events
        ):
            raise ValueError("failed attempt was already classified")
        prior_replays = sum(
            event.disposition == IncidentDisposition.REPLAY_IDENTICAL_MANIFEST
            for event in history.events
        )
        if (
            external_outage_evidence
            and evidence_sha256 is not None
            and re.fullmatch(r"[0-9a-f]{64}", evidence_sha256)
            and prior_replays == 0
        ):
            disposition = IncidentDisposition.REPLAY_IDENTICAL_MANIFEST
        else:
            disposition = IncidentDisposition.TERMINATE_NO_M1
    elif execution_attempt_id is not None:
        raise ValueError("only partial/API incidents may bind an attempt id")
    elif incident == RunIncident.IMPLEMENTATION_NONCONFORMANCE:
        prior_repairs = sum(
            event.disposition
            == IncidentDisposition.VOID_AND_CONFORMANCE_REPAIR
            for event in history.events
        )
        if prior_repairs == 0 and not changes_frozen_semantics:
            disposition = IncidentDisposition.VOID_AND_CONFORMANCE_REPAIR
        else:
            disposition = IncidentDisposition.TERMINATE_NO_M1
    else:
        disposition = IncidentDisposition.TERMINATE_NO_M1
    history.events.append(
        RunIncidentEvent(
            incident=incident,
            run_manifest_sha256=history.bound_run_manifest_sha256,
            attempted_run_manifest_sha256=current_run_manifest_sha256,
            disposition=disposition,
            evidence_sha256=evidence_sha256,
            execution_attempt_id=execution_attempt_id,
        )
    )
    _persist_run_incident_history(history_path, history)
    return disposition
