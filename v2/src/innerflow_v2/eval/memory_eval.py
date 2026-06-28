"""Stage 2 memory-eval fixtures + metrics.

Alignment rules are pinned here so they cannot be reinterpreted in an
implementation:

* contradiction_rate is anchored on semantic_key, comparing the system's cited
  observation ids against the gold effective set. The denominator is ALL gold
  facts — a missing claim counts as a contradiction, so returning an empty
  profile cannot shrink the denominator to game the score.
* conflict_resolution_accuracy matches gold<->system conflicts by the unordered
  observation-id pair (not free text).
* relevant_recall_at_k compares retrieved ids against declared relevant ids.

Systems receive ONLY the observation stream; gold lives here with the evaluator.
"""
from __future__ import annotations

import json
from collections.abc import Sequence
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, model_validator

from innerflow_v2.memory.models import MemoryConflict, Resolution
from innerflow_v2.memory.semantic_keys import is_valid_semantic_key
from innerflow_v2.memory.types import ObservationInput, ProfileClaim


# ── fixture schema ────────────────────────────────────────────────────────
class GoldCurrentFact(BaseModel):
    semantic_key: str
    # the observation id(s) that should be reflected as currently effective
    # (one for supersede/stale; several for keep_both or consolidation)
    effective_observation_ids: list[str]


class GoldConflict(BaseModel):
    semantic_key: str
    old_observation_id: str
    new_observation_id: str
    expected_resolution: Resolution


class RetrievalQuery(BaseModel):
    query: str
    kind: Literal["current", "historical"]
    relevant_observation_ids: list[str]


class MemoryEvalCase(BaseModel):
    case_id: str
    category: str
    split: Literal["dev", "locked", "challenge"]
    observations: list[ObservationInput]
    gold_current_facts: list[GoldCurrentFact]
    gold_conflicts: list[GoldConflict]
    retrieval_queries: list[RetrievalQuery]

    @model_validator(mode="after")
    def _validate(self) -> "MemoryEvalCase":
        obs_ids = {o.id for o in self.observations}
        for o in self.observations:
            if not is_valid_semantic_key(o.semantic_key):
                raise ValueError(f"{self.case_id}: unknown semantic_key {o.semantic_key}")
        for f in self.gold_current_facts:
            missing = [i for i in f.effective_observation_ids if i not in obs_ids]
            if missing:
                raise ValueError(f"{self.case_id}: gold fact cites unknown obs {missing}")
        for c in self.gold_conflicts:
            for i in (c.old_observation_id, c.new_observation_id):
                if i not in obs_ids:
                    raise ValueError(f"{self.case_id}: gold conflict cites unknown obs {i}")
        for q in self.retrieval_queries:
            missing = [i for i in q.relevant_observation_ids if i not in obs_ids]
            if missing:
                raise ValueError(f"{self.case_id}: query cites unknown obs {missing}")
        return self


def load_memory_eval_cases(path: str | Path) -> list[MemoryEvalCase]:
    rows = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            rows.append(MemoryEvalCase(**json.loads(line)))
    return rows


# ── metrics (pure) ──────────────────────────────────────────────────────────
def _claims_by_key(claims: Sequence[ProfileClaim]) -> dict[str, set[str]]:
    out: dict[str, set[str]] = {}
    for c in claims:
        out.setdefault(c.semantic_key, set()).update(c.source_observation_ids)
    return out


def contradiction_counts(
    claims: Sequence[ProfileClaim], gold_facts: Sequence[GoldCurrentFact]
) -> tuple[int, int]:
    """(contradictory, evaluated). Missing claim for a gold key => contradictory
    (empty != effective), so an empty profile scores worst, not best."""
    by_key = _claims_by_key(claims)
    contradictory = 0
    for f in gold_facts:
        if by_key.get(f.semantic_key, set()) != set(f.effective_observation_ids):
            contradictory += 1
    return contradictory, len(gold_facts)


def coverage_counts(
    claims: Sequence[ProfileClaim], gold_facts: Sequence[GoldCurrentFact]
) -> tuple[int, int]:
    by_key = _claims_by_key(claims)
    covered = sum(1 for f in gold_facts if f.semantic_key in by_key)
    return covered, len(gold_facts)


def conflict_resolution_counts(
    system_conflicts: Sequence[MemoryConflict], gold_conflicts: Sequence[GoldConflict]
) -> tuple[int, int]:
    """(correct, total_gold). Match by unordered observation-id pair."""
    pred = {
        frozenset((c.old_observation_id, c.new_observation_id)): c.resolution
        for c in system_conflicts
    }
    correct = 0
    for g in gold_conflicts:
        key = frozenset((g.old_observation_id, g.new_observation_id))
        if pred.get(key) == g.expected_resolution:
            correct += 1
    return correct, len(gold_conflicts)


def extra_claim_counts(
    claims: Sequence[ProfileClaim], gold_facts: Sequence[GoldCurrentFact]
) -> tuple[int, int]:
    """(extra, total_claims). An extra claim asserts a semantic_key that has no
    gold fact — i.e. a hallucinated/unwarranted claim. Precision guard: gold-only
    metrics (contradiction/conflict) don't penalize over-emission, so an LLM in
    PR-2 could pad output to lift recall/coverage; this catches that. Lower better."""
    gold_keys = {f.semantic_key for f in gold_facts}
    extra = sum(1 for c in claims if c.semantic_key not in gold_keys)
    return extra, len(claims)


def false_conflict_counts(
    system_conflicts: Sequence[MemoryConflict], gold_conflicts: Sequence[GoldConflict]
) -> tuple[int, int]:
    """(false, total_predicted). A false conflict is a predicted conflict whose
    observation-id pair matches no gold conflict — a hallucinated conflict.
    Precision guard for conflict resolution. Lower better."""
    gold_pairs = {
        frozenset((g.old_observation_id, g.new_observation_id)) for g in gold_conflicts
    }
    false = sum(
        1
        for c in system_conflicts
        if frozenset((c.old_observation_id, c.new_observation_id)) not in gold_pairs
    )
    return false, len(system_conflicts)


def recall_at_k(retrieved_ids: Sequence[str], relevant_ids: Sequence[str], k: int) -> float:
    gold = set(relevant_ids)
    if not gold:
        return 0.0
    return len(set(retrieved_ids[: max(0, k)]) & gold) / len(gold)
