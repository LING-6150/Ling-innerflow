"""Closed semantic-key vocabulary.

A semantic_key is the unit at which memory claims are compared. It is a SHARED,
published schema (like the pattern_key set) — emitted by the extractor and used
by gold alike — so anchoring contradiction comparison on it is fair, not an
answer leak. Keep this closed: a real extractor maps utterances onto these keys.
"""
from __future__ import annotations

SEMANTIC_KEYS: frozenset[str] = frozenset(
    {
        "location.current_city",
        "name.preferred",
        "exercise.preferred_time",
        "encouragement.style",
        "work.role",
        "diet.restriction",
        "communication.preferred_channel",
        "trigger.anxiety",
        "support.helpful_action",
        "goal.current",
    }
)


def is_valid_semantic_key(key: str) -> bool:
    return key in SEMANTIC_KEYS
