from __future__ import annotations

import time
from dataclasses import dataclass

from innerflow_v2.reliability.baselines import (
    FaithfulSummaryPolicy,
    FormationOutputError,
    MemoryMessage,
    ModelCall,
)
from innerflow_v2.reliability.client import (
    ChatBackend,
    EmbeddingBackend,
    ProviderCallError,
)
from innerflow_v2.reliability.execution_v13 import (
    AttemptV13,
    FrozenActionExecutor,
    FormationCallV13,
    FrozenRunManifestV13,
    RequestCell,
    WorldResultV13,
    build_request_order,
    record_model_response,
    record_provider_failure,
)
from innerflow_v2.reliability.prompts import response_action_prompt
from innerflow_v2.reliability.protocol_v13 import (
    CandidateRegistryV13,
    CandidateV13,
    CounterfactualWorld,
    SelectionManifestV13,
    SignedConformanceManifest,
    canonical_sha256,
)


@dataclass(frozen=True)
class _TerminalFormationOutput:
    raw_response: str
    rendered_context: str
    calls: tuple[ModelCall, ...]


@dataclass(frozen=True)
class _RenderedContext:
    value: str
    calls: tuple[ModelCall, ...] = ()


def execution_attempt_id(
    run_manifest: FrozenRunManifestV13,
    *,
    replicate: int,
    epoch: int = 1,
) -> str:
    return canonical_sha256(
        {
            "domain": "m0-v1.3-execution-attempt",
            "run_manifest_sha256": run_manifest.sha256,
            "replicate": replicate,
            "epoch": epoch,
        }
    )


def logical_request_id(
    run_manifest: FrozenRunManifestV13,
    cell: RequestCell,
    *,
    attempt: int,
) -> str:
    return canonical_sha256(
        {
            "domain": "m0-v1.3-logical-request",
            "run_manifest_sha256": run_manifest.sha256,
            "replicate": cell.replicate,
            "execution_attempt_id": cell.execution_attempt_id,
            "sequence": cell.sequence,
            "candidate_id": cell.candidate_id,
            "world_id": cell.world_id,
            "policy": cell.policy,
            "attempt": attempt,
        }
    )


def _messages(world: CounterfactualWorld) -> list[MemoryMessage]:
    timestamp_base = 1_700_000_000_000
    return [
        MemoryMessage(
            role=event.role,
            content=event.content,
            timestamp=timestamp_base + event.sequence_index,
            source_ids=(event.event_id,),
        )
        for event in world.setup_memory_events
    ]


def _full_context(world: CounterfactualWorld) -> str:
    rows = ["[Complete Memory History]"]
    labels = {"user": "User", "assistant": "AI", "system": "Context"}
    rows.extend(
        f"{labels[event.role]}: {event.content}"
        for event in world.setup_memory_events
    )
    return "\n".join(rows) + "\n"


def _summary_context(
    world: CounterfactualWorld,
    *,
    backend: ChatBackend,
    embedding_backend: EmbeddingBackend | None,
    run_manifest: FrozenRunManifestV13,
) -> _RenderedContext | _TerminalFormationOutput:
    policy = FaithfulSummaryPolicy(
        backend,
        today=run_manifest.run_date,
        compression_threshold_rounds=(
            run_manifest.compression_threshold_rounds
        ),
        keep_recent_rounds=run_manifest.keep_recent_rounds,
        formation_temperature=run_manifest.formation_temperature,
        response_temperature=run_manifest.response_temperature,
        embedding_backend=embedding_backend,
        summary_max_tokens=run_manifest.token_limits["summary"],
        wiki_max_tokens=run_manifest.token_limits["wiki"],
        reflection_max_tokens=run_manifest.token_limits["reflection"],
        response_max_tokens=run_manifest.token_limits["response"],
    )
    try:
        for message in _messages(world):
            policy.add_message(message)
        policy.end_session()
    except FormationOutputError as error:
        context, _ = policy.build_context()
        return _TerminalFormationOutput(
            raw_response=error.raw_response,
            rendered_context=context,
            calls=tuple(policy.trace.calls),
        )
    context, _ = policy.build_context()
    return _RenderedContext(context, tuple(policy.trace.calls))


def _render_context(
    cell: RequestCell,
    world: CounterfactualWorld,
    *,
    backend: ChatBackend,
    embedding_backend: EmbeddingBackend | None,
    run_manifest: FrozenRunManifestV13,
) -> _RenderedContext | _TerminalFormationOutput:
    if cell.policy == "B-summary":
        return _summary_context(
            world,
            backend=backend,
            embedding_backend=embedding_backend,
            run_manifest=run_manifest,
        )
    if cell.policy == "B-full":
        return _RenderedContext(_full_context(world))
    if cell.policy == "B-none":
        return _RenderedContext("")
    raise ValueError(f"unsupported frozen policy: {cell.policy}")


def _run_cell(
    cell: RequestCell,
    candidate: CandidateV13,
    *,
    backend: ChatBackend,
    embedding_backend: EmbeddingBackend | None,
    executor: FrozenActionExecutor,
    run_manifest: FrozenRunManifestV13,
) -> WorldResultV13:
    world = next(
        world for world in candidate.worlds if world.world_id == cell.world_id
    )
    failures: list[AttemptV13] = []
    request_ids: list[str] = []
    provider_request_ids: list[str | None] = []
    formation_calls: list[FormationCallV13] = []
    for attempt in range(1, run_manifest.retry_attempts + 1):
        request_id = logical_request_id(run_manifest, cell, attempt=attempt)
        request_ids.append(request_id)
        try:
            rendered = _render_context(
                cell,
                world,
                backend=backend,
                embedding_backend=embedding_backend,
                run_manifest=run_manifest,
            )
            formation_calls.extend(
                FormationCallV13(
                    attempt=attempt,
                    operation=call.operation,
                    provider_request_id=call.request_id,
                    prompt_sha256=canonical_sha256(call.prompt),
                    raw_response=call.response,
                )
                for call in rendered.calls
            )
            if isinstance(rendered, _TerminalFormationOutput):
                return record_model_response(
                    cell,
                    raw_response=rendered.raw_response,
                    request_id=request_id,
                    provider_request_id=(
                        rendered.calls[-1].request_id if rendered.calls else None
                    ),
                    rendered_context=rendered.rendered_context,
                    prior_failures=failures,
                    formation_calls=formation_calls,
                )
            prompt = response_action_prompt(
                rendered.value,
                current_message=world.probe,
                non_memory_state=world.non_memory_state,
                behavior_by_action={
                    action.value: behavior
                    for action, behavior in executor.behavior_by_action.items()
                },
            )
            completion = backend.complete(
                operation="memory.probe.response_action",
                prompt=prompt,
                temperature=run_manifest.response_temperature,
                max_tokens=run_manifest.token_limits["response"],
            )
            return record_model_response(
                cell,
                raw_response=completion.content,
                request_id=request_id,
                provider_request_id=completion.request_id,
                rendered_context=rendered.value,
                prior_failures=failures,
                formation_calls=formation_calls,
            )
        except ProviderCallError as error:
            provider_request_ids.append(error.provider_request_id)
            failures.append(
                AttemptV13(
                    attempt=attempt,
                    request_id=request_id,
                    provider_request_id=error.provider_request_id,
                    outcome="provider_failure",
                    error_type=error.cause_type,
                )
            )
            if attempt < run_manifest.retry_attempts:
                time.sleep(run_manifest.retry_backoff_seconds * attempt)
    return record_provider_failure(
        cell,
        request_ids=request_ids,
        error_type=failures[-1].error_type or "ProviderCallError",
        provider_request_ids=provider_request_ids,
        formation_calls=formation_calls,
    )


def run_complete_replicate(
    selection: SelectionManifestV13,
    registry: CandidateRegistryV13,
    signed_manifest: SignedConformanceManifest,
    *,
    replicate: int,
    backend: ChatBackend,
    embedding_backend: EmbeddingBackend | None,
    executor: FrozenActionExecutor,
    run_manifest: FrozenRunManifestV13,
    epoch: int = 1,
) -> list[WorldResultV13]:
    if replicate < 1 or replicate > run_manifest.variance_replicates:
        raise ValueError("replicate is outside the frozen 1..5 range")
    attempt_id = execution_attempt_id(
        run_manifest,
        replicate=replicate,
        epoch=epoch,
    )
    order = build_request_order(
        selection,
        registry,
        signed_manifest,
        replicate=replicate,
        execution_attempt_id=attempt_id,
        order_seed=run_manifest.order_seed,
    )
    candidates = {
        candidate.candidate_id: candidate for candidate in registry.candidates
    }
    records: list[WorldResultV13] = []
    for cell in order:
        result = _run_cell(
            cell,
            candidates[cell.candidate_id],
            backend=backend,
            embedding_backend=embedding_backend,
            executor=executor,
            run_manifest=run_manifest,
        )
        records.append(result)
        if result.status == "provider_failure":
            break
    return records
