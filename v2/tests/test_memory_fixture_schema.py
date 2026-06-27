from __future__ import annotations

from pathlib import Path

import pytest
from pydantic import ValidationError

from innerflow_v2.eval import (
    SafetyCase,
    load_memory_cases,
    load_safety_cases,
)

FIXTURES = Path(__file__).resolve().parents[1] / "eval" / "fixtures"


def test_memory_cases_load_and_validate():
    cases = load_memory_cases(FIXTURES / "memory_conflict_cases.jsonl")
    assert len(cases) >= 5
    for c in cases:
        assert c.sessions, f"{c.case_id} has no sessions"
        assert c.gold_observations, f"{c.case_id} has no gold_observations"
        assert c.gold_current_facts, f"{c.case_id} has no gold_current_facts"
        assert c.gold_retrieval_queries, f"{c.case_id} has no retrieval queries"


def test_retrieval_gold_is_anchored_to_declared_observations():
    # Loading succeeds only because every relevant_memory_id is declared; this
    # stops Stage 2 from "passing" by inventing or drifting ids.
    cases = load_memory_cases(FIXTURES / "memory_conflict_cases.jsonl")
    for c in cases:
        declared = {o.id for o in c.gold_observations}
        for q in c.gold_retrieval_queries:
            assert set(q.relevant_memory_ids) <= declared, f"{c.case_id} unanchored ids"


def test_unanchored_retrieval_id_is_rejected():
    from innerflow_v2.eval.fixtures import MemoryConflictCase

    with pytest.raises(ValidationError):
        MemoryConflictCase(
            case_id="bad",
            sessions=[{"session_id": "s1", "messages": ["hi"]}],
            gold_observations=[{"id": "obs_real", "kind": "fact", "content": "x", "session_id": "s1"}],
            gold_current_facts=[],
            gold_conflicts=[],
            gold_retrieval_queries=[{"query": "q", "relevant_memory_ids": ["obs_ghost"]}],
        )


def test_memory_cases_cover_required_scenarios():
    ids = {c.case_id for c in load_memory_cases(FIXTURES / "memory_conflict_cases.jsonl")}
    for needle in ("contradiction", "correction", "stale", "context_specific", "recurring_trigger"):
        assert any(needle in cid for cid in ids), f"missing scenario: {needle}"


def test_safety_cases_load_and_validate():
    cases = load_safety_cases(FIXTURES / "safety_red_team_cases.jsonl")
    assert len(cases) >= 10
    crisis = [c for c in cases if c.expected_level == "crisis"]
    benign = [c for c in cases if c.expected_level == "normal"]
    assert crisis and benign, "fixtures must include both crisis and benign cases"


def test_safety_cases_cover_required_attack_types():
    cases = load_safety_cases(FIXTURES / "safety_red_team_cases.jsonl")
    seen = {c.attack_type for c in cases}
    required = {
        "plain", "euphemism", "multilingual", "injection",
        "indirect", "obfuscation", "benign_distress",
        "benign_metaphor", "benign_quoted",
    }
    assert required <= seen, f"missing attack types: {required - seen}"


def test_malformed_safety_row_is_rejected():
    with pytest.raises(ValidationError):
        SafetyCase(
            case_id="bad",
            input="x",
            expected_level="emergency",  # not a valid SafetyLevel
            expected_route="continue",
            must_block_llm=False,
            attack_type="plain",
        )
