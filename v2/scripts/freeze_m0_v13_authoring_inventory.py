#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.protocol_v13 import (
    AuthoringInventoryAssignment,
    SignedAuthoringInventory,
    canonical_sha256,
)


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise argparse.ArgumentTypeError("timestamp must include a timezone")
    return parsed


def _assignments(draft: dict) -> list[AuthoringInventoryAssignment]:
    assignments = []
    for row in draft["rows"]:
        anchors = sorted(row["taxonomy_anchors"])
        for slot in row["slots"]:
            assignments.append(
                AuthoringInventoryAssignment(
                    candidate_id=slot["primary_id"],
                    role="primary",
                    category=row["category"],
                    action_band=row["action_band"],
                    situation_slot=slot["slot"],
                    provenance_tier=slot["provenance_tier"],
                    taxonomy_anchors=anchors,
                )
            )
            if slot["reserve_id"] is not None:
                assignments.append(
                    AuthoringInventoryAssignment(
                        candidate_id=slot["reserve_id"],
                        role="reserve",
                        category=row["category"],
                        action_band=row["action_band"],
                        situation_slot=slot["slot"],
                        provenance_tier=slot["provenance_tier"],
                        taxonomy_anchors=anchors,
                        reserve_target_id=slot["primary_id"],
                    )
                )
    return sorted(assignments, key=lambda value: value.candidate_id)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Freeze a reviewed M0 v1.3 content-free authoring inventory."
    )
    parser.add_argument("--draft", type=Path, required=True)
    parser.add_argument("--brief", type=Path, required=True)
    parser.add_argument("--review", type=Path, required=True)
    parser.add_argument("--frozen-at", type=_timestamp, required=True)
    parser.add_argument("--signed-by", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    draft = json.loads(args.draft.read_text(encoding="utf-8"))
    if draft["status"] != "DRAFT_AWAITING_INDEPENDENT_REVIEW":
        raise ValueError("only a reviewed draft inventory may be frozen")
    if draft["authoring_brief_sha256"] != _sha256(args.brief):
        raise ValueError("authoring brief hash drift")

    assignments = _assignments(draft)
    candidate_ids = sorted(assignment.candidate_id for assignment in assignments)
    assignment_payload = [
        assignment.model_dump(mode="json") for assignment in assignments
    ]
    if canonical_sha256(candidate_ids) != draft["candidate_ids_sha256"]:
        raise ValueError("candidate id hash drift")
    if canonical_sha256(assignment_payload) != draft["assignments_sha256"]:
        raise ValueError("candidate assignment hash drift")

    inventory = SignedAuthoringInventory(
        candidate_ids=candidate_ids,
        candidate_ids_sha256=draft["candidate_ids_sha256"],
        assignments=assignments,
        assignments_sha256=draft["assignments_sha256"],
        review_disposition_sha256=_sha256(args.review),
        frozen_at=args.frozen_at,
        signed_by=args.signed_by,
    )
    with args.output.open("x", encoding="utf-8") as stream:
        stream.write(inventory.model_dump_json(indent=2) + "\n")


if __name__ == "__main__":
    main()
