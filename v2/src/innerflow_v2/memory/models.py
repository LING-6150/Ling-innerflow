"""Typed memory models.

Minimal by design: this PR locks the SHAPE of memory observations, conflicts,
and the consolidated profile, so the eval and metrics can be written before any
real consolidation logic exists.
"""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field

ObservationKind = Literal["fact", "preference", "trigger", "progress", "correction"]
ConflictType = Literal["contradiction", "supersession", "uncertainty"]
Resolution = Literal["supersede", "keep_both", "mark_uncertain", "reject_new"]


class MemoryObservation(BaseModel):
    id: str
    user_id: str
    kind: ObservationKind
    content: str
    timestamp: datetime
    confidence: float = Field(ge=0.0, le=1.0)
    source_session_id: str


class MemoryConflict(BaseModel):
    old_observation_id: str
    new_observation_id: str
    conflict_type: ConflictType
    resolution: Resolution
    rationale: str


class MemoryProfile(BaseModel):
    user_id: str
    observations: list[MemoryObservation] = Field(default_factory=list)
    conflicts: list[MemoryConflict] = Field(default_factory=list)
    updated_at: datetime
