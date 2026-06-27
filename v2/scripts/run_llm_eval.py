"""Reproduce the LIVE PR-2b memory eval (writes eval/RESULTS_MEMORY_LLM.md).

Needs an OpenAI key in env (MY_OPENAI_KEY / PERSONAL_OPENAI_KEY / OPENAI_API_KEY).
Run from the v2/ directory:

    MY_OPENAI_KEY="$(cat ~/.innerflow_openai_key)" uv run python scripts/run_llm_eval.py

Costs a few cents (gpt-4o-mini + text-embedding-3-small over the locked split).
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.eval.memory_eval import load_memory_eval_cases  # noqa: E402
from innerflow_v2.eval.run_memory_eval import run_memory_eval  # noqa: E402
from innerflow_v2.memory.llm import LLMReasoner, OpenAIClient  # noqa: E402
from innerflow_v2.memory.systems import BLatestByKey, KernelDeterministic  # noqa: E402
from innerflow_v2.memory.systems_llm import KernelLLM  # noqa: E402


def main() -> None:
    client = OpenAIClient()
    locked = load_memory_eval_cases(ROOT / "eval" / "fixtures" / "memory_eval_locked.jsonl")
    factories = [BLatestByKey, KernelDeterministic, (lambda: KernelLLM(LLMReasoner(client)))]
    reps = run_memory_eval(locked, factories, k=3)

    lines = [
        "# Stage 2 Memory Eval — LIVE (PR-2b)",
        "",
        "Embedding: text-embedding-3-small · Chat: gpt-4o-mini · split: locked · k=3.",
        "Kernel-llm INFERS semantic_key + context from content and judges conflicts via",
        "LLM (PR-1 handed those fields). Precision guards (extra_claim_rate /",
        "false_conflicts) matter here because the LLM can over-emit.",
        "",
        "| system | contradiction ↓ | coverage ↑ | conflict_acc ↑ | recall cur ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |",
        "|---|--:|--:|--:|--:|--:|--:|--:|",
    ]
    for r in reps.values():
        lines.append(
            f"| {r.name} | {r.contradiction_rate:.3f} | {r.coverage_rate:.3f} | "
            f"{r.conflict_resolution_accuracy:.3f} | {r.recall_current_at_k:.3f} | "
            f"{r.recall_historical_at_k:.3f} | {r.extra_claim_rate:.3f} | {r.false_conflict_count} |"
        )
    lines += [
        "",
        "Caveat: locked split (8 cases) — small N; results vary slightly run to run.",
        "This is a real result, not a floor. Inferring key/context from free text is",
        "where the LLM diverges from the deterministic kernel — the spec for further work.",
        "",
    ]
    (ROOT / "eval" / "RESULTS_MEMORY_LLM.md").write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
