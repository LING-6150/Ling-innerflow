"""Deterministic crisis SafetyGuard — the high-precision, fail-safe floor.

This is a *minimal but real* implementation. Two guarantees it makes:

  * **non-bypassable (structural):** ``check`` always runs and returns
    ``checked=True``; nothing reaches an LLM/tool step without a decision.
  * **fail-safe (directional):** it never *downgrades* a signal it detected, and
    it has **zero bypass on the current pre-registered red-team set** (asserted by
    tests). Light, generalizable obfuscation (leetspeak) is normalized, and the
    marker list is real-world crisis vocabulary — not memorized test strings.

What it is NOT: a completeness guarantee. A novel obfuscation or a purely
contextual phrasing with no crisis vocabulary will still bypass this layer. That
residual — together with the measured false-positive on quoted third-party text
(a context-free keyword matcher cannot tell a quotation from a first-person
statement) — is exactly what a later context/LLM layer is for. The red-team
fixture set is the living spec for coverage.
"""
from __future__ import annotations

import re

from innerflow_v2.safety.models import SafetyDecision
from innerflow_v2.trace.recorder import TraceRecorder

# Real-world crisis vocabulary (English + Chinese): explicit statements, common
# euphemisms, and recognized ideation phrases. NOT fixture-specific strings.
_CRISIS_MARKERS: tuple[str, ...] = (
    # English — explicit
    "kill myself", "killing myself", "suicide", "suicidal",
    "end my life", "end myself", "ending myself", "take my own life",
    "want to die", "wanna die", "hurt myself", "harm myself",
    "self-harm", "self harm", "cut myself",
    # English — common euphemisms / recognized ideation phrases
    "end it all", "don't want to be here anymore", "do not want to be here anymore",
    "better off dead", "better off without me", "no reason to live",
    "no reason to go on", "can't go on anymore",
    # Chinese
    "自杀", "想死", "不想活", "活不下去", "结束生命", "结束这一切",
    "伤害自己", "不想活了", "了结自己", "活着没意思",
)

# Generalizable obfuscation normalization (leetspeak): defends against the
# predictable digit/symbol-for-letter substitutions, not specific test inputs.
_LEET = str.maketrans(
    {"0": "o", "1": "i", "3": "e", "4": "a", "5": "s", "7": "t", "@": "a", "$": "s", "!": "i"}
)


class SafetyGuard:
    """Deterministic crisis floor. Optionally emits trace events."""

    def __init__(self, recorder: TraceRecorder | None = None) -> None:
        self._recorder = recorder

    def check(self, message: str, user_id: str = "", session_id: str = "") -> SafetyDecision:
        normalized = re.sub(r"\s+", " ", (message or "")).strip().lower()
        deleet = normalized.translate(_LEET)

        matched = [m for m in _CRISIS_MARKERS if m in normalized or m in deleet]
        if matched:
            decision = SafetyDecision(
                checked=True,
                level="crisis",
                matched_rules=sorted(set(matched)),
                route="crisis_response",
                llm_allowed=False,
            )
        else:
            decision = SafetyDecision(
                checked=True,
                level="normal",
                matched_rules=[],
                route="continue",
                llm_allowed=True,
            )

        if self._recorder is not None:
            self._recorder.emit(
                "safety.checked", user_id, session_id,
                {"level": decision.level, "llm_allowed": decision.llm_allowed},
            )
            if decision.level == "crisis":
                self._recorder.emit(
                    "safety.crisis_detected", user_id, session_id,
                    {"matched_rules": decision.matched_rules},
                )
        return decision
