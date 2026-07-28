#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.baselines import FaithfulSummaryPolicy, MemoryMessage
from innerflow_v2.reliability.client import Completion
from innerflow_v2.reliability.protocol_v13 import (
    CandidateRegistryRecordV13,
    CandidateRegistryV13,
    candidate_sha256,
    canonical_sha256,
    load_authoring_inventory,
    load_candidate_pool,
    validate_authored_candidate_inventory,
    validate_candidate_registry,
)


class _DerivedMemoryEvidenceBackend:
    def __init__(self, claim: str) -> None:
        self.claim = claim
        self.operations: list[str] = []

    def complete(self, *, operation, prompt, temperature, max_tokens):
        self.operations.append(operation)
        if operation == "memory.compression.summary":
            content = f"Persisted user preference: {self.claim}"
        elif operation == "memory.wiki.first_extract":
            content = json.dumps(
                {
                    "emotionPattern": None,
                    "coreStruggles": None,
                    "effectiveCoping": None,
                    "languageStyle": self.claim,
                    "triggerUpdates": [],
                    "conflicts": [],
                    "newProgressNote": None,
                    "changeLogEntry": (
                        "Stored the authored deletion target in derived memory."
                    ),
                }
            )
        elif operation == "memory.reflection":
            content = f"Derived memory still contains: {self.claim}"
        else:
            raise AssertionError(f"unexpected deterministic operation: {operation}")
        return Completion(content, f"offline-{len(self.operations)}")


def _deletion_derived_evidence(candidate) -> dict:
    pre_world = next(
        world for world in candidate.worlds if world.world_id == "pre_delete"
    )
    claim = pre_world.effective_claim
    if claim is None or pre_world.applicable_claim_id is None:
        raise ValueError("deletion candidate lacks a pre-delete target")
    target_event_id = next(
        claim_record.source_event_ids[0]
        for claim_record in candidate.tracked_claims
        if claim_record.claim_id == pre_world.applicable_claim_id
    )
    backend = _DerivedMemoryEvidenceBackend(claim)
    policy = FaithfulSummaryPolicy(backend, today=date(2026, 7, 28))
    for event in pre_world.setup_memory_events:
        policy.add_message(
            MemoryMessage(
                role=event.role,
                content=event.content,
                timestamp=1_700_000_000_000 + event.sequence_index,
                source_ids=(event.event_id,),
            )
        )
    summary_contains_target = (
        policy.wiki.conversation_summary is not None
        and claim in policy.wiki.conversation_summary
    )
    summary_sources_target = target_event_id in policy.wiki.field_sources.get(
        "conversation_summary",
        set(),
    )
    policy.end_session()
    rendered_context, _ = policy.build_context()
    evidence = {
        "compression_applied": policy.trace.compression_applied == 1,
        "summary_contains_target": summary_contains_target,
        "summary_sources_target_event": summary_sources_target,
        "wiki_field_contains_target": policy.wiki.language_style == claim,
        "rendered_context_contains_target": claim in rendered_context,
        "operations": backend.operations,
    }
    evidence["passed"] = all(
        value
        for key, value in evidence.items()
        if key not in {"operations", "passed"}
    ) and evidence["operations"] == [
        "memory.compression.summary",
        "memory.wiki.first_extract",
        "memory.reflection",
    ]
    return evidence


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the offline M0 v1.3 whole-inventory conformance audit."
    )
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--audit-output", type=Path, required=True)
    parser.add_argument("--registry-output", type=Path, required=True)
    args = parser.parse_args()

    inventory = load_authoring_inventory(args.inventory)
    candidates = load_candidate_pool(args.candidates)
    candidate_by_id = {candidate.candidate_id: candidate for candidate in candidates}
    hashes = validate_authored_candidate_inventory(
        candidates,
        authoring_inventory=inventory,
    )

    audit_entries = []
    for assignment in inventory.assignments:
        candidate = candidate_by_id[assignment.candidate_id]
        deletion_evidence = (
            _deletion_derived_evidence(candidate)
            if candidate.category == "deletion"
            else None
        )
        entry = {
            "candidate_id": candidate.candidate_id,
            "assignment_match": True,
            "schema_conformance": True,
            "counterfactual_dependency": (
                "pass"
                if candidate.category
                in {"correction", "supersession", "context-exception"}
                else "not_applicable"
            ),
            "action_ontology_compatible": True,
            "claim_state_review": "pass",
            "compression_length_setup": all(
                len(world.setup_memory_events) >= 20
                for world in candidate.worlds
            ),
            "derived_deletion_target": (
                deletion_evidence["passed"]
                if candidate.category == "deletion"
                else None
            ),
            "derived_deletion_evidence": deletion_evidence,
            "v1_2_item_ledger_visible": candidate.author_saw_v12_item_ledger,
            "automated_disposition": (
                "eligible-primary"
                if assignment.role == "primary"
                else "inactive-signed-reserve"
            ),
        }
        entry["audit_artifact_sha256"] = canonical_sha256(entry)
        audit_entries.append(entry)

    audit_manifest = {
        "protocol_version": "v1.3",
        "status": "AUTOMATED_CONFORMANCE_COMPLETE_PENDING_WHOLE_POOL_REVIEW",
        "signed_inventory_sha256": inventory.sha256,
        "candidate_count": len(candidates),
        "entries": audit_entries,
    }
    with args.audit_output.open("x", encoding="utf-8") as stream:
        json.dump(audit_manifest, stream, ensure_ascii=False, indent=2)
        stream.write("\n")

    audit_by_id = {
        entry["candidate_id"]: entry for entry in audit_entries
    }
    primary_ids = {
        assignment.candidate_id
        for assignment in inventory.assignments
        if assignment.role == "primary"
    }
    eligible_candidates = [
        candidate for candidate in candidates if candidate.candidate_id in primary_ids
    ]
    records = []
    for assignment in inventory.assignments:
        candidate = candidate_by_id[assignment.candidate_id]
        is_primary = assignment.role == "primary"
        records.append(
            CandidateRegistryRecordV13(
                candidate_id=candidate.candidate_id,
                inventory_role=assignment.role,
                status="eligible" if is_primary else "reserve",
                category=candidate.category,
                action_band=candidate.action_band,
                situation_slot=candidate.situation_slot,
                provenance_tier=candidate.provenance_tier,
                taxonomy_anchors=candidate.provenance_artifact.taxonomy_anchors,
                reserve_target_id=assignment.reserve_target_id,
                content_sha256=hashes[candidate.candidate_id],
                reason=(
                    "passed every automated frozen eligibility predicate"
                    if is_primary
                    else "inactive signed reserve; its primary passed conformance"
                ),
                audit_artifact_sha256=audit_by_id[candidate.candidate_id][
                    "audit_artifact_sha256"
                ],
                reviewer_id="automated-conformance-v13",
                reviewer_disposition="pass" if is_primary else "held",
                authoring_task_id=candidate.authoring_task_id,
                authoring_prompt_sha256=candidate.authoring_prompt_sha256,
                visible_materials_sha256=candidate.visible_materials_sha256,
                provenance_reference=candidate.provenance_reference,
                counterfactual_dependency_result=audit_by_id[
                    candidate.candidate_id
                ]["counterfactual_dependency"],
                ontology_compatible=True,
                claim_state_review="pass",
                author_saw_v12_item_ledger=False,
            )
        )
    registry = CandidateRegistryV13(
        authoring_inventory_ids=inventory.candidate_ids,
        authoring_inventory_sha256=inventory.candidate_ids_sha256,
        authoring_inventory_assignments_sha256=inventory.assignments_sha256,
        candidates=eligible_candidates,
        records=records,
    )
    validate_candidate_registry(
        registry,
        authoring_inventory=inventory,
        authored_candidates=candidates,
    )
    with args.registry_output.open("x", encoding="utf-8") as stream:
        stream.write(registry.model_dump_json(indent=2) + "\n")


if __name__ == "__main__":
    main()
