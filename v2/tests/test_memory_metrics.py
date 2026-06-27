from __future__ import annotations

from innerflow_v2.eval import (
    conflict_resolution_accuracy,
    contradiction_rate,
    false_positive_rate,
    relevant_recall_at_k,
    safety_bypass_rate,
)
from innerflow_v2.eval.fixtures import SafetyCase
from innerflow_v2.safety.models import SafetyDecision


def test_contradiction_rate():
    assert contradiction_rate(2, 8) == 0.25
    assert contradiction_rate(0, 0) == 0.0  # empty denominator guard


def test_relevant_recall_at_k():
    assert relevant_recall_at_k(["a", "b", "c"], ["b", "d"], k=2) == 0.5
    assert relevant_recall_at_k(["a", "b"], ["a", "b"], k=2) == 1.0
    assert relevant_recall_at_k(["a"], [], k=2) == 0.0  # empty gold guard


def test_conflict_resolution_accuracy():
    assert conflict_resolution_accuracy(["supersede", "keep_both"], ["supersede", "supersede"]) == 0.5
    assert conflict_resolution_accuracy([], []) == 0.0


def _decision(route: str) -> SafetyDecision:
    return SafetyDecision(
        checked=True,
        level="crisis" if route == "crisis_response" else "normal",
        matched_rules=[],
        route=route,
        llm_allowed=route != "crisis_response",
    )


def _case(level: str) -> SafetyCase:
    return SafetyCase(
        case_id="c",
        input="x",
        expected_level=level,
        expected_route="crisis_response" if level == "crisis" else "continue",
        must_block_llm=level == "crisis",
        attack_type="plain",
    )


def test_safety_bypass_rate():
    cases = [_case("crisis"), _case("crisis"), _case("normal")]
    decisions = [_decision("crisis_response"), _decision("continue"), _decision("continue")]
    # one of two crisis cases bypassed
    assert safety_bypass_rate(decisions, cases) == 0.5


def test_safety_bypass_counts_llm_allowed_violation():
    # Right route but the LLM is still allowed on a must_block_llm crisis == bypass.
    cases = [_case("crisis")]
    leaky = SafetyDecision(
        checked=True,
        level="crisis",
        matched_rules=[],
        route="crisis_response",
        llm_allowed=True,  # violates must_block_llm
    )
    assert safety_bypass_rate([leaky], cases) == 1.0


def test_false_positive_rate():
    cases = [_case("normal"), _case("normal"), _case("crisis")]
    decisions = [_decision("continue"), _decision("crisis_response"), _decision("crisis_response")]
    # one of two benign cases falsely routed to crisis
    assert false_positive_rate(decisions, cases) == 0.5
