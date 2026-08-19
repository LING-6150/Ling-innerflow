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
    IMPLEMENTATION_FILES,
    build_execution_freeze,
    build_request_plan,
    file_sha256,
    persist_execution_freeze,
    write_json_create_only,
)
from innerflow_v2.reliability.execution_v13 import (
    FrozenRunManifestV13,
    IncidentDisposition,
    RunIncident,
    SealedCheckpoint,
    build_checkpoint,
    evaluate_gate_worlds,
    load_frozen_action_executor,
    load_run_incident_history,
    public_checkpoint_status,
    resolve_run_incident,
    validate_run_identity,
)
from innerflow_v2.reliability.protocol_v13 import (
    SelectionManifestV13,
    SignedConformanceManifest,
    load_invalidation_history,
    public_selection_manifest,
)
from innerflow_v2.reliability.runner_v13 import (
    execution_attempt_id,
    run_complete_replicate,
)
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
DEFAULT_SEALED_ROOT = Path.home() / "Desktop/Ling-innerflow/.sealed/execution"
DEFAULT_SELECTION = (
    Path.home()
    / "Desktop/Ling-innerflow/.sealed/selection/M0_V13_SELECTION.json"
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


def _verify_source_boundary(
    source_commit: str,
    *,
    repo_root: Path = REPO_ROOT,
) -> None:
    ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", source_commit, "HEAD"],
        cwd=repo_root,
    )
    if ancestor.returncode != 0:
        raise ValueError("frozen source commit is not an ancestor of HEAD")
    changed = subprocess.run(
        ["git", "diff", "--quiet", source_commit, "HEAD", "--", *IMPLEMENTATION_FILES],
        cwd=repo_root,
    )
    dirty = subprocess.run(
        ["git", "status", "--porcelain", "--", *IMPLEMENTATION_FILES],
        cwd=repo_root,
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    if changed.returncode != 0 or dirty:
        raise ValueError("runtime-critical source drifted from frozen source commit")


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


def _partial_path(sealed: Path, replicate: int, epoch: int) -> Path:
    return sealed / f"M0_V13_PARTIAL_R{replicate}_E{epoch}.json"


def _incident_state(
    run_manifest: FrozenRunManifestV13,
    *,
    allow_pending_api_classification: bool = False,
) -> None:
    history = load_run_incident_history(run_manifest.incident_history_path)
    if any(
        event.disposition
        in {IncidentDisposition.AUTHORIZE_M1, IncidentDisposition.TERMINATE_NO_M1}
        for event in history.events
    ):
        raise ValueError("run incident history is terminal")
    if any(
        event.incident
        not in {
            RunIncident.PARTIAL_REPLICATE_INTERRUPTION,
            RunIncident.API_FAILURE,
        }
        for event in history.events
    ):
        raise ValueError("run incident history is not executable")
    partials = {
        event.execution_attempt_id: event
        for event in history.events
        if event.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION
    }
    classifications = {
        event.execution_attempt_id: event
        for event in history.events
        if event.incident == RunIncident.API_FAILURE
    }
    expected_attempts = {
        execution_attempt_id(run_manifest, replicate=replicate, epoch=epoch): (
            replicate,
            epoch,
        )
        for replicate in range(1, run_manifest.variance_replicates + 1)
        for epoch in (1, 2)
    }
    for attempt_id, event in partials.items():
        if attempt_id not in expected_attempts:
            raise ValueError("partial incident binds an unknown execution attempt")
        replicate, epoch = expected_attempts[attempt_id]
        partial_path = _partial_path(_sealed_root(), replicate, epoch)
        if (
            not partial_path.exists()
            or event.evidence_sha256 != file_sha256(partial_path)
        ):
            raise ValueError("partial incident artifact binding drift")
    pending = set(partials) - set(classifications)
    if pending and not allow_pending_api_classification:
        raise ValueError("provider-failure partial awaits API incident classification")


def _execution_epoch(run_manifest: FrozenRunManifestV13, replicate: int) -> int:
    history = load_run_incident_history(run_manifest.incident_history_path)
    first = execution_attempt_id(run_manifest, replicate=replicate, epoch=1)
    second = execution_attempt_id(run_manifest, replicate=replicate, epoch=2)
    by_attempt = {
        event.execution_attempt_id: event
        for event in history.events
        if event.incident == RunIncident.API_FAILURE
    }
    voided = {
        event.execution_attempt_id
        for event in history.events
        if event.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION
    }
    if first not in voided:
        return 1
    first_decision = by_attempt.get(first)
    if (
        first_decision is not None
        and first_decision.disposition
        == IncidentDisposition.REPLAY_IDENTICAL_MANIFEST
        and second not in voided
    ):
        return 2
    raise ValueError("replicate has no authorized executable epoch")


def preflight(
    *,
    allow_pending_api_classification: bool = False,
) -> tuple[
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
    _verify_source_boundary(run_manifest.source_commit)
    if public_selection != public_selection_manifest(selection):
        raise ValueError("public selection projection drift")
    validate_run_identity(selection, signed, run_manifest)
    invalidations = load_invalidation_history(signed.invalidation_history_path)
    if invalidations.incidents:
        raise ValueError("selection invalidation history is non-empty")
    _incident_state(
        run_manifest,
        allow_pending_api_classification=allow_pending_api_classification,
    )
    return selection, signed, run_manifest


def run_replicate(replicate: int) -> None:
    selection, signed, run_manifest = preflight()
    sealed = _sealed_root()
    epoch = _execution_epoch(run_manifest, replicate)
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
        epoch=epoch,
    )
    failed = [record for record in records if record.status == "provider_failure"]
    if failed:
        partial_path = _partial_path(sealed, replicate, epoch)
        write_json_create_only(
            partial_path,
            {
                "run_manifest_sha256": run_manifest.sha256,
                "replicate": replicate,
                "epoch": epoch,
                "execution_attempt_id": execution_attempt_id(
                    run_manifest, replicate=replicate, epoch=epoch
                ),
                "records": [record.model_dump(mode="json") for record in records],
            },
        )
        resolve_run_incident(
            run_manifest,
            RunIncident.PARTIAL_REPLICATE_INTERRUPTION,
            current_run_manifest_sha256=run_manifest.sha256,
            evidence_sha256=file_sha256(partial_path),
            execution_attempt_id=execution_attempt_id(
                run_manifest, replicate=replicate, epoch=epoch
            ),
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


def classify_api_failure(
    replicate: int,
    external_outage_evidence: Path | None,
) -> None:
    _, _, run_manifest = preflight(allow_pending_api_classification=True)
    history = load_run_incident_history(run_manifest.incident_history_path)
    pending = [
        event
        for event in history.events
        if event.incident == RunIncident.PARTIAL_REPLICATE_INTERRUPTION
        and not any(
            classified.incident == RunIncident.API_FAILURE
            and classified.execution_attempt_id == event.execution_attempt_id
            for classified in history.events
        )
    ]
    expected_attempts = {
        execution_attempt_id(run_manifest, replicate=replicate, epoch=epoch): epoch
        for epoch in (1, 2)
    }
    pending = [
        event
        for event in pending
        if event.execution_attempt_id in expected_attempts
    ]
    if len(pending) != 1:
        raise ValueError("replicate must have exactly one unclassified partial attempt")
    event = pending[0]
    epoch = expected_attempts[event.execution_attempt_id]
    partial_path = _partial_path(_sealed_root(), replicate, epoch)
    partial = _load_json(partial_path)
    if (
        partial.get("run_manifest_sha256") != run_manifest.sha256
        or partial.get("replicate") != replicate
        or partial.get("epoch") != epoch
        or partial.get("execution_attempt_id") != event.execution_attempt_id
        or event.evidence_sha256 != file_sha256(partial_path)
        or not partial.get("records")
        or partial["records"][-1].get("status") != "provider_failure"
    ):
        raise ValueError("sealed partial artifact does not match its incident")
    evidence_sha256 = (
        file_sha256(external_outage_evidence)
        if external_outage_evidence is not None
        else None
    )
    disposition = resolve_run_incident(
        run_manifest,
        RunIncident.API_FAILURE,
        current_run_manifest_sha256=run_manifest.sha256,
        external_outage_evidence=external_outage_evidence is not None,
        evidence_sha256=evidence_sha256,
        execution_attempt_id=event.execution_attempt_id,
    )
    print(json.dumps({"disposition": disposition.value}, indent=2))


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
    classify = subparsers.add_parser("classify-api-failure")
    classify.add_argument(
        "--replicate", type=int, choices=(1, 2, 3, 4, 5), required=True
    )
    classify.add_argument("--external-outage-evidence", type=Path)
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
    elif args.command == "classify-api-failure":
        classify_api_failure(args.replicate, args.external_outage_evidence)
    elif args.command == "prepare-variance":
        prepare_variance()
    else:
        evaluate()


if __name__ == "__main__":
    main()
