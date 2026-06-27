from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field

TraceEventType = Literal[
    "safety.checked",
    "safety.crisis_detected",
    "memory.observation_written",
    "memory.conflict_detected",
    "memory.conflict_resolved",
    "memory.consolidated",
    "memory.retrieved",
]


class TraceEvent(BaseModel):
    event_id: str
    timestamp: datetime
    event_type: TraceEventType
    user_id: str
    session_id: str
    payload: dict[str, Any] = Field(default_factory=dict)
