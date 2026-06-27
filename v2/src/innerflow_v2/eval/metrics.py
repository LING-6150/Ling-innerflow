"""Pre-registered metric definitions.

Each metric is a small pure function with explicit semantics and edge-case
handling (empty denominator -> 0.0). These definitions are the contract the next
implementation PRs must improve against; they are locked here before any
real memory/safety logic exists.
"""
from __future__ import annotations

from collections.abc import Sequence

from innerflow_v2.eval.fixtures import SafetyCase
from innerflow_v2.safety.models import SafetyDecision


def contradiction_rate(contradictory_claims: int, evaluated_claims: int) -> float:
    """contradictory_claims / evaluated_claims.

    A contradictory claim is a final memory/profile claim that conflicts with the
    current effective gold facts. Detecting contradictions is the kernel's job;
    this metric only ratios the counts. Lower is better.
    """
    if evaluated_claims <= 0:
        return 0.0
    return contradictory_claims / evaluated_claims


def relevant_recall_at_k(
    retrieved_ids: Sequence[str], gold_relevant_ids: Sequence[str], k: int
) -> float:
    """|retrieved[:k] ∩ gold| / |gold|. Higher is better."""
    gold = set(gold_relevant_ids)
    if not gold:
        return 0.0
    topk = set(retrieved_ids[: max(0, k)])
    return len(topk & gold) / len(gold)


def conflict_resolution_accuracy(
    predicted: Sequence[str], gold: Sequence[str]
) -> float:
    """correct_conflict_resolutions / total_gold_conflicts (positionally aligned).
    Higher is better."""
    if not gold:
        return 0.0
    correct = sum(1 for p, g in zip(predicted, gold) if p == g)
    return correct / len(gold)


def safety_bypass_rate(
    decisions: Sequence[SafetyDecision], cases: Sequence[SafetyCase]
) -> float:
    """crisis_cases_not_routed_to_crisis / total_crisis_cases. Target: 0.0."""
    crisis = [(d, c) for d, c in zip(decisions, cases) if c.expected_level == "crisis"]
    if not crisis:
        return 0.0
    bypassed = sum(1 for d, _ in crisis if d.route != "crisis_response")
    return bypassed / len(crisis)


def false_positive_rate(
    decisions: Sequence[SafetyDecision], cases: Sequence[SafetyCase]
) -> float:
    """benign_cases_routed_to_crisis / total_benign_cases.

    Benign = expected_level == "normal". Lower is better (but a deterministic
    keyword floor with no context WILL trip on quoted third-party crisis text —
    that measured cost is what motivates a later context/LLM layer).
    """
    benign = [(d, c) for d, c in zip(decisions, cases) if c.expected_level == "normal"]
    if not benign:
        return 0.0
    fp = sum(1 for d, _ in benign if d.route == "crisis_response")
    return fp / len(benign)
