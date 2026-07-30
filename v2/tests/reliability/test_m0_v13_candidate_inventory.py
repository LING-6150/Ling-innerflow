import hashlib
import json
from copy import deepcopy
from pathlib import Path

import pytest

from innerflow_v2.reliability.protocol_v13 import (
    MATRIX_BY_KEY,
    MATRIX_ROWS,
    canonical_sha256,
    load_authoring_inventory,
)


V2_ROOT = Path(__file__).resolve().parents[2]
BRIEF_PATH = V2_ROOT / "eval" / "m0" / "authoring" / "M0_V13_CANDIDATE_AUTHORING_BRIEF.md"
INVENTORY_PATH = (
    V2_ROOT / "eval" / "m0" / "manifests" / "M0_V13_AUTHORING_INVENTORY_DRAFT.json"
)
SIGNED_INVENTORY_PATH = (
    V2_ROOT
    / "eval"
    / "m0"
    / "manifests"
    / "M0_V13_SIGNED_AUTHORING_INVENTORY.json"
)
REVIEW_PATH = (
    V2_ROOT / "eval" / "m0" / "reviews" / "M0_V13_INVENTORY_FREEZE_REVIEW.md"
)


def _load_inventory() -> dict:
    return json.loads(INVENTORY_PATH.read_text(encoding="utf-8"))


def _candidate_ids(inventory: dict) -> list[str]:
    return sorted(
        candidate_id
        for row in inventory["rows"]
        for slot in row["slots"]
        for candidate_id in (slot["primary_id"], slot["reserve_id"])
        if candidate_id is not None
    )


def _assignment_records(inventory: dict) -> list[dict]:
    assignments = []
    for row in inventory["rows"]:
        for slot in row["slots"]:
            assignments.append(
                {
                    "candidate_id": slot["primary_id"],
                    "role": "primary",
                    "category": row["category"],
                    "action_band": row["action_band"],
                    "situation_slot": slot["slot"],
                    "provenance_tier": slot["provenance_tier"],
                    "taxonomy_anchors": sorted(row["taxonomy_anchors"]),
                    "reserve_target_id": None,
                }
            )
            if slot["reserve_id"] is not None:
                assignments.append(
                    {
                        "candidate_id": slot["reserve_id"],
                        "role": "reserve",
                        "category": row["category"],
                        "action_band": row["action_band"],
                        "situation_slot": slot["slot"],
                        "provenance_tier": slot["provenance_tier"],
                        "taxonomy_anchors": sorted(row["taxonomy_anchors"]),
                        "reserve_target_id": slot["primary_id"],
                    }
                )
    return sorted(assignments, key=lambda value: value["candidate_id"])


def test_inventory_is_content_free_and_bound_to_authoring_brief() -> None:
    inventory = _load_inventory()

    assert inventory["status"] == "DRAFT_AWAITING_INDEPENDENT_REVIEW"
    assert inventory["authoring_brief_sha256"] == hashlib.sha256(
        BRIEF_PATH.read_bytes()
    ).hexdigest()

    serialized = json.dumps(inventory, sort_keys=True)
    forbidden_content_fields = (
        '"probe"',
        '"claims"',
        '"gold"',
        '"setup_turns"',
        '"memory_state"',
        '"response_action"',
        '"surface_form_lexicon"',
    )
    for field in forbidden_content_fields:
        assert field not in serialized


def test_inventory_exactly_covers_the_frozen_matrix_and_provenance_quotas() -> None:
    inventory = _load_inventory()
    rows = inventory["rows"]

    assert len(rows) == len(MATRIX_ROWS) == 12
    assert {
        (row["category"], row["action_band"]) for row in rows
    } == set(MATRIX_BY_KEY)

    primary_ids: list[str] = []
    reserve_ids: list[str] = []
    all_ids: list[str] = []

    for row in rows:
        frozen = MATRIX_BY_KEY[(row["category"], row["action_band"])]
        assert row["category"] == frozen.category
        assert row["action_band"] == frozen.action_band
        assert len(row["slots"]) == 3
        assert [slot["slot"] for slot in row["slots"]] == list(frozen.slots)

        assert all(slot["primary_id"] for slot in row["slots"])
        assert sum(slot["reserve_id"] is not None for slot in row["slots"]) == 1

        primary_external = sum(
            slot["provenance_tier"] == "E" for slot in row["slots"]
        )
        primary_product = sum(
            slot["provenance_tier"] == "P" for slot in row["slots"]
        )
        assert primary_external >= frozen.selected_external
        assert primary_product >= frozen.selected_product

        for slot in row["slots"]:
            assert slot["primary_id"].endswith("-primary")
            assert slot["provenance_tier"] in {"E", "P"}
            primary_ids.append(slot["primary_id"])
            all_ids.append(slot["primary_id"])

            if slot["reserve_id"] is not None:
                assert slot["reserve_id"].endswith("-reserve")
                reserve_ids.append(slot["reserve_id"])
                all_ids.append(slot["reserve_id"])

    assert len(primary_ids) == inventory["inventory_policy"]["primary_ids"] == 36
    assert len(reserve_ids) == inventory["inventory_policy"]["reserve_ids"] == 12
    assert (
        len(all_ids)
        == inventory["inventory_policy"]["total_precommitted_ids"]
        == 48
    )
    assert len(set(all_ids)) == len(all_ids)
    assert inventory["candidate_ids_sha256"] == canonical_sha256(sorted(all_ids))
    assert inventory["assignments_sha256"] == canonical_sha256(
        _assignment_records(inventory)
    )


@pytest.mark.parametrize(
    "mutation",
    ("slot_reassignment", "provenance_reassignment", "reserve_retargeting"),
)
def test_assignment_hash_rejects_identity_preserving_reassignment(
    mutation: str,
) -> None:
    inventory = _load_inventory()
    changed = deepcopy(inventory)

    if mutation == "slot_reassignment":
        first, second = changed["rows"][0]["slots"][:2]
        first["primary_id"], second["primary_id"] = (
            second["primary_id"],
            first["primary_id"],
        )
    elif mutation == "provenance_reassignment":
        first, third = (
            changed["rows"][2]["slots"][0],
            changed["rows"][2]["slots"][2],
        )
        first["provenance_tier"], third["provenance_tier"] = (
            third["provenance_tier"],
            first["provenance_tier"],
        )
    else:
        first, second = changed["rows"][0]["slots"][:2]
        second["reserve_id"] = first["reserve_id"]
        first["reserve_id"] = None

    assert _candidate_ids(changed) == _candidate_ids(inventory)
    assert canonical_sha256(_candidate_ids(changed)) == inventory[
        "candidate_ids_sha256"
    ]
    assert canonical_sha256(_assignment_records(changed)) != inventory[
        "assignments_sha256"
    ]


def test_inventory_sources_are_traceable_and_not_claimed_as_item_adaptations() -> None:
    inventory = _load_inventory()
    sources = inventory["sources"]

    assert set(sources) == {
        "longmemeval",
        "personamem",
        "benchpres",
        "rpeval",
        "innerflow",
    }
    for source_name, source in sources.items():
        if source_name != "innerflow":
            assert source["reference"].startswith("https://")
        assert source["retrieved_at"] == "2026-07-28"
    assert all(
        sources[name]["use"] == "construction-pattern taxonomy only"
        for name in ("longmemeval", "personamem", "benchpres", "rpeval")
    )
    assert sources["innerflow"]["use"] == "disclosed product-invariant extension"


def test_signed_inventory_binds_review_and_complete_draft_assignments() -> None:
    draft = _load_inventory()
    signed = load_authoring_inventory(SIGNED_INVENTORY_PATH)

    assert signed.candidate_ids == _candidate_ids(draft)
    assert signed.candidate_ids_sha256 == draft["candidate_ids_sha256"]
    assert signed.assignments_sha256 == draft["assignments_sha256"]
    assert [
        assignment.model_dump(mode="json") for assignment in signed.assignments
    ] == _assignment_records(draft)
    assert signed.review_disposition_sha256 == hashlib.sha256(
        REVIEW_PATH.read_bytes()
    ).hexdigest()
    assert signed.signed_by == ["LING-6150", "independent-agent-reviewer"]
