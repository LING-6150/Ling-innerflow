from __future__ import annotations

import base64
import json
from datetime import date
from pathlib import Path

import pytest

from innerflow_v2.reliability.beacon_v2 import BeaconEvidenceBundle
from innerflow_v2.reliability.execution_freeze_v13 import (
    build_execution_freeze,
    file_sha256,
    persist_execution_freeze,
)
from innerflow_v2.reliability.execution_v13 import load_frozen_action_executor
from innerflow_v2.reliability.protocol_v13 import (
    public_selection_manifest,
    select_candidate_pool,
)
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
    assert all(len(value["cells"]) == 24 * 3 * 2 for value in request_plan["replicates"])
    assert len(public_freeze["request_order_sha256_by_replicate"]) == 3

    public_text = json.dumps(public_freeze, sort_keys=True)
    holdout_ids = {
        entry.candidate_id for entry in selection.selected if entry.split == "holdout"
    }
    assert all(candidate_id not in public_text for candidate_id in holdout_ids)
    assert public_freeze["holdout"]["count"] == 8


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
