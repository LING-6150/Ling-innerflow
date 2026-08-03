#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.exclusions_v13 import build_official_exclusions


def _write_create_only(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as handle:
        handle.write(value)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Freeze exclusions derived from the immutable M0 v1.2 corpus."
    )
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--candidate-registry", type=Path, required=True)
    parser.add_argument("--freeze-manifest", type=Path, required=True)
    parser.add_argument("--forbidden-hashes-output", type=Path, required=True)
    parser.add_argument("--forbidden-fingerprints-output", type=Path, required=True)
    parser.add_argument("--manifest-output", type=Path, required=True)
    args = parser.parse_args()

    hashes, fingerprints, manifest = build_official_exclusions(
        args.fixture,
        args.candidate_registry,
        args.freeze_manifest,
    )
    _write_create_only(
        args.forbidden_hashes_output,
        "\n".join(sorted(hashes)) + "\n",
    )
    _write_create_only(
        args.forbidden_fingerprints_output,
        "\n".join(sorted(fingerprints)) + "\n",
    )
    _write_create_only(
        args.manifest_output,
        json.dumps(
            manifest.model_dump(mode="json"),
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
    )
    print(
        json.dumps(
            {
                "status": "frozen",
                "forbidden_candidate_hash_count": len(hashes),
                "forbidden_fingerprint_count": len(fingerprints),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
