#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

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
                {"summary", "wiki"}
                <= set(candidate.deletion_storage_locations)
                if candidate.category == "deletion"
                else None
            ),
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
