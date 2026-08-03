#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.beacon_v2 import write_create_only
from innerflow_v2.reliability.client import OpenAICompatibleBackend
from innerflow_v2.reliability.execution_freeze_v13 import (
    build_execution_freeze,
    build_request_plan,
    file_sha256,
    persist_execution_freeze,
    write_json_create_only,
)
from innerflow_v2.reliability.execution_v13 import (
    FrozenRunManifestV13,
    SealedCheckpoint,
    build_checkpoint,
    evaluate_gate_worlds,
    load_frozen_action_executor,
    load_run_incident_history,
    public_checkpoint_status,
    validate_run_identity,
)
from innerflow_v2.reliability.protocol_v13 import (
    SelectionManifestV13,
    SignedConformanceManifest,
    load_invalidation_history,
    public_selection_manifest,
)
from innerflow_v2.reliability.runner_v13 import run_complete_replicate
from innerflow_v2.reliability.selection_readiness import (
    load_official_selection_inputs,
)


PUBLIC_SELECTION = ROOT / "eval/m0/manifests/M0_V13_SELECTION_PUBLIC.json"
ACTION_EXECUTOR = ROOT / "eval/m0/manifests/M0_V13_ACTION_EXECUTOR.json"
MODEL_METADATA = ROOT / "eval/m0/manifests/M0_V13_MODEL_METADATA.json"
RUN_MANIFEST = ROOT / "eval/m0/manifests/M0_V13_RUN_MANIFEST.json"
PUBLIC_FREEZE = ROOT / "eval/m0/manifests/M0_V13_EXECUTION_FREEZE_PUBLIC.json"
PUBLIC_VARIANCE_PLAN = (
    ROOT / "eval/m0/manifests/M0_V13_VARIANCE_REQUEST_PLAN_PUBLIC.json"
)
DEFAULT_SEALED_ROOT = Path(
    "/Users/apple/Documents/New project/innerflow-m0-sealed/execution"
)
DEFAULT_SELECTION = Path(
    "/Users/apple/Documents/New project/innerflow-m0-sealed/selection/"
    "M0_V13_SELECTION.json"
)


def _sealed_root() -> Path:
    return Path(
        os.environ.get("M0_V13_SEALED_ROOT", str(DEFAULT_SEALED_ROOT))
    ).resolve()


def _selection_path() -> Path:
    return Path(
        os.environ.get("M0_V13_SEALED_SELECTION", str(DEFAULT_SELECTION))
    ).resolve()


def _current_commit() -> str:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def _require_clean_worktree() -> None:
    status = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    if status:
        raise ValueError("prepare requires a clean committed worktree")


def _load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def _load_freeze_inputs():
    selection = SelectionManifestV13.model_validate_json(
        _selection_path().read_text(encoding="utf-8")
    )
    public_selection = _load_json(PUBLIC_SELECTION)
    official = load_official_selection_inputs(REPO_ROOT)
    executor = load_frozen_action_executor(ACTION_EXECUTOR)
    metadata_sha256 = file_sha256(MODEL_METADATA)
    return selection, public_selection, official, executor, metadata_sha256


def prepare() -> None:
    _require_clean_worktree()
    selection, public_selection, official, executor, metadata_sha256 = (
        _load_freeze_inputs()
    )
    metadata = _load_json(MODEL_METADATA)
    signed, run_manifest, request_plan, public_freeze = build_execution_freeze(
        repo_root=REPO_ROOT,
        source_commit=_current_commit(),
        selection=selection,
        public_selection=public_selection,
        official=official,
        executor=executor,
        sealed_root=_sealed_root(),
        run_date=metadata["run_date"],
        provider_model_metadata_sha256=metadata_sha256,
    )
    persist_execution_freeze(
        signed=signed,
        run_manifest=run_manifest,
        sealed_request_plan=request_plan,
        public_freeze=public_freeze,
        sealed_root=_sealed_root(),
        public_run_manifest_path=RUN_MANIFEST,
        public_freeze_path=PUBLIC_FREEZE,
    )
    print(
        json.dumps(
            {
                "status": "prepared-no-model-call",
                "run_manifest_sha256": run_manifest.sha256,
                "selection_sha256": run_manifest.selection_sha256,
                "request_plan_sha256": public_freeze[
                    "sealed_request_plan_sha256"
                ],
            },
            indent=2,
        )
    )


def preflight() -> tuple[
    SelectionManifestV13,
    SignedConformanceManifest,
    FrozenRunManifestV13,
]:
    selection, public_selection, official, executor, metadata_sha256 = (
        _load_freeze_inputs()
    )
    sealed = _sealed_root()
    signed = SignedConformanceManifest.model_validate_json(
        (sealed / "M0_V13_SIGNED_CONFORMANCE.json").read_text(
            encoding="utf-8"
        )
    )
    run_manifest = FrozenRunManifestV13.model_validate_json(
        RUN_MANIFEST.read_text(encoding="utf-8")
    )
    request_plan = _load_json(sealed / "M0_V13_REQUEST_PLAN.json")
    public_freeze = _load_json(PUBLIC_FREEZE)
    metadata = _load_json(MODEL_METADATA)
    expected = build_execution_freeze(
        repo_root=REPO_ROOT,
        source_commit=run_manifest.source_commit,
        selection=selection,
        public_selection=public_selection,
        official=official,
        executor=executor,
        sealed_root=sealed,
        run_date=metadata["run_date"],
        provider_model_metadata_sha256=metadata_sha256,
    )
    expected_signed, expected_run, expected_plan, expected_public = expected
    if signed != expected_signed:
        raise ValueError("signed conformance manifest drift")
    if run_manifest != expected_run:
        raise ValueError("frozen run manifest drift")
    if request_plan != expected_plan:
        raise ValueError("sealed request plan drift")
    if public_freeze != expected_public:
        raise ValueError("public execution freeze drift")
    if public_selection != public_selection_manifest(selection):
        raise ValueError("public selection projection drift")
    validate_run_identity(selection, signed, run_manifest)
    invalidations = load_invalidation_history(signed.invalidation_history_path)
    incidents = load_run_incident_history(run_manifest.incident_history_path)
    if invalidations.incidents:
        raise ValueError("selection invalidation history is non-empty")
    if incidents.events:
        raise ValueError("run incident history is non-empty before execution")
    return selection, signed, run_manifest


def run_replicate(replicate: int) -> None:
    selection, signed, run_manifest = preflight()
    sealed = _sealed_root()
    if replicate > run_manifest.initial_replicates:
        initial_decision = _load_json(
            sealed / "M0_V13_G0_INITIAL_DECISION.json"
        )
        if initial_decision.get("decision") != "NEEDS_FIVE_RUNS":
            raise ValueError("replicates 4-5 require NEEDS_FIVE_RUNS")
        expected_sealed, expected_public = build_request_plan(
            selection=selection,
            registry=load_official_selection_inputs(REPO_ROOT).registry,
            signed_manifest=signed,
            run_manifest=run_manifest,
            replicates=(4, 5),
        )
        if _load_json(sealed / "M0_V13_VARIANCE_REQUEST_PLAN.json") != expected_sealed:
            raise ValueError("sealed variance request plan drift")
        if _load_json(PUBLIC_VARIANCE_PLAN) != expected_public:
            raise ValueError("public variance request plan drift")
    checkpoint_path = sealed / f"M0_V13_CHECKPOINT_R{replicate}.json"
    if checkpoint_path.exists():
        raise FileExistsError(checkpoint_path)
    prior_records = []
    if replicate > 1:
        prior_checkpoint = SealedCheckpoint.model_validate_json(
            (
                sealed / f"M0_V13_CHECKPOINT_R{replicate - 1}.json"
            ).read_text(encoding="utf-8")
        )
        if prior_checkpoint.completed_replicates != replicate - 1:
            raise ValueError("previous checkpoint boundary drift")
        prior_records = prior_checkpoint.records
    elif any(sealed.glob("M0_V13_CHECKPOINT_R*.json")):
        raise ValueError("replicate one cannot start after another checkpoint")

    api_key = os.environ.get("MODELVERSE_API_KEY")
    if not api_key:
        raise ValueError("MODELVERSE_API_KEY is required")
    backend = OpenAICompatibleBackend(
        api_key=api_key,
        model=run_manifest.model_id,
        base_url=run_manifest.provider_base_url,
        embedding_model=run_manifest.embedding_model_id,
        timeout=run_manifest.request_timeout_seconds,
    )
    official = load_official_selection_inputs(REPO_ROOT)
    executor = load_frozen_action_executor(ACTION_EXECUTOR)
    records = run_complete_replicate(
        selection,
        official.registry,
        signed,
        replicate=replicate,
        backend=backend,
        embedding_backend=backend,
        executor=executor,
        run_manifest=run_manifest,
    )
    failed = [record for record in records if record.status == "provider_failure"]
    if failed:
        partial_path = sealed / f"M0_V13_PARTIAL_R{replicate}.json"
        write_json_create_only(
            partial_path,
            {
                "run_manifest_sha256": run_manifest.sha256,
                "replicate": replicate,
                "records": [record.model_dump(mode="json") for record in records],
            },
        )
        raise RuntimeError(
            f"replicate {replicate} incomplete: {len(failed)} provider failures; "
            f"sealed partial artifact: {partial_path}"
        )
    checkpoint = build_checkpoint(
        selection,
        official.registry,
        signed,
        [*prior_records, *records],
        run_manifest=run_manifest,
    )
    write_create_only(checkpoint_path, checkpoint)
    print(json.dumps(public_checkpoint_status(checkpoint), indent=2))


def evaluate() -> None:
    selection, signed, run_manifest = preflight()
    sealed = _sealed_root()
    replicate = 5 if (sealed / "M0_V13_CHECKPOINT_R5.json").exists() else 3
    checkpoint = SealedCheckpoint.model_validate_json(
        (sealed / f"M0_V13_CHECKPOINT_R{replicate}.json").read_text(
            encoding="utf-8"
        )
    )
    if checkpoint.completed_replicates != replicate:
        raise ValueError("G0 checkpoint boundary drift")
    official = load_official_selection_inputs(REPO_ROOT)
    decision = evaluate_gate_worlds(
        selection,
        official.registry,
        signed,
        checkpoint.records,
        run_manifest=run_manifest,
    )
    label = "FINAL" if replicate == 5 else "INITIAL"
    decision_path = sealed / f"M0_V13_G0_{label}_DECISION.json"
    write_json_create_only(decision_path, asdict(decision))
    print(json.dumps(asdict(decision), indent=2))


def prepare_variance() -> None:
    selection, signed, run_manifest = preflight()
    sealed = _sealed_root()
    initial = _load_json(sealed / "M0_V13_G0_INITIAL_DECISION.json")
    if initial.get("decision") != "NEEDS_FIVE_RUNS":
        raise ValueError("variance request plan requires NEEDS_FIVE_RUNS")
    sealed_plan, public_plan = build_request_plan(
        selection=selection,
        registry=load_official_selection_inputs(REPO_ROOT).registry,
        signed_manifest=signed,
        run_manifest=run_manifest,
        replicates=(4, 5),
    )
    write_json_create_only(
        sealed / "M0_V13_VARIANCE_REQUEST_PLAN.json",
        sealed_plan,
    )
    write_json_create_only(PUBLIC_VARIANCE_PLAN, public_plan)
    print(json.dumps(public_plan, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare or execute the frozen M0 v1.3 reliability run."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("prepare")
    subparsers.add_parser("preflight")
    run = subparsers.add_parser("run-replicate")
    run.add_argument(
        "--replicate", type=int, choices=(1, 2, 3, 4, 5), required=True
    )
    subparsers.add_parser("evaluate")
    subparsers.add_parser("prepare-variance")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare()
    elif args.command == "preflight":
        preflight()
        print(json.dumps({"status": "ready-no-model-call"}, indent=2))
    elif args.command == "run-replicate":
        run_replicate(args.replicate)
    elif args.command == "prepare-variance":
        prepare_variance()
    else:
        evaluate()


if __name__ == "__main__":
    main()
