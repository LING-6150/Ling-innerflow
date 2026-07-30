import hashlib
import json
from datetime import timedelta
from pathlib import Path

from innerflow_v2.reliability.protocol_v13 import (
    load_authoring_inventory,
    load_candidate_pool,
    load_candidate_pool_freeze_manifest,
    load_candidate_pool_public_freeze_manifest,
    load_candidate_registry,
    pool_sha256,
    registry_sha256,
    validate_candidate_registry,
)


REPO_ROOT = Path(__file__).resolve().parents[3]
V2_ROOT = REPO_ROOT / "v2"
MANIFEST_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "manifests"
    / "M0_V13_CANDIDATE_POOL_FREEZE_MANIFEST.json"
)
PUBLIC_MANIFEST_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "manifests"
    / "M0_V13_CANDIDATE_POOL_PUBLIC_FREEZE_MANIFEST.json"
)


def _file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _bound_path(relative_path: str) -> Path:
    path = (REPO_ROOT / relative_path).resolve()
    path.relative_to(REPO_ROOT.resolve())
    return path


def test_candidate_pool_freeze_binds_every_reviewed_artifact() -> None:
    manifest = load_candidate_pool_freeze_manifest(MANIFEST_PATH)
    inventory_path = _bound_path(manifest.authoring_inventory_path)
    candidates_path = _bound_path(manifest.authored_candidates_path)
    audit_path = _bound_path(manifest.conformance_audit_path)
    registry_path = _bound_path(manifest.candidate_registry_path)
    review_path = _bound_path(manifest.review_disposition_path)

    inventory = load_authoring_inventory(inventory_path)
    candidates = load_candidate_pool(candidates_path)
    registry = load_candidate_registry(registry_path)
    hashes = validate_candidate_registry(
        registry,
        authoring_inventory=inventory,
        authored_candidates=candidates,
    )
    audit = json.loads(audit_path.read_text(encoding="utf-8"))

    assert manifest.source_commit == "dbe1a761d8b06b2e7cd73fd7e16badbd387e4e64"
    assert manifest.authoring_inventory_file_sha256 == _file_sha256(inventory_path)
    assert manifest.authoring_inventory_manifest_sha256 == inventory.sha256
    assert manifest.authored_candidates_file_sha256 == _file_sha256(candidates_path)
    assert manifest.conformance_audit_file_sha256 == _file_sha256(audit_path)
    assert manifest.candidate_registry_file_sha256 == _file_sha256(registry_path)
    assert manifest.candidate_registry_sha256 == registry_sha256(registry)
    assert manifest.review_disposition_file_sha256 == _file_sha256(review_path)
    assert manifest.eligible_pool_sha256 == pool_sha256(hashes)
    assert audit["signed_inventory_sha256"] == inventory.sha256
    assert len(audit["entries"]) == manifest.authored_candidate_count == 48
    assert len(hashes) == manifest.eligible_candidate_count == 36
    assert (
        sum(record.status == "reserve" for record in registry.records)
        == manifest.reserve_candidate_count
        == 12
    )


def test_candidate_pool_freeze_starts_exact_24_hour_future_seed_wait() -> None:
    manifest = load_candidate_pool_freeze_manifest(MANIFEST_PATH)

    assert manifest.status == "WAITING_FUTURE_SEED"
    assert manifest.future_seed_not_before == manifest.frozen_at + timedelta(hours=24)
    assert manifest.signed_by == ["LING-6150", "independent-agent-reviewer"]


def test_frozen_artifact_mutation_cannot_preserve_manifest_identity(tmp_path) -> None:
    manifest = load_candidate_pool_freeze_manifest(MANIFEST_PATH)
    source = _bound_path(manifest.authored_candidates_path)
    changed = tmp_path / source.name
    changed.write_bytes(source.read_bytes() + b"\n")

    assert _file_sha256(changed) != manifest.authored_candidates_file_sha256


def test_public_freeze_preserves_reviewed_pool_and_records_incident() -> None:
    original = load_candidate_pool_freeze_manifest(MANIFEST_PATH)
    public = load_candidate_pool_public_freeze_manifest(PUBLIC_MANIFEST_PATH)
    incident_path = _bound_path(public.publication_incident_path)

    assert public.superseded_unpublished_manifest_file_sha256 == _file_sha256(
        MANIFEST_PATH
    )
    assert public.publication_incident_file_sha256 == _file_sha256(incident_path)
    assert public.source_commit == original.source_commit
    assert (
        public.authoring_inventory_file_sha256
        == original.authoring_inventory_file_sha256
    )
    assert (
        public.authored_candidates_file_sha256
        == original.authored_candidates_file_sha256
    )
    assert (
        public.conformance_audit_file_sha256
        == original.conformance_audit_file_sha256
    )
    assert (
        public.candidate_registry_file_sha256
        == original.candidate_registry_file_sha256
    )
    assert public.candidate_registry_sha256 == original.candidate_registry_sha256
    assert public.eligible_pool_sha256 == original.eligible_pool_sha256


def test_public_freeze_restarts_the_full_24_hour_wait() -> None:
    public = load_candidate_pool_public_freeze_manifest(PUBLIC_MANIFEST_PATH)

    assert public.status == "PUBLISHED_WAITING_FUTURE_SEED"
    assert (
        public.future_seed_not_before
        == public.public_freeze_effective_at + timedelta(hours=24)
    )
    assert public.public_freeze_effective_at > load_candidate_pool_freeze_manifest(
        MANIFEST_PATH
    ).future_seed_not_before
