#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

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
    canonical_sha256,
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


class OutageSignoff(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    role: Literal["execution_operator", "independent_reviewer"]
    signer: str = Field(min_length=1)
    signed_at_utc: datetime
    signed_body_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")


class OutageClassificationArtifact(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schema_version: Literal["m0-v1.3-outage-classification-v1"]
    classification: Literal["CONFIRMED_PROVIDER_OUTAGE"]
    provider: str = Field(min_length=1)
    run_manifest_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    execution_attempt_id: str = Field(pattern=r"^[0-9a-f]{64}$")
    failed_request_ids: list[str] = Field(min_length=1)
    failed_provider_request_ids: list[str]
    failure_recorded_at_utc: datetime
    outage_started_at_utc: datetime
    outage_ended_at_utc: datetime
    external_reference: str = Field(min_length=1)
    classification_body_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    signoffs: list[OutageSignoff] = Field(min_length=2, max_length=2)

    def body_sha256(self) -> str:
        return canonical_sha256(
            self.model_dump(
                mode="json",
                exclude={"classification_body_sha256", "signoffs"},
            )
        )

    @model_validator(mode="after")
    def _validate_binding_and_signoffs(self) -> "OutageClassificationArtifact":
        timestamps = (
            self.failure_recorded_at_utc,
            self.outage_started_at_utc,
            self.outage_ended_at_utc,
            *(signoff.signed_at_utc for signoff in self.signoffs),
        )
        if any(value.tzinfo is None for value in timestamps):
            raise ValueError("outage artifact timestamps must be timezone-aware")
        if not (
            self.outage_started_at_utc
            <= self.failure_recorded_at_utc
            <= self.outage_ended_at_utc
        ):
            raise ValueError("outage window does not contain the failed attempt")
        if self.classification_body_sha256 != self.body_sha256():
            raise ValueError("outage classification body hash mismatch")
        if {signoff.role for signoff in self.signoffs} != {
            "execution_operator",
            "independent_reviewer",
        }:
            raise ValueError("outage artifact requires operator and reviewer signoff")
        if len({signoff.signer for signoff in self.signoffs}) != 2:
            raise ValueError("outage artifact signers must be independent")
        if any(
            signoff.signed_body_sha256 != self.classification_body_sha256
            for signoff in self.signoffs
        ):
            raise ValueError("outage signoff body hash mismatch")
        return self


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


def _expected_partial_attempts(
    run_manifest: FrozenRunManifestV13,
) -> dict[str, tuple[int, int, Path]]:
    sealed = _sealed_root()
    return {
        execution_attempt_id(run_manifest, replicate=replicate, epoch=epoch): (
            replicate,
            epoch,
            _partial_path(sealed, replicate, epoch),
        )
        for replicate in range(1, run_manifest.variance_replicates + 1)
        for epoch in (1, 2)
    }


def _validate_partial_artifact(
    run_manifest: FrozenRunManifestV13,
    *,
    attempt_id: str,
    replicate: int,
    epoch: int,
    path: Path,
) -> dict:
    partial = _load_json(path)
    records = partial.get("records")
    if (
        partial.get("run_manifest_sha256") != run_manifest.sha256
        or partial.get("replicate") != replicate
        or partial.get("epoch") != epoch
        or partial.get("execution_attempt_id") != attempt_id
        or not isinstance(records, list)
        or not records
        or records[-1].get("status") != "provider_failure"
        or any(record.get("execution_attempt_id") != attempt_id for record in records)
    ):
        raise ValueError("sealed partial artifact identity drift")
    recorded_at = datetime.fromisoformat(partial["failure_recorded_at_utc"])
    if recorded_at.tzinfo is None:
        raise ValueError("partial failure timestamp must be timezone-aware")
    return partial


def _incident_state(
    run_manifest: FrozenRunManifestV13,
    *,
    allow_pending_api_classification: bool = False,
    allow_orphan_attempt_id: str | None = None,
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
    expected_attempts = _expected_partial_attempts(run_manifest)
    path_to_attempt = {
        path: attempt_id
        for attempt_id, (_, _, path) in expected_attempts.items()
    }
    existing_paths = set(_sealed_root().glob("M0_V13_PARTIAL*.json"))
    unknown_paths = existing_paths - set(path_to_attempt)
    if unknown_paths:
        raise ValueError("sealed root contains an unknown partial artifact")
    existing_attempts = {path_to_attempt[path] for path in existing_paths}
    for attempt_id, event in partials.items():
        if attempt_id not in expected_attempts:
            raise ValueError("partial incident binds an unknown execution attempt")
        replicate, epoch, partial_path = expected_attempts[attempt_id]
        if not partial_path.exists():
            raise ValueError("partial incident artifact binding drift")
        _validate_partial_artifact(
            run_manifest,
            attempt_id=attempt_id,
            replicate=replicate,
            epoch=epoch,
            path=partial_path,
        )
        if event.evidence_sha256 != file_sha256(partial_path):
            raise ValueError("partial incident artifact binding drift")
    orphan_attempts = existing_attempts - set(partials)
    allowed_orphans = (
        {allow_orphan_attempt_id}
        if allow_orphan_attempt_id is not None
        else set()
    )
    if orphan_attempts != allowed_orphans:
        raise ValueError("unbound partial artifact blocks execution")
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
    allow_orphan_attempt_id: str | None = None,
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
        allow_orphan_attempt_id=allow_orphan_attempt_id,
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
                "failure_recorded_at_utc": datetime.now(timezone.utc).isoformat(),
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


def recover_partial_incident(replicate: int, epoch: int) -> None:
    run_manifest = FrozenRunManifestV13.model_validate_json(
        RUN_MANIFEST.read_text(encoding="utf-8")
    )
    attempt_id = execution_attempt_id(
        run_manifest,
        replicate=replicate,
        epoch=epoch,
    )
    _, _, verified_run = preflight(allow_orphan_attempt_id=attempt_id)
    partial_path = _partial_path(_sealed_root(), replicate, epoch)
    _validate_partial_artifact(
        verified_run,
        attempt_id=attempt_id,
        replicate=replicate,
        epoch=epoch,
        path=partial_path,
    )
    disposition = resolve_run_incident(
        verified_run,
        RunIncident.PARTIAL_REPLICATE_INTERRUPTION,
        current_run_manifest_sha256=verified_run.sha256,
        evidence_sha256=file_sha256(partial_path),
        execution_attempt_id=attempt_id,
    )
    print(json.dumps({"disposition": disposition.value}, indent=2))


def _validate_outage_classification(
    path: Path,
    *,
    run_manifest: FrozenRunManifestV13,
    attempt_id: str,
    partial: dict,
) -> OutageClassificationArtifact:
    artifact = OutageClassificationArtifact.model_validate_json(
        path.read_text(encoding="utf-8")
    )
    failure = partial["records"][-1]
    failed_request_ids = [attempt["request_id"] for attempt in failure["attempts"]]
    failed_provider_request_ids = [
        attempt["provider_request_id"]
        for attempt in failure["attempts"]
        if attempt.get("provider_request_id") is not None
    ]
    if (
        artifact.provider != run_manifest.provider
        or artifact.run_manifest_sha256 != run_manifest.sha256
        or artifact.execution_attempt_id != attempt_id
        or artifact.failed_request_ids != failed_request_ids
        or artifact.failed_provider_request_ids != failed_provider_request_ids
        or artifact.failure_recorded_at_utc
        != datetime.fromisoformat(partial["failure_recorded_at_utc"])
    ):
        raise ValueError("outage classification does not bind the failed attempt")
    return artifact


def classify_api_failure(
    replicate: int,
    outage_classification_artifact: Path | None,
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
    partial = _validate_partial_artifact(
        run_manifest,
        attempt_id=event.execution_attempt_id,
        replicate=replicate,
        epoch=epoch,
        path=partial_path,
    )
    if event.evidence_sha256 != file_sha256(partial_path):
        raise ValueError("sealed partial artifact does not match its incident")
    evidence_sha256 = None
    if outage_classification_artifact is not None:
        _validate_outage_classification(
            outage_classification_artifact,
            run_manifest=run_manifest,
            attempt_id=event.execution_attempt_id,
            partial=partial,
        )
        evidence_sha256 = file_sha256(outage_classification_artifact)
    disposition = resolve_run_incident(
        run_manifest,
        RunIncident.API_FAILURE,
        current_run_manifest_sha256=run_manifest.sha256,
        external_outage_evidence=outage_classification_artifact is not None,
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
    classify.add_argument("--outage-classification-artifact", type=Path)
    recover = subparsers.add_parser("recover-partial-incident")
    recover.add_argument(
        "--replicate", type=int, choices=(1, 2, 3, 4, 5), required=True
    )
    recover.add_argument("--epoch", type=int, choices=(1, 2), required=True)
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
        classify_api_failure(
            args.replicate,
            args.outage_classification_artifact,
        )
    elif args.command == "recover-partial-incident":
        recover_partial_incident(args.replicate, args.epoch)
    elif args.command == "prepare-variance":
        prepare_variance()
    else:
        evaluate()


if __name__ == "__main__":
    main()
