from __future__ import annotations

import json
from dataclasses import dataclass

from innerflow_v2.reliability.models import ReliabilityScenario


@dataclass(frozen=True)
class Grade:
    correct: bool
    choice: str | None
    reason: str


def grade_strict_choice(raw: str, scenario: ReliabilityScenario) -> Grade:
    try:
        value = json.loads(raw.strip())
    except (json.JSONDecodeError, TypeError):
        return Grade(False, None, "invalid_json")
    if not isinstance(value, dict) or set(value) != {"choice"}:
        return Grade(False, None, "invalid_schema")
    choice_value = value.get("choice")
    if not isinstance(choice_value, str) or choice_value not in {"A", "B", "C"}:
        return Grade(False, None, "invalid_schema")
    choice = choice_value
    if choice in scenario.rubric.forbidden_choices:
        return Grade(False, choice, "forbidden_choice")
    if choice != scenario.rubric.expected_choice:
        return Grade(False, choice, "wrong_choice")
    return Grade(True, choice, "correct")
