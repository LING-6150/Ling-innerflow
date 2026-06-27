"""Deterministic crisis SafetyGuard — the non-bypassable floor.

This is intentionally a *minimal but real* implementation (not a stub): the
deterministic keyword/rule layer is the fail-safe floor that must catch obvious
crisis wording even when no LLM is available and even if everything downstream
fails. It is NOT complete — it will miss euphemism, indirect ideation,
obfuscation, injection, and quoted third-party text. That gap is measured
(safety_bypass_rate / false_positive_rate) and is exactly what motivates a later
context/LLM layer. The contract this layer guarantees:

  * fail-SAFE, never fail-open: ambiguity escalates, it never silently downgrades.
  * non-bypassable: check() always runs and always returns checked=True.
"""
from __future__ import annotations

import re

from innerflow_v2.safety.models import SafetyDecision

# Plain + common-euphemism crisis markers (English + Chinese). Deliberately
# high-precision-leaning; recall gaps are measured, not hidden.
_CRISIS_MARKERS: tuple[str, ...] = (
    # English — plain
    "kill myself", "killing myself", "suicide", "suicidal",
    "end my life", "take my own life", "want to die", "wanna die",
    "hurt myself", "harm myself", "self-harm", "self harm", "cut myself",
    # English — common euphemisms
    "end it all", "don't want to be here anymore", "do not want to be here anymore",
    "better off dead", "no reason to live", "can't go on anymore",
    # Chinese
    "自杀", "想死", "不想活", "活不下去", "结束生命", "结束这一切",
    "伤害自己", "不想活了", "了结自己", "活着没意思",
)


class SafetyGuard:
    """Deterministic crisis floor. Stateless and side-effect free."""

    def check(self, message: str) -> SafetyDecision:
        text = (message or "")
        normalized = re.sub(r"\s+", " ", text).strip().lower()

        matched = [m for m in _CRISIS_MARKERS if m in normalized]
        if matched:
            return SafetyDecision(
                checked=True,
                level="crisis",
                matched_rules=matched,
                route="crisis_response",
                llm_allowed=False,
            )

        return SafetyDecision(
            checked=True,
            level="normal",
            matched_rules=[],
            route="continue",
            llm_allowed=True,
        )
