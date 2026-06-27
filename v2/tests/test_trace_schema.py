from __future__ import annotations

from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from innerflow_v2.trace import TraceEvent, TraceRecorder

REQUIRED_EVENT_TYPES = [
    "safety.checked",
    "safety.crisis_detected",
    "memory.observation_written",
    "memory.conflict_detected",
    "memory.conflict_resolved",
    "memory.consolidated",
    "memory.retrieved",
]


def test_all_required_event_types_validate():
    for et in REQUIRED_EVENT_TYPES:
        ev = TraceEvent(
            event_id="e1",
            timestamp=datetime.now(timezone.utc),
            event_type=et,
            user_id="u1",
            session_id="s1",
            payload={"k": "v"},
        )
        assert ev.event_type == et


def test_invalid_event_type_is_rejected():
    with pytest.raises(ValidationError):
        TraceEvent(
            event_id="e1",
            timestamp=datetime.now(timezone.utc),
            event_type="memory.telepathy",  # not allowed
            user_id="u1",
            session_id="s1",
        )


def test_recorder_stores_and_filters():
    rec = TraceRecorder()
    rec.emit("safety.checked", "u1", "s1", {"ok": True})
    rec.emit("memory.observation_written", "u1", "s1", {"observation_id": "o1"})
    assert len(rec.events) == 2
    assert len(rec.of_type("safety.checked")) == 1
    assert rec.of_type("memory.observation_written")[0].payload["observation_id"] == "o1"
