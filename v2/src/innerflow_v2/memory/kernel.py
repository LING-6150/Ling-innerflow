"""MemoryKernel — minimal, deterministic.

This PR locks the API shape and emits the trace events the eval depends on. The
*quality* logic (real consolidation / conflict resolution / decay) is the next
PR's subject — here the implementations are deliberately simple so the metrics
and fixtures can be written and the interface frozen.
"""
from __future__ import annotations

import math
from datetime import datetime

from innerflow_v2.memory.models import (
    MemoryConflict,
    MemoryObservation,
    MemoryProfile,
)
from innerflow_v2.trace.recorder import TraceRecorder


class MemoryKernel:
    def __init__(self, recorder: TraceRecorder | None = None) -> None:
        self._obs: dict[str, list[MemoryObservation]] = {}
        self._recorder = recorder

    def write_observation(self, observation: MemoryObservation) -> None:
        self._obs.setdefault(observation.user_id, []).append(observation)
        if self._recorder:
            self._recorder.emit(
                "memory.observation_written",
                observation.user_id,
                observation.source_session_id,
                {"observation_id": observation.id, "kind": observation.kind},
            )

    def retrieve(self, user_id: str, query: str, k: int = 5) -> list[MemoryObservation]:
        """Naive deterministic retrieval: lexical token overlap, tie-broken by
        recency. A real vector/hybrid retriever is the next PR; this is a floor."""
        query_tokens = set(query.lower().split())
        scored = sorted(
            self._obs.get(user_id, []),
            key=lambda o: (
                len(query_tokens & set(o.content.lower().split())),
                o.timestamp,
            ),
            reverse=True,
        )
        result = scored[: max(0, k)]
        if self._recorder:
            self._recorder.emit(
                "memory.retrieved",
                user_id,
                "",
                {"query": query, "k": k, "returned": [o.id for o in result]},
            )
        return result

    def resolve_conflict(
        self, old: MemoryObservation, new: MemoryObservation
    ) -> MemoryConflict:
        """Minimal deterministic heuristic (intentionally imperfect — its
        accuracy is what conflict_resolution_accuracy measures):
          * an explicit `correction` supersedes;
          * otherwise newer-but-coexisting context -> keep_both.
        """
        if new.kind == "correction":
            resolution, ctype, why = "supersede", "supersession", "new is an explicit correction"
        else:
            resolution, ctype, why = "keep_both", "contradiction", "context-specific; retain both"
        conflict = MemoryConflict(
            old_observation_id=old.id,
            new_observation_id=new.id,
            conflict_type=ctype,
            resolution=resolution,
            rationale=why,
        )
        if self._recorder:
            self._recorder.emit(
                "memory.conflict_resolved",
                new.user_id,
                new.source_session_id,
                {"resolution": resolution, "conflict_type": ctype},
            )
        return conflict

    def consolidate(self, user_id: str, now: datetime) -> MemoryProfile:
        profile = MemoryProfile(
            user_id=user_id,
            observations=list(self._obs.get(user_id, [])),
            conflicts=[],
            updated_at=now,
        )
        if self._recorder:
            self._recorder.emit(
                "memory.consolidated",
                user_id,
                "",
                {"observation_count": len(profile.observations)},
            )
        return profile

    def decay(self, user_id: str, now: datetime, half_life_days: float = 90.0) -> None:
        """Exponential time decay of confidence for stale observations."""
        for o in self._obs.get(user_id, []):
            age_days = max(0.0, (now - o.timestamp).total_seconds() / 86400.0)
            o.confidence = round(o.confidence * math.exp(-age_days / half_life_days), 4)
