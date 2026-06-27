"""Pre-registered eval fixtures + schema-validated loaders.

The fixture SCHEMA is the contract: tests validate every row, so a malformed or
under-specified case fails loudly instead of silently weakening the eval.
"""
from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, model_validator

from innerflow_v2.memory.models import ObservationKind, Resolution
from innerflow_v2.safety.models import SafetyLevel, SafetyRoute


# ── memory conflict fixtures ──────────────────────────────────────────────
class FixtureSession(BaseModel):
    session_id: str
    messages: list[str]


class GoldFact(BaseModel):
    kind: ObservationKind
    content: str
    effective_from_session: str


class GoldConflict(BaseModel):
    old: str
    new: str
    expected_resolution: Resolution


class GoldObservation(BaseModel):
    """A declared gold memory the kernel is expected to hold. Retrieval gold
    references these by id, so an eval can't pass by inventing an id."""
    id: str
    kind: ObservationKind
    content: str
    session_id: str


class GoldRetrievalQuery(BaseModel):
    query: str
    relevant_memory_ids: list[str]


class MemoryConflictCase(BaseModel):
    case_id: str
    sessions: list[FixtureSession]
    gold_observations: list[GoldObservation]
    gold_current_facts: list[GoldFact]
    gold_conflicts: list[GoldConflict]
    gold_retrieval_queries: list[GoldRetrievalQuery]

    @model_validator(mode="after")
    def _retrieval_ids_must_be_declared(self) -> "MemoryConflictCase":
        declared = {o.id for o in self.gold_observations}
        for q in self.gold_retrieval_queries:
            unknown = [mid for mid in q.relevant_memory_ids if mid not in declared]
            if unknown:
                raise ValueError(
                    f"{self.case_id}: retrieval query references undeclared "
                    f"gold_observation ids {unknown}"
                )
        return self


# ── safety red-team fixtures ──────────────────────────────────────────────
class SafetyCase(BaseModel):
    case_id: str
    input: str
    expected_level: SafetyLevel
    expected_route: SafetyRoute
    must_block_llm: bool
    attack_type: str


def _read_jsonl(path: str | Path) -> list[dict]:
    rows: list[dict] = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def load_memory_cases(path: str | Path) -> list[MemoryConflictCase]:
    return [MemoryConflictCase(**row) for row in _read_jsonl(path)]


def load_safety_cases(path: str | Path) -> list[SafetyCase]:
    return [SafetyCase(**row) for row in _read_jsonl(path)]
