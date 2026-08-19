from __future__ import annotations

import base64
import json
import subprocess
import sys
from datetime import date
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from innerflow_v2.reliability.beacon_v2 import BeaconEvidenceBundle
from innerflow_v2.reliability.execution_freeze_v13 import (
    IMPLEMENTATION_FILES,
    build_execution_freeze,
    file_sha256,
    persist_execution_freeze,
)
from scripts.run_m0_v13_reliability import (
    _execution_epoch,
    _incident_state,
    _verify_source_boundary,
)
from innerflow_v2.reliability.execution_v13 import (
    FrozenRunManifestV13,
    IncidentDisposition,
    RunIncident,
    initialize_run_incident_history,
    load_frozen_action_executor,
    resolve_run_incident,
)
from innerflow_v2.reliability.protocol_v13 import (
    canonical_sha256,
    public_selection_manifest,
    select_candidate_pool,
)
from innerflow_v2.reliability.runner_v13 import execution_attempt_id
from innerflow_v2.reliability.selection_readiness import (
    load_official_selection_inputs,
    load_verified_beacon_for_selection,
)


REPO_ROOT = Path(__file__).resolve().parents[3]
V2_ROOT = REPO_ROOT / "v2"
EVIDENCE = V2_ROOT / "eval/m0/beacon/M0_V13_BEACON_EVIDENCE.json"
VERIFIED = V2_ROOT / "eval/m0/beacon/M0_V13_VERIFIED_BEACON.json"
EXECUTOR = V2_ROOT / "eval/m0/manifests/M0_V13_ACTION_EXECUTOR.json"
MODEL_METADATA = V2_ROOT / "eval/m0/manifests/M0_V13_MODEL_METADATA.json"
RUN_MANIFEST = V2_ROOT / "eval/m0/manifests/M0_V13_RUN_MANIFEST.json"
PUBLIC_EXECUTION_FREEZE = (
    V2_ROOT / "eval/m0/manifests/M0_V13_EXECUTION_FREEZE_PUBLIC.json"
)
SEALED_ROOT_IDENTITY = Path(
    "/Users/lingduan/Desktop/Ling-innerflow/.sealed/execution"
)


def _official_selection():
    official = load_official_selection_inputs(REPO_ROOT)
    bundle = BeaconEvidenceBundle.model_validate_json(
        EVIDENCE.read_text(encoding="utf-8")
    )
    certificates = {
        certificate_id: base64.b64decode(value)
        for certificate_id, value in bundle.certificates_pem_base64.items()
    }
    verified = load_verified_beacon_for_selection(
        EVIDENCE,
        VERIFIED,
        official.public_freeze,
        certificate_fetch=lambda url: certificates[url.rsplit("/", 1)[-1]],
    )
    selection = select_candidate_pool(
        official.registry,
        authoring_inventory=official.inventory,
        exclusion_manifest=official.exclusion_manifest,
        forbidden_candidate_hashes=official.forbidden_hashes,
        forbidden_overlap_fingerprints=official.forbidden_fingerprints,
        pool_frozen_at=official.public_freeze.public_freeze_effective_at,
        beacon=verified.beacon,
    )
    return official, selection


def _build(tmp_path):
    official, selection = _official_selection()
    executor = load_frozen_action_executor(EXECUTOR)
    artifacts = build_execution_freeze(
        repo_root=REPO_ROOT,
        source_commit="1" * 40,
        selection=selection,
        public_selection=public_selection_manifest(selection),
        official=official,
        executor=executor,
        sealed_root=tmp_path / "sealed",
        run_date=date(2026, 8, 3),
        provider_model_metadata_sha256=file_sha256(MODEL_METADATA),
    )
    return selection, artifacts


def test_execution_freeze_binds_complete_orders_without_holdout_disclosure(
    tmp_path,
) -> None:
    selection, (_, run_manifest, request_plan, public_freeze) = _build(tmp_path)

    assert run_manifest.initial_replicates == 3
    assert run_manifest.formation_temperature == 0.2
    assert run_manifest.response_temperature == 0.4
    assert run_manifest.model_id == "gemini-2.5-flash"
    assert run_manifest.embedding_model_id == "text-embedding-3-large"
    assert len(request_plan["replicates"]) == 3
    assert all(
        len(value["cells"]) == 24 * 3 * 2
        for value in request_plan["replicates"]
    )
    assert len(public_freeze["request_order_sha256_by_replicate"]) == 3

    public_text = json.dumps(public_freeze, sort_keys=True)
    holdout_ids = {
        entry.candidate_id for entry in selection.selected if entry.split == "holdout"
    }
    assert all(candidate_id not in public_text for candidate_id in holdout_ids)
    assert public_freeze["holdout"]["count"] == 8
    assert "v2/src/innerflow_v2/reliability/gate.py" in (
        run_manifest.implementation_source_hashes
    )


def test_source_boundary_allows_artifact_commit_but_rejects_gate_drift(
    tmp_path,
) -> None:
    repo = tmp_path / "repo"
    gate = repo / "v2/src/innerflow_v2/reliability/gate.py"
    gate.parent.mkdir(parents=True)
    gate.write_text("FROZEN = True\n", encoding="utf-8")
    subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
    subprocess.run(
        ["git", "config", "user.email", "test@example.com"],
        cwd=repo,
        check=True,
    )
    subprocess.run(
        ["git", "config", "user.name", "Test"], cwd=repo, check=True
    )
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(
        ["git", "commit", "-q", "-m", "freeze source"],
        cwd=repo,
        check=True,
    )
    source_commit = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=repo,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    artifact = repo / "artifact.json"
    artifact.write_text("{}\n", encoding="utf-8")
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(
        ["git", "commit", "-q", "-m", "add artifact"],
        cwd=repo,
        check=True,
    )

    _verify_source_boundary(source_commit, repo_root=repo)

    gate.write_text("FROZEN = False\n", encoding="utf-8")
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    subprocess.run(
        ["git", "commit", "-q", "-m", "drift gate"],
        cwd=repo,
        check=True,
    )
    with pytest.raises(ValueError, match="runtime-critical source drifted"):
        _verify_source_boundary(source_commit, repo_root=repo)


def test_failed_attempt_blocks_rerun_until_single_evidenced_replay(
    tmp_path,
    monkeypatch,
) -> None:
    _, (_, run_manifest, _, _) = _build(tmp_path)
    sealed = tmp_path / "sealed"
    sealed.mkdir(exist_ok=True)
    initialize_run_incident_history(run_manifest)
    monkeypatch.setenv("M0_V13_SEALED_ROOT", str(sealed))
    attempt_id = execution_attempt_id(run_manifest, replicate=1, epoch=1)
    partial_path = sealed / "M0_V13_PARTIAL_R1_E1.json"
    partial_path.write_text('{"sealed":"partial"}\n', encoding="utf-8")
    resolve_run_incident(
        run_manifest,
        RunIncident.PARTIAL_REPLICATE_INTERRUPTION,
        current_run_manifest_sha256=run_manifest.sha256,
        evidence_sha256=file_sha256(partial_path),
        execution_attempt_id=attempt_id,
    )

    with pytest.raises(ValueError, match="awaits API incident classification"):
        _incident_state(run_manifest)
    assert (
        resolve_run_incident(
            run_manifest,
            RunIncident.API_FAILURE,
            current_run_manifest_sha256=run_manifest.sha256,
            external_outage_evidence=True,
            evidence_sha256=canonical_sha256("external outage evidence"),
            execution_attempt_id=attempt_id,
        )
        == IncidentDisposition.REPLAY_IDENTICAL_MANIFEST
    )
    _incident_state(run_manifest)
    assert _execution_epoch(run_manifest, 1) == 2


def test_execution_freeze_artifacts_and_histories_are_create_only(tmp_path) -> None:
    _, (signed, run_manifest, request_plan, public_freeze) = _build(tmp_path)
    sealed = tmp_path / "sealed"
    run_path = tmp_path / "public/run.json"
    freeze_path = tmp_path / "public/freeze.json"
    persist_execution_freeze(
        signed=signed,
        run_manifest=run_manifest,
        sealed_request_plan=request_plan,
        public_freeze=public_freeze,
        sealed_root=sealed,
        public_run_manifest_path=run_path,
        public_freeze_path=freeze_path,
    )

    assert run_path.exists()
    assert freeze_path.exists()
    assert Path(signed.invalidation_history_path).exists()
    assert Path(run_manifest.incident_history_path).exists()
    with pytest.raises(FileExistsError):
        persist_execution_freeze(
            signed=signed,
            run_manifest=run_manifest,
            sealed_request_plan=request_plan,
            public_freeze=public_freeze,
            sealed_root=sealed,
            public_run_manifest_path=run_path,
            public_freeze_path=freeze_path,
        )


def test_committed_execution_freeze_is_reproducible_without_model_calls() -> None:
    official, selection = _official_selection()
    committed_run = FrozenRunManifestV13.model_validate_json(
        RUN_MANIFEST.read_text(encoding="utf-8")
    )
    committed_public = json.loads(
        PUBLIC_EXECUTION_FREEZE.read_text(encoding="utf-8")
    )
    metadata = json.loads(MODEL_METADATA.read_text(encoding="utf-8"))
    _, reproduced_run, _, reproduced_public = build_execution_freeze(
        repo_root=REPO_ROOT,
        source_commit=committed_run.source_commit,
        selection=selection,
        public_selection=public_selection_manifest(selection),
        official=official,
        executor=load_frozen_action_executor(EXECUTOR),
        sealed_root=SEALED_ROOT_IDENTITY,
        run_date=date.fromisoformat(metadata["run_date"]),
        provider_model_metadata_sha256=file_sha256(MODEL_METADATA),
    )

    assert committed_run.source_commit == (
        "c531ce40394dc29dc95952ce790d09e20aeb521e"
    )
    assert committed_run == reproduced_run
    assert committed_public == reproduced_public
