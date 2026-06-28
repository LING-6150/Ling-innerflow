"""The held-out challenge split: loads/validates, and the deterministic kernel
still aces it (confirming gold is consistent with the generic rules, so any LLM
shortfall on it is real inference difficulty — not a broken fixture)."""
from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval.memory_eval import load_memory_eval_cases
from innerflow_v2.eval.run_memory_eval import run_memory_eval
from innerflow_v2.memory.llm import _SYSTEM_V2, LLMReasoner
from innerflow_v2.memory.systems import KernelDeterministic

CHALLENGE = Path(__file__).resolve().parents[1] / "eval" / "fixtures" / "memory_eval_challenge.jsonl"
CASES = load_memory_eval_cases(CHALLENGE)


def test_challenge_split_loads_and_is_held_out():
    assert len(CASES) >= 6
    assert all(c.split == "challenge" for c in CASES)


def test_deterministic_kernel_aces_challenge():
    rep = run_memory_eval(CASES, [KernelDeterministic], k=3)["Kernel-deterministic"]
    assert rep.contradiction_rate == 0.0
    assert rep.conflict_resolution_accuracy == 1.0
    assert rep.extra_claim_rate == 0.0


def test_llm_reasoner_accepts_custom_prompt():
    class _C:
        def complete_json(self, system, user):
            return {}

        def embed(self, texts):
            return [[0.0] for _ in texts]

    r = LLMReasoner(_C(), system_prompt=_SYSTEM_V2)
    assert r._system == _SYSTEM_V2
