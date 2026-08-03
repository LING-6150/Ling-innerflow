from __future__ import annotations

import hashlib
import re
import unicodedata
from pathlib import Path

from innerflow_v2.reliability.freeze import file_sha256, verify_freeze_manifest
from innerflow_v2.reliability.models import (
    FreezeManifest,
    ReliabilityScenario,
    load_scenarios,
)
from innerflow_v2.reliability.protocol_v13 import (
    CandidateV13,
    FrozenExclusionManifest,
    exclusion_list_sha256,
)

FINGERPRINT_ALGORITHM = "sha256-nfkc-casefold-whitespace-v1"
MIN_NORMALIZED_TEXT_LENGTH = 20


def normalized_text_fingerprint(value: str) -> str | None:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    normalized = re.sub(r"\s+", " ", normalized).strip()
    if len(normalized) < MIN_NORMALIZED_TEXT_LENGTH:
        return None
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _fingerprint_texts(values: list[str]) -> set[str]:
    return {
        fingerprint
        for value in values
        if (fingerprint := normalized_text_fingerprint(value)) is not None
    }


def v12_scenario_overlap_fingerprints(
    scenario: ReliabilityScenario,
) -> set[str]:
    values = [
        event.content
        for session in scenario.setup_sessions
        for event in session.events
        if event.role == "user"
    ]
    values.extend(
        [
            scenario.probe.text,
            *(option.text for option in scenario.probe.options),
        ]
    )
    if scenario.gold.deletion_target is not None:
        values.extend(
            [
                scenario.gold.deletion_target.normalized_target,
                *scenario.gold.deletion_target.forbidden_variants,
            ]
        )
    return _fingerprint_texts(values)


def candidate_normalized_overlap_fingerprints(
    candidate: CandidateV13,
) -> set[str]:
    values: list[str] = []
    for world in candidate.worlds:
        values.append(world.probe)
        values.extend(world.non_memory_state.values())
        values.extend(
            event.content
            for event in world.setup_memory_events
            if event.role == "user"
        )
        if world.effective_claim is not None:
            values.append(world.effective_claim)
    for claim in candidate.tracked_claims:
        values.extend([claim.canonical_value, *claim.surface_forms])
    return _fingerprint_texts(values)


def build_official_exclusions(
    fixture_path: str | Path,
    candidate_registry_path: str | Path,
    freeze_manifest_path: str | Path,
) -> tuple[set[str], set[str], FrozenExclusionManifest]:
    fixture = Path(fixture_path)
    candidate_registry = Path(candidate_registry_path)
    freeze_path = Path(freeze_manifest_path)
    freeze = FreezeManifest.model_validate_json(freeze_path.read_text(encoding="utf-8"))
    verify_freeze_manifest(freeze, fixture, candidate_registry)
    scenarios = load_scenarios(fixture)
    forbidden_hashes = set(freeze.scenario_hashes.values())
    forbidden_fingerprints = set().union(
        *(v12_scenario_overlap_fingerprints(scenario) for scenario in scenarios)
    )
    if len(forbidden_hashes) != len(scenarios):
        raise ValueError("v1.2 scenario hashes are not unique")
    if not forbidden_fingerprints:
        raise ValueError("v1.2 normalized overlap ledger is empty")
    manifest = FrozenExclusionManifest(
        source_fixture_sha256=file_sha256(fixture),
        source_candidate_registry_sha256=file_sha256(candidate_registry),
        source_freeze_manifest_sha256=file_sha256(freeze_path),
        forbidden_candidate_hashes_sha256=exclusion_list_sha256(
            forbidden_hashes
        ),
        forbidden_candidate_hash_count=len(forbidden_hashes),
        forbidden_fingerprints_sha256=exclusion_list_sha256(
            forbidden_fingerprints
        ),
        forbidden_fingerprint_count=len(forbidden_fingerprints),
    )
    return forbidden_hashes, forbidden_fingerprints, manifest
