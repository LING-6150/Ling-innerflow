from __future__ import annotations

import hashlib
import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from innerflow_v2.reliability.models import (
    FreezeManifest,
    load_candidate_registry,
    load_scenarios,
)

EXPECTED_CATEGORY_COUNTS = {
    "correction": 6,
    "supersession": 4,
    "context-exception": 6,
    "no-memory": 4,
    "deletion": 4,
}


def canonical_sha256(value: object) -> str:
    payload = json.dumps(
        value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def file_sha256(path: str | Path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def build_freeze_manifest(
    scenario_path: str | Path,
    candidate_path: str | Path,
    *,
    source_commit: str,
    generated_at: datetime | None = None,
) -> FreezeManifest:
    scenarios = load_scenarios(scenario_path)
    candidates = load_candidate_registry(candidate_path)
    category_counts = Counter(scenario.category for scenario in scenarios)
    split_counts = Counter(scenario.split for scenario in scenarios)
    if len(scenarios) != 24:
        raise ValueError(f"expected 24 scenarios, found {len(scenarios)}")
    if dict(category_counts) != EXPECTED_CATEGORY_COUNTS:
        raise ValueError(
            f"category distribution drift: {dict(category_counts)} != "
            f"{EXPECTED_CATEGORY_COUNTS}"
        )
    if split_counts != {"visible": 16, "holdout": 8}:
        raise ValueError(f"split distribution drift: {dict(split_counts)}")

    included = [candidate for candidate in candidates if candidate.status == "included"]
    if {candidate.case_id for candidate in included} != {
        scenario.case_id for scenario in scenarios
    }:
        raise ValueError("candidate registry included set does not match scenarios")

    scenario_hashes = {
        scenario.case_id: canonical_sha256(
            scenario.model_dump(mode="json", exclude_none=False)
        )
        for scenario in scenarios
    }
    candidate_hashes = {
        candidate.case_id: candidate.content_sha256
        for candidate in included
        if candidate.case_id
    }
    if candidate_hashes != scenario_hashes:
        raise ValueError("candidate registry scenario hashes do not match fixtures")

    deletion_derived_count = sum(
        1
        for scenario in scenarios
        if scenario.category == "deletion"
        and scenario.gold.deletion_target
        and {"summary", "wiki"} & set(scenario.gold.deletion_target.locations)
    )
    construction_pattern_count = sum(
        candidate.status == "included"
        and candidate.provenance_tier == "construction_pattern"
        for candidate in candidates
    )
    external_item_count = sum(
        candidate.status == "included"
        and candidate.provenance_tier == "external_item"
        for candidate in candidates
    )
    return FreezeManifest(
        protocol_version="v1.2",
        source_commit=source_commit,
        generated_at=generated_at or datetime.now(timezone.utc),
        fixture_sha256=file_sha256(scenario_path),
        candidate_registry_sha256=file_sha256(candidate_path),
        scenario_hashes=scenario_hashes,
        visible_ids=[
            scenario.case_id for scenario in scenarios if scenario.split == "visible"
        ],
        holdout_ids=[
            scenario.case_id for scenario in scenarios if scenario.split == "holdout"
        ],
        category_counts=dict(category_counts),
        deletion_derived_count=deletion_derived_count,
        construction_pattern_count=construction_pattern_count,
        external_item_count=external_item_count,
        reviewer="none",
        limitations=[
            "No inter-annotator agreement was measured.",
            "There is no true annotator independence.",
            "Sealing controls post-freeze leakage, not construct validity.",
            "All included M0 cases are construction-pattern or product-extension cases; "
            "none is represented as an item-level external benchmark adaptation.",
        ],
    )


def verify_freeze_manifest(
    manifest: FreezeManifest,
    scenario_path: str | Path,
    candidate_path: str | Path,
) -> None:
    current = build_freeze_manifest(
        scenario_path,
        candidate_path,
        source_commit=manifest.source_commit,
        generated_at=manifest.generated_at,
    )
    if current != manifest:
        raise ValueError("frozen M0 manifest does not match fixture corpus")

