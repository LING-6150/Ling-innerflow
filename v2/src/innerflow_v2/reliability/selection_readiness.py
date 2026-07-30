from __future__ import annotations

import hashlib
import json
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from innerflow_v2.reliability.beacon_v2 import (
    BeaconEvidenceBundle,
    VerifiedBeaconArtifactV13,
    verify_beacon_evidence,
)
from innerflow_v2.reliability.exclusions_v13 import build_official_exclusions
from innerflow_v2.reliability.protocol_v13 import (
    CandidatePoolPublicFreezeManifestV13,
    CandidateRegistryV13,
    FrozenExclusionManifest,
    SignedAuthoringInventory,
    load_authoring_inventory,
    load_candidate_pool,
    load_candidate_pool_public_freeze_manifest,
    load_candidate_registry,
    load_exclusion_manifest,
    pool_sha256,
    registry_sha256,
    validate_candidate_registry,
)

PUBLIC_FREEZE_MANIFEST = Path(
    "v2/eval/m0/manifests/M0_V13_CANDIDATE_POOL_PUBLIC_FREEZE_MANIFEST.json"
)
AUTHORING_INVENTORY = Path(
    "v2/eval/m0/manifests/M0_V13_SIGNED_AUTHORING_INVENTORY.json"
)
AUTHORED_CANDIDATES = Path(
    "v2/eval/m0/candidates/M0_V13_AUTHORED_CANDIDATES.json"
)
CONFORMANCE_AUDIT = Path(
    "v2/eval/m0/audits/M0_V13_AUTOMATED_CONFORMANCE_AUDIT.json"
)
CANDIDATE_REGISTRY = Path(
    "v2/eval/m0/registries/M0_V13_CANDIDATE_REGISTRY_DRAFT.json"
)
REVIEW_DISPOSITION = Path(
    "v2/eval/m0/reviews/M0_V13_CANDIDATE_POOL_FREEZE_REVIEW.md"
)
EXCLUSION_MANIFEST = Path(
    "v2/eval/m0/manifests/M0_V13_EXCLUSION_MANIFEST.json"
)
FORBIDDEN_HASHES = Path(
    "v2/eval/m0/exclusions/M0_V13_FORBIDDEN_V12_CANDIDATE_HASHES.txt"
)
FORBIDDEN_FINGERPRINTS = Path(
    "v2/eval/m0/exclusions/M0_V13_FORBIDDEN_V12_NORMALIZED_FINGERPRINTS.txt"
)
V12_FIXTURE = Path("v2/eval/m0/fixtures/memory_reliability_m0.json")
V12_CANDIDATE_REGISTRY = Path("v2/eval/m0/fixtures/candidate_registry.json")
V12_FREEZE_MANIFEST = Path("v2/eval/m0/manifests/M0_CORPUS_FREEZE.json")


@dataclass(frozen=True)
class OfficialSelectionInputs:
    public_freeze: CandidatePoolPublicFreezeManifestV13
    registry: CandidateRegistryV13
    inventory: SignedAuthoringInventory
    exclusion_manifest: FrozenExclusionManifest
    forbidden_hashes: set[str]
    forbidden_fingerprints: set[str]


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _hash_lines(path: Path) -> set[str]:
    values = {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }
    if any(len(value) != 64 for value in values):
        raise ValueError(f"{path}: exclusion ledger contains a non-SHA-256 value")
    return values


def load_official_selection_inputs(repo_root: str | Path) -> OfficialSelectionInputs:
    root = Path(repo_root).resolve()
    public_path = root / PUBLIC_FREEZE_MANIFEST
    public = load_candidate_pool_public_freeze_manifest(public_path)
    inventory_path = root / AUTHORING_INVENTORY
    candidates_path = root / AUTHORED_CANDIDATES
    audit_path = root / CONFORMANCE_AUDIT
    registry_path = root / CANDIDATE_REGISTRY
    review_path = root / REVIEW_DISPOSITION

    expected_file_hashes = {
        inventory_path: public.authoring_inventory_file_sha256,
        candidates_path: public.authored_candidates_file_sha256,
        audit_path: public.conformance_audit_file_sha256,
        registry_path: public.candidate_registry_file_sha256,
        review_path: public.review_disposition_file_sha256,
        root / public.superseded_unpublished_manifest_path:
            public.superseded_unpublished_manifest_file_sha256,
        root / public.publication_incident_path:
            public.publication_incident_file_sha256,
    }
    for path, expected in expected_file_hashes.items():
        if file_sha256(path) != expected:
            raise ValueError(f"public-freeze bound file hash drift: {path}")

    inventory = load_authoring_inventory(inventory_path)
    candidates = load_candidate_pool(candidates_path)
    registry = load_candidate_registry(registry_path)
    exclusion_manifest = load_exclusion_manifest(root / EXCLUSION_MANIFEST)
    forbidden_hashes = _hash_lines(root / FORBIDDEN_HASHES)
    forbidden_fingerprints = _hash_lines(root / FORBIDDEN_FINGERPRINTS)
    rebuilt_hashes, rebuilt_fingerprints, rebuilt_manifest = (
        build_official_exclusions(
            root / V12_FIXTURE,
            root / V12_CANDIDATE_REGISTRY,
            root / V12_FREEZE_MANIFEST,
        )
    )
    if (
        exclusion_manifest != rebuilt_manifest
        or forbidden_hashes != rebuilt_hashes
        or forbidden_fingerprints != rebuilt_fingerprints
    ):
        raise ValueError("official v1.2 exclusion artifacts drift")
    hashes = validate_candidate_registry(
        registry,
        authoring_inventory=inventory,
        authored_candidates=candidates,
        forbidden_candidate_hashes=forbidden_hashes,
        forbidden_overlap_fingerprints=forbidden_fingerprints,
    )
    if registry_sha256(registry) != public.candidate_registry_sha256:
        raise ValueError("public-freeze canonical registry hash drift")
    if pool_sha256(hashes) != public.eligible_pool_sha256:
        raise ValueError("public-freeze eligible pool hash drift")
    audit = json.loads(audit_path.read_text(encoding="utf-8"))
    if (
        audit.get("candidate_count") != public.authored_candidate_count
        or audit.get("signed_inventory_sha256") != inventory.sha256
        or len(audit.get("entries", [])) != public.authored_candidate_count
    ):
        raise ValueError("public-freeze conformance audit drift")
    return OfficialSelectionInputs(
        public_freeze=public,
        registry=registry,
        inventory=inventory,
        exclusion_manifest=exclusion_manifest,
        forbidden_hashes=forbidden_hashes,
        forbidden_fingerprints=forbidden_fingerprints,
    )


def load_verified_beacon_for_selection(
    evidence_path: str | Path,
    artifact_path: str | Path,
    public_freeze: CandidatePoolPublicFreezeManifestV13,
    *,
    certificate_fetch: Callable[[str], bytes],
) -> VerifiedBeaconArtifactV13:
    bundle = BeaconEvidenceBundle.model_validate_json(
        Path(evidence_path).read_text(encoding="utf-8")
    )
    claimed = VerifiedBeaconArtifactV13.model_validate_json(
        Path(artifact_path).read_text(encoding="utf-8")
    )
    verified = verify_beacon_evidence(
        bundle,
        public_freeze,
        certificate_fetch=certificate_fetch,
    )
    if claimed != verified:
        raise ValueError("verified Beacon artifact does not match raw evidence")
    return verified
