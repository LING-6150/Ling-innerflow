"""Precision guards: gold-only metrics don't penalize over-emission, so these
catch a system that pads claims / hallucinates conflicts (a real risk once an
LLM is wired in PR-2). Deterministic PR-1 systems must read 0 here."""
from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval.memory_eval import (
    GoldConflict,
    GoldCurrentFact,
    extra_claim_counts,
    false_conflict_counts,
    load_memory_eval_cases,
)
from innerflow_v2.eval.run_memory_eval import run_memory_eval
from innerflow_v2.memory.models import MemoryConflict
from innerflow_v2.memory.systems import ALL_SYSTEMS
from innerflow_v2.memory.types import ProfileClaim

FIXTURES = Path(__file__).resolve().parents[1] / "eval" / "fixtures"
CASES = load_memory_eval_cases(FIXTURES / "memory_eval_dev.jsonl") + load_memory_eval_cases(
    FIXTURES / "memory_eval_locked.jsonl"
)


def _claim(key):
    return ProfileClaim(semantic_key=key, content="x", source_observation_ids=["o1"], kind="fact")


def test_extra_claim_counts_flags_ungrounded_keys():
    gold = [GoldCurrentFact(semantic_key="k1", effective_observation_ids=["o1"])]
    extra, total = extra_claim_counts([_claim("k1"), _claim("k2")], gold)
    assert (extra, total) == (1, 2)  # k2 has no gold fact


def test_false_conflict_counts_flags_hallucinated_pairs():
    gold = [GoldConflict(semantic_key="k", old_observation_id="o1", new_observation_id="o2", expected_resolution="supersede")]
    cons = [
        MemoryConflict(old_observation_id="o1", new_observation_id="o2", conflict_type="supersession", resolution="supersede", rationale=""),
        MemoryConflict(old_observation_id="o3", new_observation_id="o4", conflict_type="contradiction", resolution="keep_both", rationale=""),
    ]
    false, total = false_conflict_counts(cons, gold)
    assert (false, total) == (1, 2)  # (o3,o4) is not a gold conflict


def test_deterministic_systems_do_not_over_emit():
    reports = run_memory_eval(CASES, ALL_SYSTEMS, k=3)
    for r in reports.values():
        assert r.extra_claim_rate == 0.0, f"{r.name} emitted ungrounded claims"
        assert r.false_conflict_count == 0, f"{r.name} hallucinated conflicts"
