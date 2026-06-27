"""Pin the Stage 2 metric alignment rules (the spot the reviewer flagged)."""
from __future__ import annotations

import pytest
from pydantic import ValidationError

from innerflow_v2.eval.memory_eval import (
    GoldConflict,
    GoldCurrentFact,
    MemoryEvalCase,
    conflict_resolution_counts,
    contradiction_counts,
    coverage_counts,
    recall_at_k,
)
from innerflow_v2.memory.models import MemoryConflict
from innerflow_v2.memory.types import ProfileClaim


def _claim(key, sources):
    return ProfileClaim(semantic_key=key, content="x", source_observation_ids=sources, kind="fact")


def test_contradiction_anchored_on_semantic_key():
    gold = [GoldCurrentFact(semantic_key="k", effective_observation_ids=["o2"])]
    assert contradiction_counts([_claim("k", ["o2"])], gold) == (0, 1)
    # keeping a stale obs as current is a contradiction
    assert contradiction_counts([_claim("k", ["o1", "o2"])], gold) == (1, 1)


def test_empty_profile_cannot_game_contradiction():
    gold = [GoldCurrentFact(semantic_key="k", effective_observation_ids=["o2"])]
    # missing claim -> contradiction, denominator stays 1 (no shrinking to game)
    assert contradiction_counts([], gold) == (1, 1)
    assert coverage_counts([], gold) == (0, 1)


def test_conflict_resolution_matched_by_id_pair():
    gold = [GoldConflict(semantic_key="k", old_observation_id="o1", new_observation_id="o2", expected_resolution="keep_both")]
    good = [MemoryConflict(old_observation_id="o1", new_observation_id="o2", conflict_type="contradiction", resolution="keep_both", rationale="")]
    bad = [MemoryConflict(old_observation_id="o1", new_observation_id="o2", conflict_type="supersession", resolution="supersede", rationale="")]
    assert conflict_resolution_counts(good, gold) == (1, 1)
    assert conflict_resolution_counts(bad, gold) == (0, 1)
    assert conflict_resolution_counts([], gold) == (0, 1)


def test_recall_at_k():
    assert recall_at_k(["o1", "o2"], ["o2"], 3) == 1.0
    assert recall_at_k(["o2"], ["o1"], 3) == 0.0
    assert recall_at_k([], ["o1"], 3) == 0.0


def test_unknown_semantic_key_rejected():
    with pytest.raises(ValidationError):
        MemoryEvalCase(
            case_id="bad", category="x", split="dev",
            observations=[{"id": "o1", "session_id": "s1", "order": 1, "semantic_key": "not.a.key", "kind": "fact", "content": "x"}],
            gold_current_facts=[], gold_conflicts=[], retrieval_queries=[],
        )
