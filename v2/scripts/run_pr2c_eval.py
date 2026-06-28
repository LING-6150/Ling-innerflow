"""PR-2c: does an improved prompt help KernelLLM infer structure — and does it
GENERALIZE to a held-out challenge split (not just the tuning splits)?

Compares prompt v1 vs v2 across dev / locked / challenge, with the deterministic
kernel and B-latest-by-key as reference. Writes eval/RESULTS_MEMORY_PR2C.md.

Tune by reading dev/challenge; the locked split is reported but MUST NOT be tuned
on. Needs a key:

    MY_OPENAI_KEY="$(cat ~/.innerflow_openai_key)" uv run python scripts/run_pr2c_eval.py
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.eval.memory_eval import load_memory_eval_cases  # noqa: E402
from innerflow_v2.eval.run_memory_eval import run_memory_eval  # noqa: E402
from innerflow_v2.memory.llm import _SYSTEM, _SYSTEM_V2, LLMReasoner, OpenAIClient  # noqa: E402
from innerflow_v2.memory.systems import BLatestByKey, KernelDeterministic  # noqa: E402
from innerflow_v2.memory.systems_llm import KernelLLM  # noqa: E402

F = ROOT / "eval" / "fixtures"
SPLITS = {
    "dev": F / "memory_eval_dev.jsonl",
    "locked": F / "memory_eval_locked.jsonl",
    "challenge": F / "memory_eval_challenge.jsonl",
}


def main() -> None:
    client = OpenAIClient()
    factories = [
        BLatestByKey,
        KernelDeterministic,
        (lambda: KernelLLM(LLMReasoner(client, _SYSTEM), name="Kernel-llm-v1")),
        (lambda: KernelLLM(LLMReasoner(client, _SYSTEM_V2), name="Kernel-llm-v2")),
    ]
    out = [
        "# Stage 2 Memory Eval — PR-2c (prompt v1 vs v2, generalization check)",
        "",
        "gpt-4o-mini + text-embedding-3-small · k=3. Prompt v2 adds context-inference",
        "rules + few-shot. Question: does v2 beat v1, and does the gain hold on the",
        "HELD-OUT challenge split (not tuned on)? locked must not be tuned on.",
        "",
    ]
    for split, path in SPLITS.items():
        cases = load_memory_eval_cases(path)
        reps = run_memory_eval(cases, factories, k=3)
        out += [
            f"## split: {split} ({len(cases)} cases)",
            "",
            "| system | contradiction ↓ | conflict_acc ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |",
            "|---|--:|--:|--:|--:|--:|",
        ]
        for r in reps.values():
            out.append(
                f"| {r.name} | {r.contradiction_rate:.3f} | {r.conflict_resolution_accuracy:.3f} | "
                f"{r.recall_historical_at_k:.3f} | {r.extra_claim_rate:.3f} | {r.false_conflict_count} |"
            )
        out.append("")
    out += [
        "Caveat: small N per split; gpt-4o-mini temperature 0 still varies run to run.",
        "Verdict is whatever the challenge split shows — report it honestly, do not",
        "iterate the prompt against locked/challenge until it looks good.",
        "",
    ]
    (ROOT / "eval" / "RESULTS_MEMORY_PR2C.md").write_text("\n".join(out), encoding="utf-8")
    print("\n".join(out))


if __name__ == "__main__":
    main()
