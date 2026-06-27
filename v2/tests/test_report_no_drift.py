"""Guard against the committed RESULTS_MEMORY.md drifting from what the harness
actually produces (e.g. a hand-edited report). Regenerate and compare."""
from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval.memory_eval import load_memory_eval_cases
from innerflow_v2.eval.run_memory_eval import render_markdown, run_memory_eval
from innerflow_v2.memory.systems import ALL_SYSTEMS

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "eval" / "RESULTS_MEMORY.md"


def test_committed_report_matches_generated_output():
    locked = load_memory_eval_cases(ROOT / "eval" / "fixtures" / "memory_eval_locked.jsonl")
    expected = render_markdown(run_memory_eval(locked, ALL_SYSTEMS, k=3), k=3, split="locked")
    assert REPORT.read_text(encoding="utf-8") == expected, (
        "RESULTS_MEMORY.md is stale — regenerate it, do not hand-edit."
    )
