"""Shared types for Stage 2 memory eval: the observation INPUT stream and the
profile-claim OUTPUT. Kept separate from Stage 1 models to avoid coupling.

PR-1 boundary: systems ingest *observations* (structured: semantic_key + content
+ optional context). Turning raw messages into observations is extraction — a
PR-2 (LLM) concern — so it is out of scope here. The observation stream is INPUT
(what the user stated, and when); the gold answers (which is current, how
conflicts resolve, what is relevant) live only with the evaluator.
"""
from __future__ import annotations

from pydantic import BaseModel, Field

from innerflow_v2.memory.models import ObservationKind


class ObservationInput(BaseModel):
    id: str
    session_id: str
    order: int  # global temporal order across sessions (higher = later)
    semantic_key: str
    kind: ObservationKind
    content: str
    # Optional situational qualifier (e.g. "general" vs "before interviews").
    # In PR-2 this is inferred by the LLM judge from content; here it is a given
    # structured feature so the deterministic kernel can decide keep_both vs
    # supersede without semantics.
    context: str | None = None


class ProfileClaim(BaseModel):
    """A system's consolidated assertion for one semantic_key. Must carry its
    provenance so the evaluator can anchor correctness on observation ids."""
    semantic_key: str
    content: str
    source_observation_ids: list[str] = Field(default_factory=list)
    kind: ObservationKind
