from __future__ import annotations

import hashlib
import inspect
import json
from dataclasses import asdict
from datetime import date
from pathlib import Path
from typing import Any

from innerflow_v2.reliability.execution_v13 import (
    OUTER_JSON_FENCE,
    FrozenActionExecutor,
    FrozenRunManifestV13,
    build_request_order,
    grade_response_action,
    initialize_run_incident_history,
    normalize_response_transport,
    request_order_sha256,
)
from innerflow_v2.reliability.prompts import (
    first_extract_prompt,
    merge_prompt,
    reflection_prompt,
    response_action_prompt,
    summary_prompt,
)
from innerflow_v2.reliability.protocol_v13 import (
    ConformancePredicate,
    ResponseAction,
    SelectionManifestV13,
    SignedConformanceManifest,
    canonical_sha256,
    initialize_invalidation_history,
    public_selection_manifest,
    selection_manifest_sha256,
    signed_conformance_manifest_sha256,
)
from innerflow_v2.reliability.runner_v13 import execution_attempt_id
from innerflow_v2.reliability.selection_readiness import OfficialSelectionInputs


IMPLEMENTATION_FILES = (
    "v2/scripts/run_m0_v13_reliability.py",
    "v2/src/innerflow_v2/reliability/baselines.py",
    "v2/src/innerflow_v2/reliability/client.py",
    "v2/src/innerflow_v2/reliability/execution_freeze_v13.py",
    "v2/src/innerflow_v2/reliability/execution_v13.py",
    "v2/src/innerflow_v2/reliability/prompts.py",
    "v2/src/innerflow_v2/reliability/protocol_v13.py",
    "v2/src/innerflow_v2/reliability/runner_v13.py",
)


def file_sha256(path: str | Path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def frozen_prompt_hashes() -> dict[str, str]:
    return {
        "summary": canonical_sha256(inspect.getsource(summary_prompt)),
        "wiki": canonical_sha256(
            {
                "first_extract_prompt": inspect.getsource(first_extract_prompt),
                "merge_prompt": inspect.getsource(merge_prompt),
            }
        ),
        "reflection": canonical_sha256(inspect.getsource(reflection_prompt)),
        "response": canonical_sha256(inspect.getsource(response_action_prompt)),
    }


def frozen_normalizer_sha256() -> str:
    return canonical_sha256(
        {
            "outer_json_fence": OUTER_JSON_FENCE.pattern,
            "normalize_response_transport": inspect.getsource(
                normalize_response_transport
            ),
            "grade_response_action": inspect.getsource(grade_response_action),
        }
    )


def frozen_ontology_sha256() -> str:
    return canonical_sha256(sorted(action.value for action in ResponseAction))


def implementation_source_hashes(repo_root: str | Path) -> dict[str, str]:
    root = Path(repo_root)
    return {path: file_sha256(root / path) for path in IMPLEMENTATION_FILES}


def build_request_plan(
    *,
    selection: SelectionManifestV13,
    registry,
    signed_manifest: SignedConformanceManifest,
    run_manifest: FrozenRunManifestV13,
    replicates: range | tuple[int, ...],
) -> tuple[dict[str, Any], dict[str, Any]]:
    replicate_plans: list[dict[str, Any]] = []
    public_order_hashes: dict[str, str] = {}
    public_attempt_ids: dict[str, str] = {}
    for replicate in replicates:
        attempt_id = execution_attempt_id(run_manifest, replicate=replicate)
        order = build_request_order(
            selection,
            registry,
            signed_manifest,
            replicate=replicate,
            execution_attempt_id=attempt_id,
            order_seed=run_manifest.order_seed,
        )
        order_hash = request_order_sha256(order)
        replicate_plans.append(
            {
                "replicate": replicate,
                "execution_attempt_id": attempt_id,
                "order_sha256": order_hash,
                "cells": [asdict(cell) for cell in order],
            }
        )
        public_order_hashes[str(replicate)] = order_hash
        public_attempt_ids[str(replicate)] = attempt_id
    sealed_request_plan = {
        "protocol_version": "v1.3",
        "selection_sha256": selection_manifest_sha256(selection),
        "run_manifest_sha256": run_manifest.sha256,
        "replicates": replicate_plans,
    }
    public_request_plan = {
        "protocol_version": "v1.3",
        "selection_sha256": selection_manifest_sha256(selection),
        "run_manifest_sha256": run_manifest.sha256,
        "sealed_request_plan_sha256": canonical_sha256(sealed_request_plan),
        "request_order_sha256_by_replicate": public_order_hashes,
        "execution_attempt_id_by_replicate": public_attempt_ids,
    }
    return sealed_request_plan, public_request_plan


def build_execution_freeze(
    *,
    repo_root: str | Path,
    source_commit: str,
    selection: SelectionManifestV13,
    public_selection: dict[str, Any],
    official: OfficialSelectionInputs,
    executor: FrozenActionExecutor,
    sealed_root: str | Path,
    run_date: date,
    provider_model_metadata_sha256: str,
) -> tuple[
    SignedConformanceManifest,
    FrozenRunManifestV13,
    dict[str, Any],
    dict[str, Any],
]:
    root = Path(repo_root)
    sealed = Path(sealed_root).resolve()
    if public_selection != public_selection_manifest(selection):
        raise ValueError("sealed selection differs from public projection")
    if selection.pool_sha256 != official.public_freeze.eligible_pool_sha256:
        raise ValueError("selection pool differs from public freeze")

    signed = SignedConformanceManifest(
        pool_sha256=selection.pool_sha256,
        seed_sha256=hashlib.sha256(selection.beacon.seed_bytes).hexdigest(),
        selection_sha256=selection_manifest_sha256(selection),
        invalidation_history_path=str(
            sealed / "M0_V13_INVALIDATION_HISTORY.json"
        ),
        signed_by=[
            "owner:LING-6150",
            "reviewer:adversarial-agent-FREEZE_SELECTION",
        ],
        allowed_invalidation_predicates=set(ConformancePredicate),
    )
    run_manifest = FrozenRunManifestV13(
        source_commit=source_commit,
        selection_sha256=selection_manifest_sha256(selection),
        signed_conformance_manifest_sha256=(
            signed_conformance_manifest_sha256(signed)
        ),
        candidate_pool_sha256=selection.pool_sha256,
        incident_history_path=str(sealed / "M0_V13_RUN_INCIDENT_HISTORY.json"),
        run_date=run_date,
        model_id="gemini-2.5-flash",
        provider_model_version=(
            "ModelVerse alias gemini-2.5-flash; immutable upstream snapshot "
            "not exposed"
        ),
        provider="ModelVerse",
        provider_base_url="https://api.modelverse.cn/v1",
        provider_model_metadata_sha256=provider_model_metadata_sha256,
        embedding_model_id="text-embedding-3-large",
        formation_temperature=0.2,
        response_temperature=0.4,
        token_limits={
            "summary": 2048,
            "wiki": 4096,
            "reflection": 2048,
            "response": 1024,
        },
        prompt_hashes=frozen_prompt_hashes(),
        ontology_sha256=frozen_ontology_sha256(),
        normalizer_sha256=frozen_normalizer_sha256(),
        executor_sha256=executor.sha256,
        dependency_lock_sha256=file_sha256(root / "v2/uv.lock"),
        implementation_source_hashes=implementation_source_hashes(root),
        order_seed=6150,
        retry_attempts=3,
        retry_backoff_seconds=2.0,
        request_timeout_seconds=120.0,
    )

    sealed_request_plan, public_request_plan = build_request_plan(
        selection=selection,
        registry=official.registry,
        signed_manifest=signed,
        run_manifest=run_manifest,
        replicates=range(1, run_manifest.initial_replicates + 1),
    )
    public_freeze = {
        "protocol_version": "v1.3",
        "status": "FROZEN_BEFORE_FIRST_MODEL_REQUEST",
        "selection_sha256": selection_manifest_sha256(selection),
        "candidate_pool_sha256": selection.pool_sha256,
        "signed_conformance_manifest_sha256": (
            signed_conformance_manifest_sha256(signed)
        ),
        "run_manifest_sha256": run_manifest.sha256,
        "sealed_request_plan_sha256": public_request_plan[
            "sealed_request_plan_sha256"
        ],
        "request_order_sha256_by_replicate": public_request_plan[
            "request_order_sha256_by_replicate"
        ],
        "execution_attempt_id_by_replicate": public_request_plan[
            "execution_attempt_id_by_replicate"
        ],
        "visible_count": len(public_selection["visible"]),
        "holdout": public_selection["holdout"],
        "provider_model_metadata_sha256": provider_model_metadata_sha256,
        "holdout_disclosure": "aggregate-only",
        "limitations": [
            (
                "ModelVerse exposes gemini-2.5-flash as a mutable alias and "
                "does not expose an immutable upstream snapshot version."
            ),
            (
                "The sealed holdout is a process control, not cryptographic or "
                "organizational isolation from the repository owner."
            ),
        ],
        "authorization_boundary": (
            "No model request until this freeze receives FREEZE_EXECUTION and "
            "the execution-freeze PR is merged."
        ),
    }
    return signed, run_manifest, sealed_request_plan, public_freeze


def write_json_create_only(path: str | Path, payload: Any) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    value = payload.model_dump(mode="json") if hasattr(payload, "model_dump") else payload
    with target.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def persist_execution_freeze(
    *,
    signed: SignedConformanceManifest,
    run_manifest: FrozenRunManifestV13,
    sealed_request_plan: dict[str, Any],
    public_freeze: dict[str, Any],
    sealed_root: str | Path,
    public_run_manifest_path: str | Path,
    public_freeze_path: str | Path,
) -> None:
    sealed = Path(sealed_root).resolve()
    sealed.mkdir(parents=True, exist_ok=True)
    write_json_create_only(
        sealed / "M0_V13_SIGNED_CONFORMANCE.json",
        signed,
    )
    write_json_create_only(
        sealed / "M0_V13_REQUEST_PLAN.json",
        sealed_request_plan,
    )
    write_json_create_only(public_run_manifest_path, run_manifest)
    write_json_create_only(public_freeze_path, public_freeze)
    initialize_invalidation_history(signed)
    initialize_run_incident_history(run_manifest)
