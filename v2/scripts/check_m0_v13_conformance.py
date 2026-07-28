#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.protocol_v13 import (
    load_beacon_pulse,
    load_authoring_inventory,
    load_candidate_registry,
    load_exclusion_manifest,
    pool_sha256,
    public_selection_manifest,
    select_candidate_pool,
    selection_manifest_sha256,
    validate_candidate_registry,
)


def _hash_lines(path: Path | None) -> set[str]:
    if path is None:
        return set()
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    }


def _timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise argparse.ArgumentTypeError("timestamp must include a timezone")
    return parsed


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

    select = subparsers.add_parser("select")
    select.add_argument("--registry", type=Path, required=True)
    select.add_argument("--authoring-inventory", type=Path, required=True)
    select.add_argument("--pool-frozen-at", type=_timestamp, required=True)
    select.add_argument("--beacon", type=Path, required=True)
    select.add_argument("--output", type=Path, required=True)
    select.add_argument("--public-output", type=Path, required=True)
    select.add_argument("--forbidden-hashes", type=Path, required=True)
    select.add_argument("--forbidden-fingerprints", type=Path, required=True)
    select.add_argument("--exclusion-manifest", type=Path, required=True)

    args = parser.parse_args()
    registry = load_candidate_registry(args.registry)
    authoring_inventory = load_authoring_inventory(args.authoring_inventory)
    candidates = registry.candidates
    if args.command == "validate-pool":
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
        return

    forbidden_hashes = _hash_lines(args.forbidden_hashes)
    forbidden_fingerprints = _hash_lines(args.forbidden_fingerprints)
    validate_candidate_registry(
        registry,
        authoring_inventory=authoring_inventory,
        forbidden_candidate_hashes=forbidden_hashes,
        forbidden_overlap_fingerprints=forbidden_fingerprints,
    )
    beacon = load_beacon_pulse(args.beacon)
    exclusion_manifest = load_exclusion_manifest(args.exclusion_manifest)
    manifest = select_candidate_pool(
        registry,
        authoring_inventory=authoring_inventory,
        exclusion_manifest=exclusion_manifest,
        forbidden_candidate_hashes=forbidden_hashes,
        forbidden_overlap_fingerprints=forbidden_fingerprints,
        pool_frozen_at=args.pool_frozen_at,
        beacon=beacon,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(manifest.model_dump(mode="json"), ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )
    args.public_output.parent.mkdir(parents=True, exist_ok=True)
    args.public_output.write_text(
        json.dumps(public_selection_manifest(manifest), ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
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


if __name__ == "__main__":
    main()
