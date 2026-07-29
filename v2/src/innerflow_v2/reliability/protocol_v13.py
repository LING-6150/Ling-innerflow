from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import StrEnum
from pathlib import Path
from typing import Any, Literal

import rfc8785
from pydantic import BaseModel, ConfigDict, Field, model_validator


POLICIES = ("B-summary", "B-full", "B-none")
REQUIRED_MEMORY_CATEGORIES = {
    "correction",
    "supersession",
    "context-exception",
}
EXPECTED_CATEGORY_COUNTS = {
    "correction": 6,
    "supersession": 4,
    "context-exception": 6,
    "no-memory": 4,
    "deletion": 4,
}
HOLDOUT_COUNTS = {
    "correction": 2,
    "supersession": 1,
    "context-exception": 2,
    "no-memory": 1,
    "deletion": 2,
}
NIST_BEACON_V2_CHAIN = "1"
NIST_BEACON_V2_ENDPOINT = "https://beacon.nist.gov/beacon/2.0"
HEX_64 = re.compile(r"^[0-9a-f]{64}$")
HEX_EVEN = re.compile(r"^(?:[0-9a-f]{2})+$")


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ResponseAction(StrEnum):
    ASK_PERMISSION = "ASK_PERMISSION"
    GIVE_DIRECT_STEPS = "GIVE_DIRECT_STEPS"
    USE_CONCISE_DETAIL = "USE_CONCISE_DETAIL"
    USE_EXPANDED_DETAIL = "USE_EXPANDED_DETAIL"
    USE_GENTLE_PRIVATE = "USE_GENTLE_PRIVATE"
    USE_DIRECT_TONE = "USE_DIRECT_TONE"
    USE_UNPERSONALIZED_DEFAULT = "USE_UNPERSONALIZED_DEFAULT"


class GoldApplicability(StrEnum):
    CURRENT_EFFECTIVE = "CURRENT_EFFECTIVE"
    HISTORICAL = "HISTORICAL"
    OUT_OF_SCOPE = "OUT_OF_SCOPE"
    DELETED = "DELETED"
    IRRELEVANT = "IRRELEVANT"
    FORBIDDEN_INFERENCE = "FORBIDDEN_INFERENCE"


class ObservedRendering(StrEnum):
    NOT_DETECTED_UNDER_FROZEN_LEXICON = "NOT_DETECTED_UNDER_FROZEN_LEXICON"
    ACTIVE_ASSERTION = "ACTIVE_ASSERTION"
    HISTORICAL_OR_NEGATED = "HISTORICAL_OR_NEGATED"
    AMBIGUOUS = "AMBIGUOUS"


class ConformancePredicate(StrEnum):
    SCHEMA_HASH_CANONICALIZATION = "schema_hash_canonicalization"
    STRATUM_PROVENANCE_QUOTA = "stratum_provenance_quota"
    DETERMINISTIC_PAIR_CHECK = "deterministic_pair_check"
    OLD_ITEM_OVERLAP = "old_item_overlap"
    PROVENANCE_ARTIFACT = "provenance_artifact"


@dataclass(frozen=True)
class MatrixRow:
    category: str
    action_band: str
    selected_external: int
    selected_product: int
    slots: tuple[str, str, str]
    action_pair: tuple[ResponseAction, ResponseAction] | None = None
    deletion_pre_actions: tuple[ResponseAction, ResponseAction] | None = None

    @property
    def selected(self) -> int:
        return self.selected_external + self.selected_product


MATRIX_ROWS: tuple[MatrixRow, ...] = (
    MatrixRow(
        "correction",
        "detail",
        2,
        0,
        ("project_update", "tutorial_explanation", "itinerary_briefing"),
        (ResponseAction.USE_CONCISE_DETAIL, ResponseAction.USE_EXPANDED_DETAIL),
    ),
    MatrixRow(
        "correction",
        "tone",
        2,
        0,
        ("peer_review", "planning_disagreement", "accountability_reminder"),
        (ResponseAction.USE_GENTLE_PRIVATE, ResponseAction.USE_DIRECT_TONE),
    ),
    MatrixRow(
        "correction",
        "support",
        1,
        1,
        ("routine_setback", "decision_uncertainty", "creative_block"),
        (ResponseAction.ASK_PERMISSION, ResponseAction.GIVE_DIRECT_STEPS),
    ),
    MatrixRow(
        "supersession",
        "detail",
        2,
        0,
        ("meeting_recap", "technical_handoff", "options_comparison"),
        (ResponseAction.USE_CONCISE_DETAIL, ResponseAction.USE_EXPANDED_DETAIL),
    ),
    MatrixRow(
        "supersession",
        "tone",
        1,
        1,
        ("performance_reflection", "boundary_negotiation", "schedule_conflict"),
        (ResponseAction.USE_GENTLE_PRIVATE, ResponseAction.USE_DIRECT_TONE),
    ),
    MatrixRow(
        "context-exception",
        "detail",
        2,
        0,
        ("audience_scope", "work_personal_scope", "urgency_scope"),
        (ResponseAction.USE_CONCISE_DETAIL, ResponseAction.USE_EXPANDED_DETAIL),
    ),
    MatrixRow(
        "context-exception",
        "tone",
        2,
        0,
        ("public_private_scope", "work_home_scope", "celebration_debug_scope"),
        (ResponseAction.USE_GENTLE_PRIVATE, ResponseAction.USE_DIRECT_TONE),
    ),
    MatrixRow(
        "context-exception",
        "support",
        1,
        1,
        ("venting_planning_scope", "ideation_decision_scope", "setback_next_scope"),
        (ResponseAction.ASK_PERMISSION, ResponseAction.GIVE_DIRECT_STEPS),
    ),
    MatrixRow(
        "no-memory",
        "information-task-default",
        2,
        0,
        ("unrelated_scheduling", "neutral_summary", "document_organization"),
    ),
    MatrixRow(
        "no-memory",
        "social-support-default",
        1,
        1,
        ("casual_greeting", "third_party_coordination", "neutral_checkin"),
    ),
    MatrixRow(
        "deletion",
        "detail",
        0,
        2,
        ("saved_report_format", "saved_reading_format", "saved_planning_format"),
        deletion_pre_actions=(
            ResponseAction.USE_CONCISE_DETAIL,
            ResponseAction.USE_EXPANDED_DETAIL,
        ),
    ),
    MatrixRow(
        "deletion",
        "tone",
        0,
        2,
        ("saved_feedback_style", "saved_reminder_style", "saved_collaboration_style"),
        deletion_pre_actions=(
            ResponseAction.USE_GENTLE_PRIVATE,
            ResponseAction.USE_DIRECT_TONE,
        ),
    ),
)
MATRIX_BY_KEY = {(row.category, row.action_band): row for row in MATRIX_ROWS}


class SymmetryCertificate(StrictModel):
    controlled_claim: str
    action_a_binding: str
    action_b_binding: str
    both_plausible_and_safe_reason: str
    no_non_memory_preference_reason: str
    reviewer_id: str
    disposition: Literal["pass"]


class TrackedClaim(StrictModel):
    claim_id: str
    canonical_value: str
    source_event_ids: list[str]
    gold_applicability: GoldApplicability
    surface_forms: list[str] = Field(min_length=1)
    active_cues: list[str] = Field(default_factory=list)
    historical_cues: list[str] = Field(default_factory=list)


class SetupMemoryEventV13(StrictModel):
    event_id: str = Field(min_length=1)
    sequence_index: int = Field(ge=0)
    role: Literal["user", "assistant", "system"]
    operation: Literal[
        "observe",
        "correct",
        "supersede",
        "scope",
        "delete",
    ]
    content: str = Field(min_length=1)
    claim_ids: list[str] = Field(default_factory=list)
    delete_target_claim_id: str | None = None

    @model_validator(mode="after")
    def _event_operation_contract(self) -> "SetupMemoryEventV13":
        if len(set(self.claim_ids)) != len(self.claim_ids):
            raise ValueError("setup event claim ids must be unique")
        if self.operation == "delete":
            if not self.delete_target_claim_id or self.claim_ids:
                raise ValueError(
                    "delete event needs one target and cannot assert claims"
                )
        elif self.delete_target_claim_id is not None:
            raise ValueError("only delete events may name a delete target")
        return self


class CandidateProvenanceV13(StrictModel):
    origin_type: Literal["construction_pattern", "product_extension"]
    source_reference: str = Field(min_length=1)
    source_item_id: str | None = None
    taxonomy_anchors: list[str] = Field(min_length=1)
    transformation_log: str = Field(min_length=1)
    author: str = Field(min_length=1)
    reviewer: str = Field(min_length=1)
    unambiguous_gold_reason: str = Field(min_length=1)
    leakage_note: str = Field(min_length=1)

    @model_validator(mode="after")
    def _provenance_is_canonical(self) -> "CandidateProvenanceV13":
        if self.taxonomy_anchors != sorted(set(self.taxonomy_anchors)):
            raise ValueError("provenance taxonomy anchors must be sorted and unique")
        if self.source_item_id is not None:
            raise ValueError(
                "M0 construction-pattern/product-extension items cannot claim "
                "item-level provenance"
            )
        return self


class CounterfactualWorld(StrictModel):
    world_id: str
    probe: str
    non_memory_state: dict[str, str]
    setup_memory_events: list[SetupMemoryEventV13] = Field(min_length=1)
    effective_claim: str | None
    applicable_claim_id: str | None
    gold_response_action: ResponseAction


class CandidateV13(StrictModel):
    candidate_id: str = Field(min_length=1)
    category: Literal[
        "correction",
        "supersession",
        "context-exception",
        "no-memory",
        "deletion",
    ]
    action_band: str = Field(min_length=1)
    situation_slot: str = Field(min_length=1)
    provenance_tier: Literal["E", "P"]
    provenance_reference: str = Field(min_length=1)
    provenance_artifact: CandidateProvenanceV13
    underlying_event_fingerprint: str
    probe_template_fingerprint: str
    semantic_overlap_fingerprint: str
    authoring_task_id: str = Field(min_length=1)
    authoring_prompt_sha256: str
    visible_materials_sha256: str
    author_saw_v12_item_ledger: Literal[False] = False
    worlds: list[CounterfactualWorld] = Field(min_length=2, max_length=2)
    tracked_claims: list[TrackedClaim] = Field(default_factory=list)
    symmetry_certificate: SymmetryCertificate | None = None
    deletion_storage_locations: list[
        Literal["raw", "summary", "wiki", "reflection"]
    ] = Field(default_factory=list)
    old_item_overlap_hashes: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def _validate_frozen_construct(self) -> "CandidateV13":
        row = MATRIX_BY_KEY.get((self.category, self.action_band))
        if row is None:
            raise ValueError("candidate is outside the frozen matrix")
        if self.situation_slot not in row.slots:
            raise ValueError("candidate uses an unregistered situation/template slot")
        if len({world.world_id for world in self.worlds}) != 2:
            raise ValueError("counterfactual world ids must be unique")
        if len({world.probe for world in self.worlds}) != 1:
            raise ValueError("counterfactual probes must be byte-identical")
        if len(
            {
                tuple(sorted(world.non_memory_state.items()))
                for world in self.worlds
            }
        ) != 1:
            raise ValueError("counterfactual non-memory state must be identical")
        for world in self.worlds:
            indexes = [event.sequence_index for event in world.setup_memory_events]
            if indexes != list(range(len(indexes))):
                raise ValueError("setup memory event indexes must be contiguous")
            event_ids = [event.event_id for event in world.setup_memory_events]
            if len(set(event_ids)) != len(event_ids):
                raise ValueError("setup memory event ids must be unique")
        if self.old_item_overlap_hashes:
            raise ValueError("candidate overlaps a frozen v1.2 item")
        if not all(
            HEX_64.fullmatch(value)
            for value in (
                self.authoring_prompt_sha256,
                self.visible_materials_sha256,
                self.underlying_event_fingerprint,
                self.probe_template_fingerprint,
                self.semantic_overlap_fingerprint,
            )
        ):
            raise ValueError("candidate hashes/fingerprints must be lowercase SHA-256")

        actions = {world.gold_response_action for world in self.worlds}
        claims_by_id = {claim.claim_id: claim for claim in self.tracked_claims}
        if len(claims_by_id) != len(self.tracked_claims):
            raise ValueError("tracked claim ids must be unique")
        source_event_ids = {
            event.event_id
            for world in self.worlds
            for event in world.setup_memory_events
        }
        if any(
            source_event_id not in source_event_ids
            for claim in self.tracked_claims
            for source_event_id in claim.source_event_ids
        ):
            raise ValueError("tracked claim references an unknown setup event")
        if self.provenance_reference != self.provenance_artifact.source_reference:
            raise ValueError("candidate provenance reference drift")
        expected_origin = {
            "E": "construction_pattern",
            "P": "product_extension",
        }[self.provenance_tier]
        if self.provenance_artifact.origin_type != expected_origin:
            raise ValueError("candidate provenance tier/origin drift")
        if self.category in REQUIRED_MEMORY_CATEGORIES:
            by_id = {world.world_id: world for world in self.worlds}
            if set(by_id) != {"world_a", "world_b"}:
                raise ValueError("required-memory worlds must be world_a/world_b")
            if tuple(sorted(action.value for action in actions)) != tuple(
                sorted(action.value for action in row.action_pair or ())
            ):
                raise ValueError("required-memory worlds must use the frozen action pair")
            if any(
                not world.effective_claim or not world.effective_claim.strip()
                for world in self.worlds
            ):
                raise ValueError("required-memory effective claims must be non-empty")
            if len({world.effective_claim for world in self.worlds}) != 2:
                raise ValueError("required-memory effective claims must differ")
            applicable_ids = {world.applicable_claim_id for world in self.worlds}
            if None in applicable_ids or len(applicable_ids) != 2:
                raise ValueError("each required world needs a different applicable claim")
            for world in self.worlds:
                claim = claims_by_id.get(world.applicable_claim_id or "")
                if (
                    claim is None
                    or claim.gold_applicability
                    != GoldApplicability.CURRENT_EFFECTIVE
                    or claim.canonical_value != world.effective_claim
                ):
                    raise ValueError(
                        "required world must bind its current-effective tracked claim"
                    )
            if self.symmetry_certificate is None:
                raise ValueError("required-memory candidate needs a symmetry certificate")
            left_events, right_events = (
                by_id["world_a"].setup_memory_events,
                by_id["world_b"].setup_memory_events,
            )
            if len(left_events) != len(right_events):
                raise ValueError("required-memory worlds must share event structure")
            changed_events = 0
            for left, right in zip(left_events, right_events, strict=True):
                left_payload = left.model_dump(mode="json")
                right_payload = right.model_dump(mode="json")
                left_content = left_payload.pop("content")
                right_content = right_payload.pop("content")
                left_claim_ids = left_payload.pop("claim_ids")
                right_claim_ids = right_payload.pop("claim_ids")
                if left_payload != right_payload:
                    raise ValueError(
                        "required-memory worlds may change only one event payload"
                    )
                changed_events += (
                    left_content != right_content
                    or left_claim_ids != right_claim_ids
                )
            if changed_events != 1:
                raise ValueError(
                    "required-memory worlds need exactly one memory intervention"
                )
            if (
                self.symmetry_certificate.action_a_binding
                != by_id["world_a"].gold_response_action.value
                or self.symmetry_certificate.action_b_binding
                != by_id["world_b"].gold_response_action.value
            ):
                raise ValueError("symmetry certificate action bindings drift")
        elif self.category == "no-memory":
            if actions != {ResponseAction.USE_UNPERSONALIZED_DEFAULT}:
                raise ValueError("no-memory variants must use the default action")
            if {world.world_id for world in self.worlds} != {
                "variant_a",
                "variant_b",
            }:
                raise ValueError("no-memory worlds must be variant_a/variant_b")
            if any(
                not world.effective_claim or not world.effective_claim.strip()
                for world in self.worlds
            ) or len({world.effective_claim for world in self.worlds}) != 2:
                raise ValueError("no-memory variants need two distinct irrelevant claims")
            if any(world.applicable_claim_id is not None for world in self.worlds):
                raise ValueError("irrelevant variants cannot bind an applicable claim")
            irrelevant_values = {
                claim.canonical_value
                for claim in self.tracked_claims
                if claim.gold_applicability == GoldApplicability.IRRELEVANT
            }
            if {world.effective_claim for world in self.worlds} != irrelevant_values:
                raise ValueError("no-memory worlds must bind tracked irrelevant claims")
            left_events, right_events = (
                next(
                    world for world in self.worlds if world.world_id == "variant_a"
                ).setup_memory_events,
                next(
                    world for world in self.worlds if world.world_id == "variant_b"
                ).setup_memory_events,
            )
            if len(left_events) != len(right_events):
                raise ValueError("no-memory variants must share event structure")
            changed_events = 0
            for left, right in zip(left_events, right_events, strict=True):
                left_payload = left.model_dump(mode="json")
                right_payload = right.model_dump(mode="json")
                left_content = left_payload.pop("content")
                right_content = right_payload.pop("content")
                left_claim_ids = left_payload.pop("claim_ids")
                right_claim_ids = right_payload.pop("claim_ids")
                if left_payload != right_payload:
                    raise ValueError(
                        "no-memory variants may change only one event payload"
                    )
                changed_events += (
                    left_content != right_content
                    or left_claim_ids != right_claim_ids
                )
            if changed_events != 1:
                raise ValueError(
                    "no-memory variants need exactly one irrelevant-memory change"
                )
            if self.deletion_storage_locations:
                raise ValueError("no-memory candidate cannot declare deletion storage")
        else:
            by_id = {world.world_id: world for world in self.worlds}
            if set(by_id) != {"pre_delete", "post_delete"}:
                raise ValueError("deletion worlds must be pre_delete/post_delete")
            if (
                by_id["post_delete"].gold_response_action
                != ResponseAction.USE_UNPERSONALIZED_DEFAULT
            ):
                raise ValueError("post-delete world must use the default action")
            if by_id["pre_delete"].gold_response_action not in (
                row.deletion_pre_actions or ()
            ):
                raise ValueError("pre-delete action is outside the frozen band")
            pre = by_id["pre_delete"]
            post = by_id["post_delete"]
            if (
                not pre.effective_claim
                or not pre.effective_claim.strip()
                or pre.applicable_claim_id is None
                or post.effective_claim is not None
                or post.applicable_claim_id is not None
            ):
                raise ValueError("deletion must remove the pre-delete effective claim")
            claim = claims_by_id.get(pre.applicable_claim_id)
            if (
                claim is None
                or claim.gold_applicability != GoldApplicability.DELETED
                or claim.canonical_value != pre.effective_claim
            ):
                raise ValueError("deletion worlds must bind the deleted tracked claim")
            pre_events = by_id["pre_delete"].setup_memory_events
            post_events = by_id["post_delete"].setup_memory_events
            if (
                len(post_events) != len(pre_events) + 1
                or post_events[:-1] != pre_events
                or post_events[-1].operation != "delete"
                or post_events[-1].delete_target_claim_id
                != pre.applicable_claim_id
            ):
                raise ValueError(
                    "post-delete world must append one matching delete event"
                )
            if not self.deletion_storage_locations:
                raise ValueError("deletion candidate needs frozen storage locations")
        if self.category != "deletion" and self.deletion_storage_locations:
            raise ValueError("only deletion candidates declare storage locations")
        return self


class CandidateRegistryRecordV13(StrictModel):
    candidate_id: str = Field(min_length=1)
    inventory_role: Literal["primary", "reserve"]
    status: Literal["eligible", "reserve", "rejected", "replaced"]
    category: str = Field(min_length=1)
    action_band: str = Field(min_length=1)
    situation_slot: str = Field(min_length=1)
    provenance_tier: Literal["E", "P"]
    taxonomy_anchors: list[str] = Field(min_length=1)
    reserve_target_id: str | None = None
    content_sha256: str
    reason: str = Field(min_length=1)
    replaced_by: str | None = None
    audit_artifact_sha256: str
    reviewer_id: str = Field(min_length=1)
    reviewer_disposition: Literal["pass", "held", "fail", "superseded"]
    authoring_task_id: str = Field(min_length=1)
    authoring_prompt_sha256: str
    visible_materials_sha256: str
    provenance_reference: str = Field(min_length=1)
    counterfactual_dependency_result: Literal["pass", "fail", "not_applicable"]
    ontology_compatible: bool
    claim_state_review: Literal["pass", "fail", "not_applicable"]
    author_saw_v12_item_ledger: bool

    @model_validator(mode="after")
    def _replacement_contract(self) -> "CandidateRegistryRecordV13":
        for value in (
            self.content_sha256,
            self.audit_artifact_sha256,
            self.authoring_prompt_sha256,
            self.visible_materials_sha256,
        ):
            if not HEX_64.fullmatch(value):
                raise ValueError("registry audit hashes must be lowercase SHA-256")
        if self.status == "replaced" and not self.replaced_by:
            raise ValueError("replaced record must name its replacement")
        if self.status != "replaced" and self.replaced_by is not None:
            raise ValueError("only replaced records may name a replacement")
        expected_disposition = {
            "eligible": "pass",
            "reserve": "held",
            "rejected": "fail",
            "replaced": "superseded",
        }[self.status]
        if self.reviewer_disposition != expected_disposition:
            raise ValueError("registry status and reviewer disposition disagree")
        if self.status == "eligible" and (
            not self.ontology_compatible or self.claim_state_review != "pass"
        ):
            raise ValueError("eligible record must pass ontology and claim review")
        if self.taxonomy_anchors != sorted(set(self.taxonomy_anchors)):
            raise ValueError("taxonomy anchors must be sorted and unique")
        if self.inventory_role == "primary" and self.reserve_target_id is not None:
            raise ValueError("primary inventory record cannot name a reserve target")
        if self.inventory_role == "reserve" and (
            not self.reserve_target_id
            or self.reserve_target_id == self.candidate_id
        ):
            raise ValueError("reserve inventory record must name another candidate")
        if self.status == "reserve" and self.inventory_role != "reserve":
            raise ValueError("only a signed reserve assignment may be held in reserve")
        return self


class CandidateRegistryV13(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    authoring_inventory_ids: list[str] = Field(min_length=36)
    authoring_inventory_sha256: str
    authoring_inventory_assignments_sha256: str
    candidates: list[CandidateV13]
    records: list[CandidateRegistryRecordV13]

    @model_validator(mode="after")
    def _inventory_is_precommitted(self) -> "CandidateRegistryV13":
        if len(set(self.authoring_inventory_ids)) != len(
            self.authoring_inventory_ids
        ):
            raise ValueError("authoring inventory ids must be unique")
        if not HEX_64.fullmatch(
            self.authoring_inventory_sha256
        ) or not HEX_64.fullmatch(self.authoring_inventory_assignments_sha256):
            raise ValueError("authoring inventory hash must be lowercase SHA-256")
        return self


class AuthoringInventoryAssignment(StrictModel):
    candidate_id: str = Field(min_length=1)
    role: Literal["primary", "reserve"]
    category: str = Field(min_length=1)
    action_band: str = Field(min_length=1)
    situation_slot: str = Field(min_length=1)
    provenance_tier: Literal["E", "P"]
    taxonomy_anchors: list[str] = Field(min_length=1)
    reserve_target_id: str | None = None

    @model_validator(mode="after")
    def _assignment_is_canonical(self) -> "AuthoringInventoryAssignment":
        row = MATRIX_BY_KEY.get((self.category, self.action_band))
        if row is None or self.situation_slot not in row.slots:
            raise ValueError("authoring assignment is outside the frozen matrix")
        if self.taxonomy_anchors != sorted(set(self.taxonomy_anchors)):
            raise ValueError("taxonomy anchors must be sorted and unique")
        if self.role == "primary" and self.reserve_target_id is not None:
            raise ValueError("primary assignment cannot name a reserve target")
        if self.role == "reserve" and (
            not self.reserve_target_id
            or self.reserve_target_id == self.candidate_id
        ):
            raise ValueError("reserve assignment must name another candidate")
        return self


class SignedAuthoringInventory(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    candidate_ids: list[str] = Field(min_length=36)
    candidate_ids_sha256: str
    assignments: list[AuthoringInventoryAssignment] = Field(min_length=36)
    assignments_sha256: str
    review_disposition_sha256: str
    frozen_at: datetime
    signed_by: list[str] = Field(min_length=2)

    @model_validator(mode="after")
    def _signed_inventory_identity(self) -> "SignedAuthoringInventory":
        if len(set(self.candidate_ids)) != len(self.candidate_ids):
            raise ValueError("signed authoring inventory ids must be unique")
        if canonical_sha256(sorted(self.candidate_ids)) != self.candidate_ids_sha256:
            raise ValueError("signed authoring inventory hash drift")
        assignment_ids = [assignment.candidate_id for assignment in self.assignments]
        if len(set(assignment_ids)) != len(assignment_ids):
            raise ValueError("signed authoring assignments must have unique ids")
        if sorted(assignment_ids) != sorted(self.candidate_ids):
            raise ValueError("signed authoring assignment ids drift")
        assignment_payload = [
            assignment.model_dump(mode="json")
            for assignment in sorted(
                self.assignments,
                key=lambda value: value.candidate_id,
            )
        ]
        if canonical_sha256(assignment_payload) != self.assignments_sha256:
            raise ValueError("signed authoring assignment hash drift")
        if not HEX_64.fullmatch(self.review_disposition_sha256):
            raise ValueError("inventory review disposition must be a SHA-256")
        assignments_by_id = {
            assignment.candidate_id: assignment for assignment in self.assignments
        }
        for assignment in self.assignments:
            if assignment.role != "reserve":
                continue
            target = assignments_by_id.get(assignment.reserve_target_id or "")
            if target is None or target.role != "primary":
                raise ValueError("reserve target must be a signed primary assignment")
            if (
                assignment.category,
                assignment.action_band,
                assignment.situation_slot,
                assignment.provenance_tier,
                assignment.taxonomy_anchors,
            ) != (
                target.category,
                target.action_band,
                target.situation_slot,
                target.provenance_tier,
                target.taxonomy_anchors,
            ):
                raise ValueError("reserve assignment must match its primary target")
        if self.frozen_at.tzinfo is None:
            raise ValueError("authoring inventory freeze must be timezone-aware")
        if len(set(self.signed_by)) != len(self.signed_by):
            raise ValueError("authoring inventory signers must be distinct")
        return self

    @property
    def sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


class FrozenExclusionManifest(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    forbidden_candidate_hashes_sha256: str
    forbidden_candidate_hash_count: int = Field(ge=1)
    forbidden_fingerprints_sha256: str
    forbidden_fingerprint_count: int = Field(ge=1)

    @model_validator(mode="after")
    def _hashes_are_valid(self) -> "FrozenExclusionManifest":
        if not HEX_64.fullmatch(
            self.forbidden_candidate_hashes_sha256
        ) or not HEX_64.fullmatch(self.forbidden_fingerprints_sha256):
            raise ValueError("exclusion-list hashes must be lowercase SHA-256")
        return self


class CandidatePoolFreezeManifestV13(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    status: Literal["WAITING_FUTURE_SEED"] = "WAITING_FUTURE_SEED"
    source_commit: str
    authoring_inventory_path: Literal[
        "v2/eval/m0/manifests/M0_V13_SIGNED_AUTHORING_INVENTORY.json"
    ]
    authored_candidates_path: Literal[
        "v2/eval/m0/candidates/M0_V13_AUTHORED_CANDIDATES.json"
    ]
    conformance_audit_path: Literal[
        "v2/eval/m0/audits/M0_V13_AUTOMATED_CONFORMANCE_AUDIT.json"
    ]
    candidate_registry_path: Literal[
        "v2/eval/m0/registries/M0_V13_CANDIDATE_REGISTRY_DRAFT.json"
    ]
    review_disposition_path: Literal[
        "v2/eval/m0/reviews/M0_V13_CANDIDATE_POOL_FREEZE_REVIEW.md"
    ]
    authoring_inventory_file_sha256: str
    authoring_inventory_manifest_sha256: str
    authored_candidates_file_sha256: str
    conformance_audit_file_sha256: str
    candidate_registry_file_sha256: str
    candidate_registry_sha256: str
    review_disposition_file_sha256: str
    eligible_pool_sha256: str
    authored_candidate_count: Literal[48]
    eligible_candidate_count: Literal[36]
    reserve_candidate_count: Literal[12]
    frozen_at: datetime
    future_seed_not_before: datetime
    signed_by: list[str] = Field(min_length=2)

    @model_validator(mode="after")
    def _freeze_identity(self) -> "CandidatePoolFreezeManifestV13":
        if not re.fullmatch(r"[0-9a-f]{40}", self.source_commit):
            raise ValueError("freeze source commit must be a lowercase Git SHA-1")
        for value in (
            self.authoring_inventory_file_sha256,
            self.authoring_inventory_manifest_sha256,
            self.authored_candidates_file_sha256,
            self.conformance_audit_file_sha256,
            self.candidate_registry_file_sha256,
            self.candidate_registry_sha256,
            self.review_disposition_file_sha256,
            self.eligible_pool_sha256,
        ):
            if not HEX_64.fullmatch(value):
                raise ValueError("freeze hashes must be lowercase SHA-256")
        if self.frozen_at.tzinfo is None or self.future_seed_not_before.tzinfo is None:
            raise ValueError("candidate-pool freeze timestamps must be timezone-aware")
        if self.future_seed_not_before != self.frozen_at + timedelta(hours=24):
            raise ValueError("future seed boundary must equal pool freeze plus 24 hours")
        if len(set(self.signed_by)) != len(self.signed_by):
            raise ValueError("candidate-pool freeze signers must be distinct")
        return self

    @property
    def sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


class BeaconPulse(StrictModel):
    chain_id: Literal[NIST_BEACON_V2_CHAIN] = NIST_BEACON_V2_CHAIN
    endpoint: Literal[NIST_BEACON_V2_ENDPOINT] = NIST_BEACON_V2_ENDPOINT
    pulse_timestamp: datetime
    output_value_hex: str
    signed_pulse_sha256: str
    signature_verified: Literal[True]

    @model_validator(mode="after")
    def _canonical_hex(self) -> "BeaconPulse":
        if not HEX_EVEN.fullmatch(self.output_value_hex):
            raise ValueError("beacon output must be non-empty lowercase even-length hex")
        if not HEX_64.fullmatch(self.signed_pulse_sha256):
            raise ValueError("signed pulse hash must be lowercase SHA-256")
        if self.pulse_timestamp.tzinfo is None:
            raise ValueError("pulse timestamp must be timezone-aware")
        return self

    @property
    def seed_bytes(self) -> bytes:
        return bytes.fromhex(self.output_value_hex)


class SelectedCandidate(StrictModel):
    candidate_id: str
    candidate_sha256: str
    category: str
    action_band: str
    split: Literal["visible", "holdout"]
    gate_world_id: str
    counter_world_ids: list[str]

    @model_validator(mode="after")
    def _selected_identity(self) -> "SelectedCandidate":
        if not HEX_64.fullmatch(self.candidate_sha256):
            raise ValueError("selected candidate hash must be lowercase SHA-256")
        if (
            not self.counter_world_ids
            or len(set(self.counter_world_ids)) != len(self.counter_world_ids)
        ):
            raise ValueError("selected candidate needs unique counter worlds")
        return self


class SelectionManifestV13(StrictModel):
    protocol_version: Literal["v1.3"] = "v1.3"
    pool_sha256: str
    registry_sha256: str
    authoring_inventory_manifest_sha256: str
    exclusion_manifest_sha256: str
    pool_frozen_at: datetime
    beacon: BeaconPulse
    selected: list[SelectedCandidate]

    @model_validator(mode="after")
    def _selection_identity(self) -> "SelectionManifestV13":
        for value in (
            self.pool_sha256,
            self.registry_sha256,
            self.authoring_inventory_manifest_sha256,
            self.exclusion_manifest_sha256,
        ):
            if not HEX_64.fullmatch(value):
                raise ValueError("selection hashes must be lowercase SHA-256")
        if self.pool_frozen_at.tzinfo is None:
            raise ValueError("selection freeze timestamp must be timezone-aware")
        return self


class SignedConformanceManifest(StrictModel):
    pool_sha256: str
    seed_sha256: str
    selection_sha256: str
    invalidation_history_path: str = Field(min_length=1)
    signed_by: list[str] = Field(min_length=2)
    allowed_invalidation_predicates: set[ConformancePredicate]

    @model_validator(mode="after")
    def _all_predicates_are_frozen(self) -> "SignedConformanceManifest":
        if self.allowed_invalidation_predicates != set(ConformancePredicate):
            raise ValueError("signed manifest must enumerate every frozen predicate")
        for value in (self.pool_sha256, self.seed_sha256, self.selection_sha256):
            if not HEX_64.fullmatch(value):
                raise ValueError("signed manifest hashes must be lowercase SHA-256")
        if len(set(self.signed_by)) != len(self.signed_by):
            raise ValueError("conformance signers must be distinct")
        if not Path(self.invalidation_history_path).is_absolute():
            raise ValueError("invalidation history path must be absolute")
        return self


class InvalidationDecision(StrEnum):
    REJECT_BINDING_SELECTION = "REJECT_BINDING_SELECTION"
    REFREEZE_ALLOWED = "REFREEZE_ALLOWED"
    TERMINATE_HYPOTHESIS = "TERMINATE_HYPOTHESIS"


class InvalidationIncident(StrictModel):
    predicate: str
    evidence_sha256: str | None
    prior_invalidation_count: int
    old_pool_sha256: str
    old_seed_sha256: str
    old_selection_sha256: str
    decision: InvalidationDecision

    @property
    def incident_sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


class InvalidationHistory(StrictModel):
    signed_manifest_sha256: str
    incidents: list[InvalidationIncident] = Field(default_factory=list)

    @model_validator(mode="after")
    def _incident_counts_are_append_only(self) -> "InvalidationHistory":
        if not HEX_64.fullmatch(self.signed_manifest_sha256):
            raise ValueError("invalidation history must bind a signed manifest hash")
        allowed_so_far = 0
        for incident in self.incidents:
            if incident.prior_invalidation_count != allowed_so_far:
                raise ValueError("invalidation history count drift")
            if incident.decision == InvalidationDecision.REFREEZE_ALLOWED:
                allowed_so_far += 1
        if allowed_so_far > 1:
            raise ValueError("invalidation history exceeds one refreeze")
        return self

    @property
    def history_sha256(self) -> str:
        return canonical_sha256(self.model_dump(mode="json"))


def initialize_invalidation_history(
    manifest: SignedConformanceManifest,
) -> InvalidationHistory:
    history_path = Path(manifest.invalidation_history_path)
    history = InvalidationHistory(
        signed_manifest_sha256=canonical_sha256(
            manifest.model_dump(mode="json")
        )
    )
    with history_path.open("x", encoding="utf-8") as stream:
        stream.write(history.model_dump_json(indent=2) + "\n")
    return history


def load_invalidation_history(path: str | Path) -> InvalidationHistory:
    return InvalidationHistory.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


def _persist_invalidation_history(
    path: str | Path,
    history: InvalidationHistory,
) -> None:
    history_path = Path(path)
    temporary = history_path.with_name(f".{history_path.name}.tmp")
    temporary.write_text(
        history.model_dump_json(indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(history_path)


def canonical_bytes(value: Any) -> bytes:
    try:
        return rfc8785.dumps(value)
    except (rfc8785.CanonicalizationError, TypeError) as error:
        raise ValueError(f"value is not RFC 8785 canonicalizable: {error}") from error


def canonical_sha256(value: Any) -> str:
    return hashlib.sha256(canonical_bytes(value)).hexdigest()


def candidate_sha256(candidate: CandidateV13) -> str:
    return canonical_sha256(candidate.model_dump(mode="json"))


def load_candidate_pool(path: str | Path) -> list[CandidateV13]:
    payload = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(payload, list):
        raise ValueError("v1.3 candidate pool must be a JSON list")
    return [CandidateV13.model_validate(value) for value in payload]


def validate_authored_candidate_inventory(
    candidates: list[CandidateV13],
    *,
    authoring_inventory: SignedAuthoringInventory,
) -> dict[str, str]:
    assignments_by_id = {
        assignment.candidate_id: assignment
        for assignment in authoring_inventory.assignments
    }
    if len(candidates) != len(assignments_by_id):
        raise ValueError("authored corpus does not fill the signed inventory")
    if len({candidate.candidate_id for candidate in candidates}) != len(candidates):
        raise ValueError("authored corpus contains duplicate candidate ids")
    if {candidate.candidate_id for candidate in candidates} != set(assignments_by_id):
        raise ValueError("authored corpus candidate ids drift from signed inventory")
    for field_name in (
        "underlying_event_fingerprint",
        "probe_template_fingerprint",
        "semantic_overlap_fingerprint",
    ):
        values = [getattr(candidate, field_name) for candidate in candidates]
        if len(set(values)) != len(values):
            raise ValueError(f"authored corpus duplicate {field_name}")

    hashes = {}
    by_id = {candidate.candidate_id: candidate for candidate in candidates}
    for candidate_id, assignment in assignments_by_id.items():
        candidate = by_id[candidate_id]
        if (
            candidate.category,
            candidate.action_band,
            candidate.situation_slot,
            candidate.provenance_tier,
            candidate.provenance_artifact.taxonomy_anchors,
        ) != (
            assignment.category,
            assignment.action_band,
            assignment.situation_slot,
            assignment.provenance_tier,
            assignment.taxonomy_anchors,
        ):
            raise ValueError("authored candidate assignment drift")
        if candidate.author_saw_v12_item_ledger:
            raise ValueError("authoring context exposed the v1.2 item ledger")
        hashes[candidate_id] = candidate_sha256(candidate)

    for assignment in authoring_inventory.assignments:
        if assignment.role != "reserve":
            continue
        reserve = by_id[assignment.candidate_id]
        target = by_id[assignment.reserve_target_id or ""]
        if (
            reserve.underlying_event_fingerprint
            == target.underlying_event_fingerprint
            or reserve.probe_template_fingerprint
            == target.probe_template_fingerprint
            or reserve.semantic_overlap_fingerprint
            == target.semantic_overlap_fingerprint
        ):
            raise ValueError("reserve candidate is not distinct from its target")

    deletion_candidates = [
        candidate for candidate in candidates if candidate.category == "deletion"
    ]
    if not deletion_candidates or not all(
        {"summary", "wiki"} <= set(candidate.deletion_storage_locations)
        for candidate in deletion_candidates
    ):
        raise ValueError(
            "authored deletion inventory does not guarantee derived-memory deletion"
        )
    return dict(sorted(hashes.items()))


def load_candidate_registry(path: str | Path) -> CandidateRegistryV13:
    return CandidateRegistryV13.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


def load_authoring_inventory(path: str | Path) -> SignedAuthoringInventory:
    return SignedAuthoringInventory.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


def load_exclusion_manifest(path: str | Path) -> FrozenExclusionManifest:
    return FrozenExclusionManifest.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


def load_candidate_pool_freeze_manifest(
    path: str | Path,
) -> CandidatePoolFreezeManifestV13:
    return CandidatePoolFreezeManifestV13.model_validate_json(
        Path(path).read_text(encoding="utf-8")
    )


def load_beacon_pulse(path: str | Path) -> BeaconPulse:
    return BeaconPulse.model_validate_json(Path(path).read_text(encoding="utf-8"))


def validate_candidate_pool(
    candidates: list[CandidateV13],
    *,
    forbidden_candidate_hashes: set[str] | None = None,
    forbidden_overlap_fingerprints: set[str] | None = None,
) -> dict[str, str]:
    if len(candidates) != 36:
        raise ValueError(
            "frozen matrix requires exactly 36 eligible candidates, "
            f"got {len(candidates)}"
        )
    if len({candidate.candidate_id for candidate in candidates}) != len(candidates):
        raise ValueError("duplicate candidate_id")
    for field_name in (
        "underlying_event_fingerprint",
        "probe_template_fingerprint",
        "semantic_overlap_fingerprint",
    ):
        values = [getattr(candidate, field_name) for candidate in candidates]
        if len(set(values)) != len(values):
            raise ValueError(f"duplicate/near-duplicate {field_name}")

    grouped: dict[tuple[str, str], list[CandidateV13]] = defaultdict(list)
    for candidate in candidates:
        grouped[(candidate.category, candidate.action_band)].append(candidate)
    if set(grouped) != set(MATRIX_BY_KEY):
        raise ValueError("candidate rows do not match the frozen matrix")
    for key, row in MATRIX_BY_KEY.items():
        values = grouped[key]
        if len(values) != 3:
            raise ValueError(f"{key}: expected exactly three eligible candidates")
        if {candidate.situation_slot for candidate in values} != set(row.slots):
            raise ValueError(f"{key}: situation/template slot drift")
        provenance = Counter(candidate.provenance_tier for candidate in values)
        if provenance["E"] < row.selected_external:
            raise ValueError(f"{key}: insufficient external-provenance candidates")
        if provenance["P"] < row.selected_product:
            raise ValueError(f"{key}: insufficient product-extension candidates")

    hashes = {
        candidate.candidate_id: candidate_sha256(candidate)
        for candidate in sorted(candidates, key=lambda value: value.candidate_id)
    }
    overlap_hashes = set(hashes.values()) & set(forbidden_candidate_hashes or ())
    if overlap_hashes:
        raise ValueError("candidate hash overlaps the frozen v1.2 corpus")
    overlap_fingerprints = {
        candidate.semantic_overlap_fingerprint for candidate in candidates
    } & set(forbidden_overlap_fingerprints or ())
    if overlap_fingerprints:
        raise ValueError("candidate semantic fingerprint overlaps a frozen item")
    return hashes


def validate_candidate_registry(
    registry: CandidateRegistryV13,
    *,
    authoring_inventory: SignedAuthoringInventory,
    authored_candidates: list[CandidateV13] | None = None,
    forbidden_candidate_hashes: set[str] | None = None,
    forbidden_overlap_fingerprints: set[str] | None = None,
) -> dict[str, str]:
    hashes = validate_candidate_pool(
        registry.candidates,
        forbidden_candidate_hashes=forbidden_candidate_hashes,
        forbidden_overlap_fingerprints=forbidden_overlap_fingerprints,
    )
    if len({record.candidate_id for record in registry.records}) != len(
        registry.records
    ):
        raise ValueError("candidate registry contains duplicate audit ids")
    inventory_ids = sorted(registry.authoring_inventory_ids)
    if (
        inventory_ids != sorted(authoring_inventory.candidate_ids)
        or registry.authoring_inventory_sha256
        != authoring_inventory.candidate_ids_sha256
        or registry.authoring_inventory_assignments_sha256
        != authoring_inventory.assignments_sha256
    ):
        raise ValueError(
            "registry does not match independently signed authoring inventory"
        )
    if canonical_sha256(inventory_ids) != registry.authoring_inventory_sha256:
        raise ValueError("authoring inventory hash drift")
    if set(inventory_ids) != {record.candidate_id for record in registry.records}:
        raise ValueError("registry records do not match precommitted inventory")
    assignments_by_id = {
        assignment.candidate_id: assignment
        for assignment in authoring_inventory.assignments
    }
    for record in registry.records:
        assignment = assignments_by_id[record.candidate_id]
        if (
            record.inventory_role,
            record.category,
            record.action_band,
            record.situation_slot,
            record.provenance_tier,
            record.taxonomy_anchors,
            record.reserve_target_id,
        ) != (
            assignment.role,
            assignment.category,
            assignment.action_band,
            assignment.situation_slot,
            assignment.provenance_tier,
            assignment.taxonomy_anchors,
            assignment.reserve_target_id,
        ):
            raise ValueError(
                "registry assignment does not match signed authoring inventory"
            )
    if authored_candidates is not None:
        authored_hashes = validate_authored_candidate_inventory(
            authored_candidates,
            authoring_inventory=authoring_inventory,
        )
        for record in registry.records:
            if record.content_sha256 != authored_hashes[record.candidate_id]:
                raise ValueError("registry authored-candidate content hash drift")
    eligible = {
        record.candidate_id: record
        for record in registry.records
        if record.status == "eligible"
    }
    if set(eligible) != set(hashes):
        raise ValueError("eligible registry records do not match the 36-candidate pool")
    for candidate_id, candidate_hash in hashes.items():
        record = eligible[candidate_id]
        candidate = next(
            value
            for value in registry.candidates
            if value.candidate_id == candidate_id
        )
        if record.content_sha256 != candidate_hash:
            raise ValueError("eligible registry content hash drift")
        if (record.category, record.action_band) != (
            candidate.category,
            candidate.action_band,
        ):
            raise ValueError("eligible registry matrix metadata drift")
        if (
            record.situation_slot != candidate.situation_slot
            or record.provenance_tier != candidate.provenance_tier
            or record.taxonomy_anchors
            != candidate.provenance_artifact.taxonomy_anchors
        ):
            raise ValueError("eligible registry assignment metadata drift")
        if (
            record.authoring_task_id != candidate.authoring_task_id
            or record.authoring_prompt_sha256 != candidate.authoring_prompt_sha256
            or record.visible_materials_sha256
            != candidate.visible_materials_sha256
            or record.provenance_reference != candidate.provenance_reference
            or record.author_saw_v12_item_ledger
            != candidate.author_saw_v12_item_ledger
        ):
            raise ValueError("eligible registry audit metadata drift")
    records_by_id = {record.candidate_id: record for record in registry.records}
    for record in registry.records:
        if record.status == "replaced":
            if record.replaced_by not in eligible:
                raise ValueError("replacement target must be an eligible candidate")
            replacement_assignment = assignments_by_id[record.replaced_by or ""]
            if (
                replacement_assignment.role != "reserve"
                or replacement_assignment.reserve_target_id != record.candidate_id
            ):
                raise ValueError(
                    "replacement must use the signed reserve for that primary"
                )
        if record.inventory_role == "reserve" and record.status == "eligible":
            target = records_by_id[record.reserve_target_id or ""]
            if (
                target.status != "replaced"
                or target.replaced_by != record.candidate_id
            ):
                raise ValueError(
                    "eligible reserve must replace its signed primary target"
                )
    return hashes


def registry_sha256(registry: CandidateRegistryV13) -> str:
    payload = registry.model_dump(mode="json")
    payload["authoring_inventory_ids"] = sorted(payload["authoring_inventory_ids"])
    payload["candidates"] = sorted(
        payload["candidates"], key=lambda value: value["candidate_id"]
    )
    payload["records"] = sorted(
        payload["records"], key=lambda value: value["candidate_id"]
    )
    return canonical_sha256(payload)


def exclusion_list_sha256(values: set[str]) -> str:
    return canonical_sha256(sorted(values))


def validate_exclusion_manifest(
    manifest: FrozenExclusionManifest,
    *,
    forbidden_candidate_hashes: set[str],
    forbidden_overlap_fingerprints: set[str],
) -> None:
    if not forbidden_candidate_hashes or not forbidden_overlap_fingerprints:
        raise ValueError("frozen exclusion lists must be non-empty")
    if (
        len(forbidden_candidate_hashes) != manifest.forbidden_candidate_hash_count
        or exclusion_list_sha256(forbidden_candidate_hashes)
        != manifest.forbidden_candidate_hashes_sha256
    ):
        raise ValueError("forbidden candidate-hash list does not match frozen identity")
    if (
        len(forbidden_overlap_fingerprints)
        != manifest.forbidden_fingerprint_count
        or exclusion_list_sha256(forbidden_overlap_fingerprints)
        != manifest.forbidden_fingerprints_sha256
    ):
        raise ValueError("forbidden fingerprint list does not match frozen identity")


def exclusion_manifest_sha256(manifest: FrozenExclusionManifest) -> str:
    return canonical_sha256(manifest.model_dump(mode="json"))


def pool_sha256(candidate_hashes: dict[str, str]) -> str:
    return canonical_sha256(
        [
            {"candidate_id": candidate_id, "sha256": value}
            for candidate_id, value in sorted(candidate_hashes.items())
        ]
    )


def _rank(seed: bytes, domain: str, candidate_hash: str, suffix: str = "") -> bytes:
    return hashlib.sha256(
        seed + domain.encode("utf-8") + bytes.fromhex(candidate_hash) + suffix.encode("utf-8")
    ).digest()


def select_candidate_pool(
    registry: CandidateRegistryV13,
    *,
    authoring_inventory: SignedAuthoringInventory,
    exclusion_manifest: FrozenExclusionManifest,
    forbidden_candidate_hashes: set[str],
    forbidden_overlap_fingerprints: set[str],
    pool_frozen_at: datetime,
    beacon: BeaconPulse,
) -> SelectionManifestV13:
    if pool_frozen_at.tzinfo is None:
        raise ValueError("pool freeze timestamp must be timezone-aware")
    if beacon.pulse_timestamp < pool_frozen_at + timedelta(hours=24):
        raise ValueError("beacon pulse must be at least 24 hours after pool freeze")
    validate_exclusion_manifest(
        exclusion_manifest,
        forbidden_candidate_hashes=forbidden_candidate_hashes,
        forbidden_overlap_fingerprints=forbidden_overlap_fingerprints,
    )
    hashes = validate_candidate_registry(
        registry,
        authoring_inventory=authoring_inventory,
        forbidden_candidate_hashes=forbidden_candidate_hashes,
        forbidden_overlap_fingerprints=forbidden_overlap_fingerprints,
    )
    candidates = registry.candidates
    by_key: dict[tuple[str, str], list[CandidateV13]] = defaultdict(list)
    by_id = {candidate.candidate_id: candidate for candidate in candidates}
    for candidate in candidates:
        by_key[(candidate.category, candidate.action_band)].append(candidate)

    selected_ids: list[str] = []
    for key, row in MATRIX_BY_KEY.items():
        values = by_key[key]
        for provenance, quota in (
            ("E", row.selected_external),
            ("P", row.selected_product),
        ):
            ranked = sorted(
                (
                    candidate
                    for candidate in values
                    if candidate.provenance_tier == provenance
                ),
                key=lambda candidate: _rank(
                    beacon.seed_bytes,
                    "|select|",
                    hashes[candidate.candidate_id],
                ),
            )
            if len(ranked) < quota:
                raise ValueError(f"{key}: selection provenance quota cannot be met")
            selected_ids.extend(candidate.candidate_id for candidate in ranked[:quota])

    if len(selected_ids) != 24 or len(set(selected_ids)) != 24:
        raise ValueError("selection must produce 24 unique candidates")

    split_by_id: dict[str, Literal["visible", "holdout"]] = {}
    for category, holdout_count in HOLDOUT_COUNTS.items():
        ranked = sorted(
            (
                candidate_id
                for candidate_id in selected_ids
                if by_id[candidate_id].category == category
            ),
            key=lambda candidate_id: _rank(
                beacon.seed_bytes, "|split|", hashes[candidate_id]
            ),
        )
        for candidate_id in ranked:
            split_by_id[candidate_id] = (
                "holdout" if candidate_id in ranked[:holdout_count] else "visible"
            )

    gate_by_id: dict[str, str] = {}
    for key, row in MATRIX_BY_KEY.items():
        row_ids = [candidate_id for candidate_id in selected_ids if (
            by_id[candidate_id].category,
            by_id[candidate_id].action_band,
        ) == key]
        if row.action_pair:
            ranked = sorted(
                row_ids,
                key=lambda candidate_id: _rank(
                    beacon.seed_bytes, "|gate-world|", hashes[candidate_id]
                ),
            )
            actions = sorted(row.action_pair, key=lambda action: action.value)
            for candidate_id, desired_action in zip(ranked, actions, strict=True):
                candidate = by_id[candidate_id]
                gate_by_id[candidate_id] = next(
                    world.world_id
                    for world in candidate.worlds
                    if world.gold_response_action == desired_action
                )
        elif row.category == "deletion":
            gate_by_id.update({candidate_id: "post_delete" for candidate_id in row_ids})
        else:
            for candidate_id in row_ids:
                candidate = by_id[candidate_id]
                gate_by_id[candidate_id] = min(
                    (world.world_id for world in candidate.worlds),
                    key=lambda world_id: _rank(
                        beacon.seed_bytes,
                        "|gate-world|",
                        hashes[candidate_id],
                        world_id,
                    ),
                )

    entries = []
    for candidate_id in selected_ids:
        candidate = by_id[candidate_id]
        gate_world_id = gate_by_id[candidate_id]
        entries.append(
            SelectedCandidate(
                candidate_id=candidate_id,
                candidate_sha256=hashes[candidate_id],
                category=candidate.category,
                action_band=candidate.action_band,
                split=split_by_id[candidate_id],
                gate_world_id=gate_world_id,
                counter_world_ids=[
                    world.world_id
                    for world in candidate.worlds
                    if world.world_id != gate_world_id
                ],
            )
        )
    entries.sort(key=lambda entry: entry.candidate_id)
    manifest = SelectionManifestV13(
        pool_sha256=pool_sha256(hashes),
        registry_sha256=registry_sha256(registry),
        authoring_inventory_manifest_sha256=authoring_inventory.sha256,
        exclusion_manifest_sha256=exclusion_manifest_sha256(
            exclusion_manifest
        ),
        pool_frozen_at=pool_frozen_at,
        beacon=beacon,
        selected=entries,
    )
    validate_selection_manifest(manifest, by_id)
    return manifest


def validate_selection_manifest(
    manifest: SelectionManifestV13,
    by_id: dict[str, CandidateV13],
) -> None:
    candidate_ids = [entry.candidate_id for entry in manifest.selected]
    if len(candidate_ids) != len(set(candidate_ids)):
        raise ValueError("selected candidate ids must be unique")
    if any(candidate_id not in by_id for candidate_id in candidate_ids):
        raise ValueError("selection references an unavailable candidate")
    if Counter(entry.category for entry in manifest.selected) != EXPECTED_CATEGORY_COUNTS:
        raise ValueError("selected category distribution drift")
    if Counter(entry.split for entry in manifest.selected) != {
        "visible": 16,
        "holdout": 8,
    }:
        raise ValueError("selected split distribution drift")
    for entry in manifest.selected:
        world_ids = {world.world_id for world in by_id[entry.candidate_id].worlds}
        if {entry.gate_world_id, *entry.counter_world_ids} != world_ids:
            raise ValueError("gate/counter worlds do not cover the candidate")
        if entry.gate_world_id in entry.counter_world_ids:
            raise ValueError("gate world leaked into counter worlds")
    for row in MATRIX_ROWS:
        if not row.action_pair:
            continue
        actions = []
        for entry in manifest.selected:
            if (entry.category, entry.action_band) != (row.category, row.action_band):
                continue
            candidate = by_id[entry.candidate_id]
            actions.append(
                next(
                    world.gold_response_action
                    for world in candidate.worlds
                    if world.world_id == entry.gate_world_id
                )
            )
        if Counter(actions) != Counter(row.action_pair):
            raise ValueError("required-memory gate gold is not exactly balanced")


def selection_manifest_sha256(manifest: SelectionManifestV13) -> str:
    return canonical_sha256(manifest.model_dump(mode="json"))


def signed_conformance_manifest_sha256(
    manifest: SignedConformanceManifest,
) -> str:
    return canonical_sha256(manifest.model_dump(mode="json"))


def validate_authoritative_selection(
    selection: SelectionManifestV13,
    signed_manifest: SignedConformanceManifest,
) -> None:
    if signed_manifest.pool_sha256 != selection.pool_sha256:
        raise ValueError("signed conformance pool identity drift")
    if signed_manifest.selection_sha256 != selection_manifest_sha256(selection):
        raise ValueError("selection differs from signed authoritative selection")
    expected_seed_sha256 = hashlib.sha256(selection.beacon.seed_bytes).hexdigest()
    if signed_manifest.seed_sha256 != expected_seed_sha256:
        raise ValueError("signed conformance beacon seed identity drift")


def public_selection_manifest(manifest: SelectionManifestV13) -> dict[str, Any]:
    visible = [
        entry.model_dump(mode="json")
        for entry in manifest.selected
        if entry.split == "visible"
    ]
    holdout_hashes = sorted(
        entry.candidate_sha256
        for entry in manifest.selected
        if entry.split == "holdout"
    )
    return {
        "protocol_version": manifest.protocol_version,
        "pool_sha256": manifest.pool_sha256,
        "selection_sha256": selection_manifest_sha256(manifest),
        "visible": visible,
        "holdout": {
            "count": len(holdout_hashes),
            "aggregate_sha256": canonical_sha256(holdout_hashes),
        },
    }


def request_post_split_invalidation(
    manifest: SignedConformanceManifest,
    *,
    predicate: str,
    evidence_sha256: str | None,
) -> InvalidationIncident:
    history_path = Path(manifest.invalidation_history_path)
    history = load_invalidation_history(history_path)
    manifest_sha = canonical_sha256(manifest.model_dump(mode="json"))
    if history.signed_manifest_sha256 != manifest_sha:
        raise ValueError("invalidation history is bound to another signed manifest")
    if any(
        incident.old_pool_sha256 != manifest.pool_sha256
        or incident.old_seed_sha256 != manifest.seed_sha256
        or incident.old_selection_sha256 != manifest.selection_sha256
        for incident in history.incidents
    ):
        raise ValueError("invalidation history incident identity drift")
    prior_allowed = sum(
        incident.decision == InvalidationDecision.REFREEZE_ALLOWED
        for incident in history.incidents
    )
    if prior_allowed >= 1:
        decision = InvalidationDecision.TERMINATE_HYPOTHESIS
    else:
        try:
            frozen_predicate = ConformancePredicate(predicate)
        except ValueError:
            decision = InvalidationDecision.REJECT_BINDING_SELECTION
        else:
            if (
                frozen_predicate not in manifest.allowed_invalidation_predicates
                or evidence_sha256 is None
                or not HEX_64.fullmatch(evidence_sha256)
            ):
                decision = InvalidationDecision.REJECT_BINDING_SELECTION
            else:
                decision = InvalidationDecision.REFREEZE_ALLOWED
    incident = InvalidationIncident(
        predicate=predicate,
        evidence_sha256=evidence_sha256,
        prior_invalidation_count=prior_allowed,
        old_pool_sha256=manifest.pool_sha256,
        old_seed_sha256=manifest.seed_sha256,
        old_selection_sha256=manifest.selection_sha256,
        decision=decision,
    )
    history.incidents.append(incident)
    _persist_invalidation_history(history_path, history)
    return incident


def normalize_lexicon_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return " ".join(normalized.split())


def classify_rendering(text: str, claim: TrackedClaim) -> ObservedRendering:
    normalized = normalize_lexicon_text(text)
    surfaces = [normalize_lexicon_text(value) for value in claim.surface_forms]
    if not any(surface in normalized for surface in surfaces):
        return ObservedRendering.NOT_DETECTED_UNDER_FROZEN_LEXICON
    active = any(
        normalize_lexicon_text(cue) in normalized for cue in claim.active_cues
    )
    historical = any(
        normalize_lexicon_text(cue) in normalized for cue in claim.historical_cues
    )
    if active and not historical:
        return ObservedRendering.ACTIVE_ASSERTION
    if historical and not active:
        return ObservedRendering.HISTORICAL_OR_NEGATED
    return ObservedRendering.AMBIGUOUS
