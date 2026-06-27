"""In-memory trace recorder.

Every memory/safety decision should be replayable ("why did the agent remember
/ recall / refuse this?"). This PR only needs in-memory storage; a durable sink
(OTel etc.) is explicitly out of scope.
"""
from __future__ import annotations

import uuid
from datetime import datetime, timezone
from typing import Any

from innerflow_v2.trace.models import TraceEvent, TraceEventType


class TraceRecorder:
    def __init__(self) -> None:
        self._events: list[TraceEvent] = []

    def emit(
        self,
        event_type: TraceEventType,
        user_id: str,
        session_id: str,
        payload: dict[str, Any] | None = None,
    ) -> TraceEvent:
        event = TraceEvent(
            event_id=str(uuid.uuid4()),
            timestamp=datetime.now(timezone.utc),
            event_type=event_type,
            user_id=user_id,
            session_id=session_id,
            payload=payload or {},
        )
        self._events.append(event)
        return event

    @property
    def events(self) -> list[TraceEvent]:
        return list(self._events)

    def of_type(self, event_type: TraceEventType) -> list[TraceEvent]:
        return [e for e in self._events if e.event_type == event_type]
