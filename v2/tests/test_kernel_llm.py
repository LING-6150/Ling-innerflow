"""KernelLLM wiring, verified offline with the deterministic FakeReasoner (no
tokens). The REAL LLM result is produced by the gated runner with a key and lives
in eval/RESULTS_MEMORY_LLM.md — not asserted here."""
from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval.memory_eval import load_memory_eval_cases
from innerflow_v2.eval.run_memory_eval import run_memory_eval
from innerflow_v2.memory.llm import FakeReasoner
from innerflow_v2.memory.systems_llm import KernelLLM
from innerflow_v2.memory.types import ObservationInput, ProfileClaim

FIXTURES = Path(__file__).resolve().parents[1] / "eval" / "fixtures"
CASES = load_memory_eval_cases(FIXTURES / "memory_eval_dev.jsonl") + load_memory_eval_cases(
    FIXTURES / "memory_eval_locked.jsonl"
)


def _factory():
    return KernelLLM(FakeReasoner())


def test_kernel_llm_with_fake_reasoner_matches_deterministic_kernel():
    reports = run_memory_eval(CASES, [_factory], k=3)
    r = reports["Kernel-llm"]
    assert r.contradiction_rate == 0.0
    assert r.coverage_rate == 1.0
    assert r.conflict_resolution_accuracy == 1.0
    assert r.extra_claim_rate == 0.0
    assert r.false_conflict_count == 0
    assert r.recall_historical_at_k > 0.0


def test_kernel_llm_forwards_reasoner_output():
    class ScriptedReasoner:
        def consolidate(self, observations):
            return [ProfileClaim(semantic_key="goal.current", content="x", source_observation_ids=["o1"], kind="fact")], []

        def rank(self, query, observations, k):
            return ["o1"]

    sys = KernelLLM(ScriptedReasoner())
    sys.ingest([ObservationInput(id="o1", session_id="s1", order=1, semantic_key="goal.current", kind="fact", content="my goal")])
    assert [c.semantic_key for c in sys.profile()] == ["goal.current"]
    assert sys.resolved_conflicts() == []
    assert sys.retrieve("anything", 3) == ["o1"]
