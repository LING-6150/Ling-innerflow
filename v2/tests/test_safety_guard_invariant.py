"""The core safety property tests.

What this PR LOCKS (hard invariants):
  * non-bypassable (structural): SafetyGuard.check runs and returns checked=True
    for EVERY input.
  * zero bypass on the pre-registered red-team set: every crisis fixture is
    routed to crisis_response AND has the LLM blocked (safety_bypass_rate == 0).
    Generalizable obfuscation (leetspeak) is normalized; markers are real crisis
    vocabulary, not memorized test strings.

What this PR honestly does NOT claim:
  * completeness — a novel obfuscation or purely-contextual phrasing with no
    crisis vocabulary can still bypass a keyword floor. The red-team set is the
    living spec; a later context/LLM layer extends coverage.
  * perfect precision — a quoted third-party crisis sentence trips a measured
    false positive (a context-free matcher can't tell a quotation from a
    first-person statement). That residual motivates the context layer.
"""
from __future__ import annotations

from pathlib import Path

from innerflow_v2.eval import false_positive_rate, load_safety_cases, safety_bypass_rate
from innerflow_v2.safety import SafetyGuard
from innerflow_v2.trace import TraceRecorder

FIXTURES = Path(__file__).resolve().parents[1] / "eval" / "fixtures"
CASES = load_safety_cases(FIXTURES / "safety_red_team_cases.jsonl")
GUARD = SafetyGuard()
DECISIONS = [GUARD.check(c.input) for c in CASES]


def test_invariant_every_input_is_checked():
    # Non-bypassable: nothing reaches downstream without a checked SafetyDecision.
    assert all(d.checked for d in DECISIONS)
    assert len(DECISIONS) == len(CASES)


def test_no_fail_open_on_plain_crisis():
    for d, c in zip(DECISIONS, CASES):
        if c.attack_type == "plain":
            assert c.expected_level == "crisis"
            assert d.route == "crisis_response", f"{c.case_id} failed open"
            assert d.llm_allowed is False


def test_zero_bypass_on_red_team_set():
    # Hard invariant: no crisis fixture is bypassed (route AND llm_allowed checked
    # by safety_bypass_rate). This is "zero bypass on the current red-team set",
    # not a completeness proof.
    assert safety_bypass_rate(DECISIONS, CASES) == 0.0


def test_clear_benigns_not_over_flagged():
    for d, c in zip(DECISIONS, CASES):
        if c.attack_type in ("benign_distress", "benign_metaphor"):
            assert d.route != "crisis_response", f"{c.case_id} false-triggered"

    # Measured precision residual: the quoted-third-party case is an accepted,
    # documented false positive (no context => can't tell a quotation apart).
    fp = false_positive_rate(DECISIONS, CASES)
    assert 0.0 < fp <= 0.34, f"unexpected false_positive_rate={fp}"


def test_guard_emits_trace_events_when_recorder_present():
    rec = TraceRecorder()
    guard = SafetyGuard(recorder=rec)
    guard.check("I'm going to kill myself", user_id="u1", session_id="s1")
    guard.check("Had a normal day.", user_id="u1", session_id="s1")
    # every check is replayable
    assert len(rec.of_type("safety.checked")) == 2
    # only the crisis emits a crisis_detected event
    assert len(rec.of_type("safety.crisis_detected")) == 1
