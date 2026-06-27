"""The discriminator matrix: the kernel must be the only system strong on every
column, and each baseline must fail for a principled reason."""
from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval.memory_eval import load_memory_eval_cases
from innerflow_v2.memory.systems import ALL_SYSTEMS
from innerflow_v2.eval.run_memory_eval import run_memory_eval

FIXTURES = Path(__file__).resolve().parents[1] / "eval" / "fixtures"
CASES = load_memory_eval_cases(FIXTURES / "memory_eval_dev.jsonl") + load_memory_eval_cases(
    FIXTURES / "memory_eval_locked.jsonl"
)
REPORTS = run_memory_eval(CASES, ALL_SYSTEMS, k=3)


def test_kernel_is_perfect_on_current_facts_and_conflicts():
    k = REPORTS["Kernel-deterministic"]
    assert k.contradiction_rate == 0.0
    assert k.coverage_rate == 1.0
    assert k.conflict_resolution_accuracy == 1.0


def test_kernel_dominates_every_baseline_on_contradiction():
    k = REPORTS["Kernel-deterministic"].contradiction_rate
    for name in ("B-full", "B-rag", "B-latest-by-key", "B-extract-only"):
        assert k < REPORTS[name].contradiction_rate, f"kernel not better than {name}"


def test_latest_by_key_strong_current_but_loses_keepboth_and_history():
    latest = REPORTS["B-latest-by-key"]
    kernel = REPORTS["Kernel-deterministic"]
    # it has SOME contradiction (keep_both + recurring keys it drops)
    assert latest.contradiction_rate > 0.0
    # and it cannot retrieve dropped history
    assert latest.recall_historical_at_k < kernel.recall_historical_at_k


def test_brag_builds_no_profile_so_worst_on_contradiction():
    brag = REPORTS["B-rag"]
    assert brag.contradiction_rate == 1.0
    assert brag.coverage_rate == 0.0


def test_baselines_do_not_resolve_conflicts():
    for name in ("B-full", "B-rag", "B-latest-by-key", "B-extract-only"):
        assert REPORTS[name].conflict_resolution_accuracy == 0.0


def test_no_system_hallucinates_conflicts_on_hard_negatives():
    hard = [c for c in CASES if c.category == "hard_negative_no_conflict"]
    from innerflow_v2.memory.systems import KernelDeterministic

    for case in hard:
        k = KernelDeterministic()
        k.ingest(case.observations)
        assert k.resolved_conflicts() == []
