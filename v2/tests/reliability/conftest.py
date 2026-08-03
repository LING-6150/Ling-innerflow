from __future__ import annotations

import hashlib
from datetime import datetime, timedelta, timezone

import pytest

from innerflow_v2.reliability.protocol_v13 import (
    MATRIX_ROWS,
    AuthoringInventoryAssignment,
    BeaconPulse,
    CandidateProvenanceV13,
    CandidateRegistryRecordV13,
    CandidateRegistryV13,
    CandidateV13,
    ConformancePredicate,
    CounterfactualWorld,
    FrozenExclusionManifest,
    GoldApplicability,
    ResponseAction,
    SetupMemoryEventV13,
    SignedAuthoringInventory,
    SignedConformanceManifest,
    SymmetryCertificate,
    TrackedClaim,
    candidate_sha256,
    canonical_sha256,
    exclusion_list_sha256,
    select_candidate_pool,
    selection_manifest_sha256,
)


def sha(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


@pytest.fixture
def candidate_pool_v13() -> list[CandidateV13]:
    candidates = []
    for row in MATRIX_ROWS:
        for index, slot in enumerate(row.slots):
            candidate_id = f"{row.category}-{row.action_band}-{index}"
            if row.selected_product and index == 2:
                provenance = "P"
            elif row.selected_external == 0:
                provenance = "P"
            else:
                provenance = "E"
            non_memory_state = {
                "situation_slot": slot,
                "current_request": f"request-{candidate_id}",
            }
            if row.action_pair:
                operation = {
                    "correction": "correct",
                    "supersession": "supersede",
                    "context-exception": "scope",
                }[row.category]
                common_event = SetupMemoryEventV13(
                    event_id=f"event-{candidate_id}-context",
                    sequence_index=0,
                    role="user",
                    operation="observe",
                    content=f"Neutral setup for {candidate_id}.",
                )
                worlds = [
                    CounterfactualWorld(
                        world_id="world_a",
                        probe=f"Choose the response action for {candidate_id}.",
                        non_memory_state=non_memory_state,
                        setup_memory_events=[
                            common_event,
                            SetupMemoryEventV13(
                                event_id=f"event-{candidate_id}-effective",
                                sequence_index=1,
                                role="user",
                                operation=operation,
                                content=f"{candidate_id}-claim-a",
                                claim_ids=[f"claim-{candidate_id}-a"],
                            ),
                        ],
                        effective_claim=f"{candidate_id}-claim-a",
                        applicable_claim_id=f"claim-{candidate_id}-a",
                        gold_response_action=row.action_pair[0],
                    ),
                    CounterfactualWorld(
                        world_id="world_b",
                        probe=f"Choose the response action for {candidate_id}.",
                        non_memory_state=non_memory_state,
                        setup_memory_events=[
                            common_event,
                            SetupMemoryEventV13(
                                event_id=f"event-{candidate_id}-effective",
                                sequence_index=1,
                                role="user",
                                operation=operation,
                                content=f"{candidate_id}-claim-b",
                                claim_ids=[f"claim-{candidate_id}-b"],
                            ),
                        ],
                        effective_claim=f"{candidate_id}-claim-b",
                        applicable_claim_id=f"claim-{candidate_id}-b",
                        gold_response_action=row.action_pair[1],
                    ),
                ]
                tracked_claims = [
                    TrackedClaim(
                        claim_id=f"claim-{candidate_id}-a",
                        canonical_value=f"{candidate_id}-claim-a",
                        source_event_ids=[f"event-{candidate_id}-effective"],
                        gold_applicability=GoldApplicability.CURRENT_EFFECTIVE,
                        surface_forms=[f"surface {candidate_id} a"],
                        active_cues=["currently"],
                        historical_cues=["used to"],
                    ),
                    TrackedClaim(
                        claim_id=f"claim-{candidate_id}-b",
                        canonical_value=f"{candidate_id}-claim-b",
                        source_event_ids=[f"event-{candidate_id}-effective"],
                        gold_applicability=GoldApplicability.CURRENT_EFFECTIVE,
                        surface_forms=[f"surface {candidate_id} b"],
                        active_cues=["currently"],
                        historical_cues=["used to"],
                    ),
                ]
                certificate = SymmetryCertificate(
                    controlled_claim=f"{candidate_id}-controlled",
                    action_a_binding=row.action_pair[0].value,
                    action_b_binding=row.action_pair[1].value,
                    both_plausible_and_safe_reason="both actions are safe in this neutral task",
                    no_non_memory_preference_reason="the probe contains no action clue",
                    reviewer_id="reviewer-agent",
                    disposition="pass",
                )
            elif row.category == "no-memory":
                worlds = [
                    CounterfactualWorld(
                        world_id="variant_a",
                        probe=f"Choose the response action for {candidate_id}.",
                        non_memory_state=non_memory_state,
                        setup_memory_events=[
                            SetupMemoryEventV13(
                                event_id=f"event-{candidate_id}-irrelevant",
                                sequence_index=0,
                                role="user",
                                operation="observe",
                                content=f"{candidate_id}-irrelevant-a",
                                claim_ids=[f"claim-{candidate_id}-a"],
                            )
                        ],
                        effective_claim=f"{candidate_id}-irrelevant-a",
                        applicable_claim_id=None,
                        gold_response_action=ResponseAction.USE_UNPERSONALIZED_DEFAULT,
                    ),
                    CounterfactualWorld(
                        world_id="variant_b",
                        probe=f"Choose the response action for {candidate_id}.",
                        non_memory_state=non_memory_state,
                        setup_memory_events=[
                            SetupMemoryEventV13(
                                event_id=f"event-{candidate_id}-irrelevant",
                                sequence_index=0,
                                role="user",
                                operation="observe",
                                content=f"{candidate_id}-irrelevant-b",
                                claim_ids=[f"claim-{candidate_id}-b"],
                            )
                        ],
                        effective_claim=f"{candidate_id}-irrelevant-b",
                        applicable_claim_id=None,
                        gold_response_action=ResponseAction.USE_UNPERSONALIZED_DEFAULT,
                    ),
                ]
                tracked_claims = [
                    TrackedClaim(
                        claim_id=f"claim-{candidate_id}-a",
                        canonical_value=f"{candidate_id}-irrelevant-a",
                        source_event_ids=[f"event-{candidate_id}-irrelevant"],
                        gold_applicability=GoldApplicability.IRRELEVANT,
                        surface_forms=[f"surface {candidate_id} a"],
                    ),
                    TrackedClaim(
                        claim_id=f"claim-{candidate_id}-b",
                        canonical_value=f"{candidate_id}-irrelevant-b",
                        source_event_ids=[f"event-{candidate_id}-irrelevant"],
                        gold_applicability=GoldApplicability.IRRELEVANT,
                        surface_forms=[f"surface {candidate_id} b"],
                    ),
                ]
                certificate = None
            else:
                assert row.deletion_pre_actions is not None
                stored_event = SetupMemoryEventV13(
                    event_id=f"event-{candidate_id}",
                    sequence_index=0,
                    role="user",
                    operation="observe",
                    content=f"{candidate_id}-stored",
                    claim_ids=[f"claim-{candidate_id}"],
                )
                worlds = [
                    CounterfactualWorld(
                        world_id="pre_delete",
                        probe=f"Choose the response action for {candidate_id}.",
                        non_memory_state=non_memory_state,
                        setup_memory_events=[stored_event],
                        effective_claim=f"{candidate_id}-stored",
                        applicable_claim_id=f"claim-{candidate_id}",
                        gold_response_action=row.deletion_pre_actions[index % 2],
                    ),
                    CounterfactualWorld(
                        world_id="post_delete",
                        probe=f"Choose the response action for {candidate_id}.",
                        non_memory_state=non_memory_state,
                        setup_memory_events=[
                            stored_event,
                            SetupMemoryEventV13(
                                event_id=f"event-{candidate_id}-delete",
                                sequence_index=1,
                                role="user",
                                operation="delete",
                                content=f"Forget {candidate_id}-stored.",
                                delete_target_claim_id=f"claim-{candidate_id}",
                            ),
                        ],
                        effective_claim=None,
                        applicable_claim_id=None,
                        gold_response_action=ResponseAction.USE_UNPERSONALIZED_DEFAULT,
                    ),
                ]
                tracked_claims = [
                    TrackedClaim(
                        claim_id=f"claim-{candidate_id}",
                        canonical_value=f"{candidate_id}-stored",
                        source_event_ids=[f"event-{candidate_id}"],
                        gold_applicability=GoldApplicability.DELETED,
                        surface_forms=[f"surface {candidate_id}"],
                        active_cues=["currently"],
                        historical_cues=["used to"],
                    )
                ]
                certificate = None
            candidates.append(
                CandidateV13(
                    candidate_id=candidate_id,
                    category=row.category,
                    action_band=row.action_band,
                    situation_slot=slot,
                    provenance_tier=provenance,
                    provenance_reference=f"source:{candidate_id}",
                    provenance_artifact=CandidateProvenanceV13(
                        origin_type=(
                            "construction_pattern"
                            if provenance == "E"
                            else "product_extension"
                        ),
                        source_reference=f"source:{candidate_id}",
                        taxonomy_anchors=[
                            (
                                "external-taxonomy"
                                if provenance == "E"
                                else "innerflow-product-invariant"
                            )
                        ],
                        transformation_log="Synthetic conformance fixture.",
                        author="test-author",
                        reviewer="test-reviewer",
                        unambiguous_gold_reason="The controlled claim binds the action.",
                        leakage_note="No v1.2 item content used.",
                    ),
                    underlying_event_fingerprint=sha(f"event:{candidate_id}"),
                    probe_template_fingerprint=sha(f"template:{candidate_id}"),
                    semantic_overlap_fingerprint=sha(f"semantic:{candidate_id}"),
                    authoring_task_id=f"task-{candidate_id}",
                    authoring_prompt_sha256=sha(f"prompt:{candidate_id}"),
                    visible_materials_sha256=sha(f"materials:{candidate_id}"),
                    worlds=worlds,
                    tracked_claims=tracked_claims,
                    symmetry_certificate=certificate,
                    deletion_storage_locations=(
                        ["summary", "wiki"]
                        if row.category == "deletion" and index == 0
                        else ["raw"]
                        if row.category == "deletion"
                        else []
                    ),
                )
            )
    return candidates


@pytest.fixture
def frozen_at_v13() -> datetime:
    return datetime(2026, 8, 1, 12, 0, tzinfo=timezone.utc)


@pytest.fixture
def beacon_v13(frozen_at_v13: datetime) -> BeaconPulse:
    return BeaconPulse(
        pulse_timestamp=frozen_at_v13 + timedelta(hours=24),
        output_value_hex="ab" * 64,
        signed_pulse_sha256=sha("signed-pulse"),
        signature_verified=True,
    )


@pytest.fixture
def forbidden_hashes_v13() -> set[str]:
    return {sha("v1.2-candidate-hash")}


@pytest.fixture
def forbidden_fingerprints_v13() -> set[str]:
    return {sha("v1.2-semantic-fingerprint")}


@pytest.fixture
def exclusion_manifest_v13(
    forbidden_hashes_v13,
    forbidden_fingerprints_v13,
) -> FrozenExclusionManifest:
    return FrozenExclusionManifest(
        source_fixture_sha256=sha("v1.2-fixture"),
        source_candidate_registry_sha256=sha("v1.2-registry"),
        source_freeze_manifest_sha256=sha("v1.2-freeze"),
        forbidden_candidate_hashes_sha256=exclusion_list_sha256(
            forbidden_hashes_v13
        ),
        forbidden_candidate_hash_count=len(forbidden_hashes_v13),
        forbidden_fingerprints_sha256=exclusion_list_sha256(
            forbidden_fingerprints_v13
        ),
        forbidden_fingerprint_count=len(forbidden_fingerprints_v13),
    )


@pytest.fixture
def authoring_inventory_v13(
    candidate_pool_v13,
    frozen_at_v13,
) -> SignedAuthoringInventory:
    assignments = [
        AuthoringInventoryAssignment(
            candidate_id=candidate.candidate_id,
            role="primary",
            category=candidate.category,
            action_band=candidate.action_band,
            situation_slot=candidate.situation_slot,
            provenance_tier=candidate.provenance_tier,
            taxonomy_anchors=[
                (
                    "external-taxonomy"
                    if candidate.provenance_tier == "E"
                    else "innerflow-product-invariant"
                )
            ],
        )
        for candidate in candidate_pool_v13
    ]
    reserve_target = candidate_pool_v13[0]
    assignments.append(
        AuthoringInventoryAssignment(
            candidate_id="rejected-audit-record",
            role="reserve",
            category=reserve_target.category,
            action_band=reserve_target.action_band,
            situation_slot=reserve_target.situation_slot,
            provenance_tier=reserve_target.provenance_tier,
            taxonomy_anchors=["external-taxonomy"],
            reserve_target_id=reserve_target.candidate_id,
        )
    )
    assignments = sorted(assignments, key=lambda value: value.candidate_id)
    inventory = [assignment.candidate_id for assignment in assignments]
    return SignedAuthoringInventory(
        candidate_ids=inventory,
        candidate_ids_sha256=canonical_sha256(inventory),
        assignments=assignments,
        assignments_sha256=canonical_sha256(
            [
                assignment.model_dump(mode="json")
                for assignment in assignments
            ]
        ),
        review_disposition_sha256=sha("inventory-freeze-review"),
        frozen_at=frozen_at_v13,
        signed_by=["owner", "independent-reviewer"],
    )


@pytest.fixture
def registry_v13(
    candidate_pool_v13,
    authoring_inventory_v13,
) -> CandidateRegistryV13:
    records = [
        CandidateRegistryRecordV13(
            candidate_id=candidate.candidate_id,
            inventory_role="primary",
            status="eligible",
            category=candidate.category,
            action_band=candidate.action_band,
            situation_slot=candidate.situation_slot,
            provenance_tier=candidate.provenance_tier,
            taxonomy_anchors=[
                (
                    "external-taxonomy"
                    if candidate.provenance_tier == "E"
                    else "innerflow-product-invariant"
                )
            ],
            content_sha256=candidate_sha256(candidate),
            reason="passed every frozen eligibility predicate",
            audit_artifact_sha256=sha(f"audit:{candidate.candidate_id}"),
            reviewer_id="pool-reviewer",
            reviewer_disposition="pass",
            authoring_task_id=candidate.authoring_task_id,
            authoring_prompt_sha256=candidate.authoring_prompt_sha256,
            visible_materials_sha256=candidate.visible_materials_sha256,
            provenance_reference=candidate.provenance_reference,
            counterfactual_dependency_result=(
                "pass"
                if candidate.category
                in {"correction", "supersession", "context-exception"}
                else "not_applicable"
            ),
            ontology_compatible=True,
            claim_state_review="pass",
            author_saw_v12_item_ledger=False,
        )
        for candidate in candidate_pool_v13
    ]
    records.append(
        CandidateRegistryRecordV13(
            candidate_id="rejected-audit-record",
            inventory_role="reserve",
            status="reserve",
            category="correction",
            action_band="detail",
            situation_slot=candidate_pool_v13[0].situation_slot,
            provenance_tier=candidate_pool_v13[0].provenance_tier,
            taxonomy_anchors=["external-taxonomy"],
            reserve_target_id=candidate_pool_v13[0].candidate_id,
            content_sha256=canonical_sha256("rejected content"),
            reason="inactive signed reserve; its primary passed conformance",
            audit_artifact_sha256=sha("audit:rejected"),
            reviewer_id="pool-reviewer",
            reviewer_disposition="held",
            authoring_task_id="task-rejected",
            authoring_prompt_sha256=sha("prompt:rejected"),
            visible_materials_sha256=sha("materials:rejected"),
            provenance_reference="source:rejected",
            counterfactual_dependency_result="fail",
            ontology_compatible=True,
            claim_state_review="not_applicable",
            author_saw_v12_item_ledger=False,
        )
    )
    return CandidateRegistryV13(
        authoring_inventory_ids=authoring_inventory_v13.candidate_ids,
        authoring_inventory_sha256=(
            authoring_inventory_v13.candidate_ids_sha256
        ),
        authoring_inventory_assignments_sha256=(
            authoring_inventory_v13.assignments_sha256
        ),
        candidates=candidate_pool_v13,
        records=records,
    )


@pytest.fixture
def selection_v13(
    registry_v13,
    authoring_inventory_v13,
    exclusion_manifest_v13,
    forbidden_hashes_v13,
    forbidden_fingerprints_v13,
    frozen_at_v13,
    beacon_v13,
):
    return select_candidate_pool(
        registry_v13,
        authoring_inventory=authoring_inventory_v13,
        exclusion_manifest=exclusion_manifest_v13,
        forbidden_candidate_hashes=forbidden_hashes_v13,
        forbidden_overlap_fingerprints=forbidden_fingerprints_v13,
        pool_frozen_at=frozen_at_v13,
        beacon=beacon_v13,
    )


@pytest.fixture
def signed_conformance_v13(
    selection_v13,
    tmp_path,
) -> SignedConformanceManifest:
    return SignedConformanceManifest(
        pool_sha256=selection_v13.pool_sha256,
        seed_sha256=hashlib.sha256(selection_v13.beacon.seed_bytes).hexdigest(),
        selection_sha256=selection_manifest_sha256(selection_v13),
        invalidation_history_path=str(
            tmp_path / "invalidation-history.json"
        ),
        signed_by=["owner", "independent-reviewer"],
        allowed_invalidation_predicates=set(ConformancePredicate),
    )
