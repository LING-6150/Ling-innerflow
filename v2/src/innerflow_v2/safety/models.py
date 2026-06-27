from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field

SafetyLevel = Literal["normal", "concern", "crisis"]
SafetyRoute = Literal["continue", "supportive_response", "crisis_response"]


class SafetyDecision(BaseModel):
    # Invariant: `checked` is True whenever SafetyGuard.check ran. The whole
    # point of the kernel is that NO path reaches an LLM/tool step without a
    # SafetyDecision with checked=True.
    checked: bool
    level: SafetyLevel
    matched_rules: list[str] = Field(default_factory=list)
    route: SafetyRoute
    # For crisis, the deterministic floor refuses to let an LLM answer.
    llm_allowed: bool
