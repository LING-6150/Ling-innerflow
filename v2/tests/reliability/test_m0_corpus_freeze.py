from __future__ import annotations

from collections import Counter
from pathlib import Path

from innerflow_v2.reliability.freeze import verify_freeze_manifest
from innerflow_v2.reliability.models import (
    FreezeManifest,
    load_candidate_registry,
    load_scenarios,
)

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "eval" / "m0" / "fixtures"
MANIFEST_PATH = ROOT / "eval" / "m0" / "manifests" / "M0_CORPUS_FREEZE.json"


def _scenarios():
    return load_scenarios(FIXTURES / "memory_reliability_m0.json")


def test_frozen_distribution_and_answer_position_balance():
    scenarios = _scenarios()
    assert Counter(s.category for s in scenarios) == {
        "correction": 6,
        "supersession": 4,
        "context-exception": 6,
        "no-memory": 4,
        "deletion": 4,
    }
    assert Counter(s.split for s in scenarios) == {"visible": 16, "holdout": 8}
    assert Counter(s.rubric.expected_choice for s in scenarios) == {
        "A": 8,
        "B": 8,
        "C": 8,
    }


def test_candidate_registry_is_complete_and_honest_about_provenance():
    scenarios = _scenarios()
    records = load_candidate_registry(FIXTURES / "candidate_registry.json")
    included = [record for record in records if record.status == "included"]
    rejected = [record for record in records if record.status == "rejected"]
    assert len(included) == 24
    assert len(rejected) == 4
    assert {record.case_id for record in included} == {s.case_id for s in scenarios}
    assert not [r for r in included if r.provenance_tier == "external_item"]
    assert sum(r.provenance_tier == "construction_pattern" for r in included) >= 12


def test_derived_deletion_and_holdout_contracts_are_frozen():
    scenarios = _scenarios()
    deletion = [s for s in scenarios if s.category == "deletion"]
    assert len(deletion) == 4
    assert any(
        {"summary", "wiki"} & set(s.gold.deletion_target.locations)
        for s in deletion
        if s.gold.deletion_target
    )
    for scenario in deletion:
        target = scenario.gold.deletion_target
        assert target is not None
        assert target.normalized_target
        assert target.forbidden_variants
    assert len([s for s in scenarios if s.split == "holdout"]) == 8
    assert all(s.provenance.reviewer == "none" for s in scenarios)


def test_committed_manifest_matches_every_fixture_byte_and_scenario():
    manifest = FreezeManifest.model_validate_json(
        MANIFEST_PATH.read_text(encoding="utf-8")
    )
    verify_freeze_manifest(
        manifest,
        FIXTURES / "memory_reliability_m0.json",
        FIXTURES / "candidate_registry.json",
    )

