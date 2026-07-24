from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field, model_validator

Category = Literal[
    "correction",
    "supersession",
    "context-exception",
    "no-memory",
    "deletion",
]
Split = Literal["visible", "holdout"]
Role = Literal["user", "assistant", "system"]
Resolution = Literal["supersede", "keep_scoped", "tombstone", "no_conflict"]
OriginType = Literal["adapted_external", "pattern_authored", "consented_anonymized"]


class MemoryEvent(BaseModel):
    id: str
    timestamp: datetime
    role: Role
    content: str


class SetupSession(BaseModel):
    id: str
    events: list[MemoryEvent]

    @model_validator(mode="after")
    def _events_are_ordered(self) -> "SetupSession":
        if not self.events:
            raise ValueError(f"{self.id}: session must contain events")
        timestamps = [event.timestamp for event in self.events]
        if timestamps != sorted(timestamps):
            raise ValueError(f"{self.id}: events are not timestamp ordered")
        if len({event.id for event in self.events}) != len(self.events):
            raise ValueError(f"{self.id}: duplicate event ids")
        return self


class ProbeOption(BaseModel):
    id: Literal["A", "B", "C"]
    text: str


class Probe(BaseModel):
    text: str
    options: list[ProbeOption]

    @model_validator(mode="after")
    def _three_unique_options(self) -> "Probe":
        ids = [option.id for option in self.options]
        if sorted(ids) != ["A", "B", "C"]:
            raise ValueError("probe options must be exactly A, B, C")
        return self


class AnswerRubric(BaseModel):
    expected_choice: Literal["A", "B", "C"]
    forbidden_choices: list[Literal["A", "B", "C"]]
    normalization: Literal["strict_json_choice"] = "strict_json_choice"
    invalid_if_multiple_choices: bool = True


class GoldResolution(BaseModel):
    old_memory_id: str | None = None
    new_memory_id: str | None = None
    expected: Resolution


class DeletionTarget(BaseModel):
    memory_id: str
    locations: list[Literal["raw", "summary", "wiki", "reflection"]]
    normalized_target: str
    forbidden_variants: list[str]


class GoldContract(BaseModel):
    effective_memory_ids: list[str]
    historical_memory_ids: list[str]
    required_context_ids: list[str]
    harmful_context_ids: list[str]
    resolutions: list[GoldResolution]
    baseline_mechanisms: list[str]
    exact_match_bug_exposed: bool = False
    narrow_context_memory_id: str | None = None
    deletion_target: DeletionTarget | None = None


class Provenance(BaseModel):
    origin_type: OriginType
    source_reference: str
    source_item_id: str | None = None
    taxonomy_anchor: str
    transformation_log: str
    author: str
    reviewer: str = "none"
    unambiguous_gold_reason: str
    leakage_note: str


class ReliabilityScenario(BaseModel):
    case_id: str
    category: Category
    split: Split
    setup_sessions: list[SetupSession]
    probe: Probe
    rubric: AnswerRubric
    gold: GoldContract
    provenance: Provenance

    @model_validator(mode="after")
    def _validate_references(self) -> "ReliabilityScenario":
        events = [event for session in self.setup_sessions for event in session.events]
        event_ids = {event.id for event in events}
        if len(event_ids) != len(events):
            raise ValueError(f"{self.case_id}: event ids must be globally unique")
        referenced = set(
            self.gold.effective_memory_ids
            + self.gold.historical_memory_ids
            + self.gold.required_context_ids
            + self.gold.harmful_context_ids
        )
        for resolution in self.gold.resolutions:
            referenced.update(
                memory_id
                for memory_id in (resolution.old_memory_id, resolution.new_memory_id)
                if memory_id
            )
        if self.gold.narrow_context_memory_id:
            referenced.add(self.gold.narrow_context_memory_id)
        if self.gold.deletion_target:
            referenced.add(self.gold.deletion_target.memory_id)
        unknown = sorted(referenced - event_ids)
        if unknown:
            raise ValueError(f"{self.case_id}: gold references unknown event ids {unknown}")
        if self.gold.narrow_context_memory_id not in (None, *self.gold.required_context_ids):
            raise ValueError(
                f"{self.case_id}: narrow context id must be a required context id"
            )
        if self.category == "deletion" and self.gold.deletion_target is None:
            raise ValueError(f"{self.case_id}: deletion case needs deletion_target")
        if self.category != "deletion" and self.gold.deletion_target is not None:
            raise ValueError(f"{self.case_id}: non-deletion case has deletion_target")
        return self


class CandidateRecord(BaseModel):
    candidate_id: str
    status: Literal["included", "rejected", "replaced"]
    case_id: str | None
    category: Category
    origin_type: OriginType
    provenance_tier: Literal["external_item", "construction_pattern", "product_extension"]
    source_reference: str
    reason: str
    content_sha256: str


class FreezeManifest(BaseModel):
    protocol_version: Literal["v1.2"]
    source_commit: str
    generated_at: datetime
    fixture_sha256: str
    candidate_registry_sha256: str
    scenario_hashes: dict[str, str]
    visible_ids: list[str]
    holdout_ids: list[str]
    category_counts: dict[str, int]
    deletion_derived_count: int
    construction_pattern_count: int
    external_item_count: int
    reviewer: Literal["none"]
    limitations: list[str]


def load_scenarios(path: str | Path) -> list[ReliabilityScenario]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("scenario fixture must be a JSON list")
    scenarios = [ReliabilityScenario.model_validate(row) for row in payload]
    if len({scenario.case_id for scenario in scenarios}) != len(scenarios):
        raise ValueError("duplicate case_id")
    return scenarios


def load_candidate_registry(path: str | Path) -> list[CandidateRecord]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("candidate registry must be a JSON list")
    records = [CandidateRecord.model_validate(row) for row in payload]
    if len({record.candidate_id for record in records}) != len(records):
        raise ValueError("duplicate candidate_id")
    return records

