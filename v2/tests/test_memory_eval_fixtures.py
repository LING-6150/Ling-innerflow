from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval.memory_eval import load_memory_eval_cases

FIXTURES = Path(__file__).resolve().parents[1] / "eval" / "fixtures"
DEV = load_memory_eval_cases(FIXTURES / "memory_eval_dev.jsonl")
LOCKED = load_memory_eval_cases(FIXTURES / "memory_eval_locked.jsonl")
ALL = DEV + LOCKED


def test_at_least_15_cases_with_dev_and_locked_splits():
    assert len(ALL) >= 15
    assert all(c.split == "dev" for c in DEV)
    assert all(c.split == "locked" for c in LOCKED)
    assert DEV and LOCKED


def test_required_categories_covered():
    cats = {c.category for c in ALL}
    required = {
        "direct_contradiction", "explicit_correction", "stale_preference",
        "context_specific_exception", "recurring_trigger",
        "hard_negative_no_conflict", "retrieval_ambiguity",
    }
    assert required <= cats, f"missing categories: {required - cats}"


def test_every_case_has_at_least_two_queries():
    for c in ALL:
        assert len(c.retrieval_queries) >= 2, f"{c.case_id} has <2 queries"


def test_dataset_has_both_current_and_historical_queries():
    kinds = {q.kind for c in ALL for q in c.retrieval_queries}
    assert {"current", "historical"} <= kinds
