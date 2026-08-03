#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.beacon_v2 import (
    fetch_first_eligible_beacon_evidence,
    fetch_url,
    write_create_only,
)
from innerflow_v2.reliability.protocol_v13 import (
    load_authoring_inventory,
    load_candidate_registry,
    pool_sha256,
    public_selection_manifest,
    select_candidate_pool,
    selection_manifest_sha256,
    validate_candidate_registry,
)
from innerflow_v2.reliability.selection_readiness import (
    load_official_selection_inputs,
    load_verified_beacon_for_selection,
)


def _hash_lines(path: Path | None) -> set[str]:
    if path is None:
        return set()
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Validate or deterministically select an M0 v1.3 candidate pool."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate = subparsers.add_parser("validate-pool")
    validate.add_argument("--registry", type=Path, required=True)
    validate.add_argument("--authoring-inventory", type=Path, required=True)
    validate.add_argument("--forbidden-hashes", type=Path)
    validate.add_argument("--forbidden-fingerprints", type=Path)

    fetch_beacon = subparsers.add_parser("fetch-beacon")
    fetch_beacon.add_argument("--evidence-output", type=Path, required=True)
    fetch_beacon.add_argument("--verified-output", type=Path, required=True)

    select = subparsers.add_parser("select")
    select.add_argument("--beacon-evidence", type=Path, required=True)
    select.add_argument("--beacon-artifact", type=Path, required=True)
    select.add_argument("--output", type=Path, required=True)
    select.add_argument("--public-output", type=Path, required=True)

    args = parser.parse_args()
    if args.command == "fetch-beacon":
        official = load_official_selection_inputs(REPO_ROOT)
        evidence, verified = fetch_first_eligible_beacon_evidence(
            official.public_freeze,
            fetch=fetch_url,
        )
        write_create_only(args.evidence_output, evidence)
        write_create_only(args.verified_output, verified)
        print(
            json.dumps(
                {
                    "status": "verified",
                    "pulse_timestamp": verified.beacon.pulse_timestamp.isoformat(),
                    "signed_pulse_sha256": (
                        verified.beacon.signed_pulse_sha256
                    ),
                },
                indent=2,
            )
        )
        return

    if args.command == "select":
        official = load_official_selection_inputs(REPO_ROOT)
        verified = load_verified_beacon_for_selection(
            args.beacon_evidence,
            args.beacon_artifact,
            official.public_freeze,
            certificate_fetch=fetch_url,
        )
        manifest = select_candidate_pool(
            official.registry,
            authoring_inventory=official.inventory,
            exclusion_manifest=official.exclusion_manifest,
            forbidden_candidate_hashes=official.forbidden_hashes,
            forbidden_overlap_fingerprints=official.forbidden_fingerprints,
            pool_frozen_at=official.public_freeze.public_freeze_effective_at,
            beacon=verified.beacon,
        )
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("x", encoding="utf-8") as stream:
            stream.write(
                json.dumps(
                    manifest.model_dump(mode="json"),
                    ensure_ascii=False,
                    indent=2,
                )
                + "\n"
            )
        args.public_output.parent.mkdir(parents=True, exist_ok=True)
        with args.public_output.open("x", encoding="utf-8") as stream:
            stream.write(
                json.dumps(
                    public_selection_manifest(manifest),
                    ensure_ascii=False,
                    indent=2,
                )
                + "\n"
            )
        print(
            json.dumps(
                {
                    "status": "selected",
                    "selection_sha256": selection_manifest_sha256(manifest),
                    "selected": len(manifest.selected),
                    "visible": sum(
                        entry.split == "visible" for entry in manifest.selected
                    ),
                    "holdout": sum(
                        entry.split == "holdout" for entry in manifest.selected
                    ),
                },
                indent=2,
            )
        )
        return

    registry = load_candidate_registry(args.registry)
    authoring_inventory = load_authoring_inventory(args.authoring_inventory)
    candidates = registry.candidates
    hashes = validate_candidate_registry(
        registry,
        authoring_inventory=authoring_inventory,
        forbidden_candidate_hashes=_hash_lines(args.forbidden_hashes),
        forbidden_overlap_fingerprints=_hash_lines(
            args.forbidden_fingerprints
        ),
    )
    print(
        json.dumps(
            {
                "status": "valid",
                "eligible_candidates": len(candidates),
                "pool_sha256": pool_sha256(hashes),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
