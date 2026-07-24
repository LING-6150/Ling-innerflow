"""Write or verify the frozen M0 corpus hash manifest."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.freeze import (  # noqa: E402
    build_freeze_manifest,
    verify_freeze_manifest,
)
from innerflow_v2.reliability.models import FreezeManifest  # noqa: E402

SCENARIOS = ROOT / "eval" / "m0" / "fixtures" / "memory_reliability_m0.json"
CANDIDATES = ROOT / "eval" / "m0" / "fixtures" / "candidate_registry.json"
MANIFEST = ROOT / "eval" / "m0" / "manifests" / "M0_CORPUS_FREEZE.json"
SOURCE_COMMIT = "d202d10648947d35ef205b79102f44f62c217858"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    if args.write:
        manifest = build_freeze_manifest(
            SCENARIOS, CANDIDATES, source_commit=SOURCE_COMMIT
        )
        MANIFEST.write_text(
            json.dumps(manifest.model_dump(mode="json"), indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {MANIFEST}")
        return

    manifest = FreezeManifest.model_validate_json(MANIFEST.read_text(encoding="utf-8"))
    verify_freeze_manifest(manifest, SCENARIOS, CANDIDATES)
    print(
        f"verified {len(manifest.scenario_hashes)} scenarios "
        f"({len(manifest.visible_ids)} visible, {len(manifest.holdout_ids)} holdout)"
    )


if __name__ == "__main__":
    main()

