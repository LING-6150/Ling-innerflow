#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = ROOT.parent
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.protocol_v13 import (
    CandidatePoolFreezeManifestV13,
    load_authoring_inventory,
    load_candidate_pool,
    load_candidate_registry,
    pool_sha256,
    registry_sha256,
    validate_candidate_registry,
)


def _file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise argparse.ArgumentTypeError("timestamp must include a timezone")
    return parsed


def _repo_relative(path: Path) -> str:
    return path.resolve().relative_to(REPO_ROOT.resolve()).as_posix()


def _head_commit() -> str:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=REPO_ROOT,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Freeze the independently reviewed M0 v1.3 candidate pool."
    )
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--registry", type=Path, required=True)
    parser.add_argument("--review", type=Path, required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--frozen-at", type=_timestamp, required=True)
    parser.add_argument("--signed-by", action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        raise ValueError("source commit must be a full lowercase Git SHA-1")
    if args.source_commit != _head_commit():
        raise ValueError("source commit must be the current reviewed HEAD")

    review = args.review.read_text(encoding="utf-8")
    if "**Disposition:** `FREEZE_CANDIDATE_POOL`" not in review:
        raise ValueError("review does not authorize candidate-pool freeze")
    if f"**Reviewed source commit:** `{args.source_commit}`" not in review:
        raise ValueError("review does not bind the current source commit")

    inventory = load_authoring_inventory(args.inventory)
    candidates = load_candidate_pool(args.candidates)
    registry = load_candidate_registry(args.registry)
    audit = json.loads(args.audit.read_text(encoding="utf-8"))
    hashes = validate_candidate_registry(
        registry,
        authoring_inventory=inventory,
        authored_candidates=candidates,
    )
    if audit.get("candidate_count") != 48:
        raise ValueError("conformance audit must cover all 48 authored candidates")
    if audit.get("signed_inventory_sha256") != inventory.sha256:
        raise ValueError("conformance audit inventory identity drift")
    if len(audit.get("entries", [])) != 48:
        raise ValueError("conformance audit entry count drift")

    manifest = CandidatePoolFreezeManifestV13(
        source_commit=args.source_commit,
        authoring_inventory_path=_repo_relative(args.inventory),
        authored_candidates_path=_repo_relative(args.candidates),
        conformance_audit_path=_repo_relative(args.audit),
        candidate_registry_path=_repo_relative(args.registry),
        review_disposition_path=_repo_relative(args.review),
        authoring_inventory_file_sha256=_file_sha256(args.inventory),
        authoring_inventory_manifest_sha256=inventory.sha256,
        authored_candidates_file_sha256=_file_sha256(args.candidates),
        conformance_audit_file_sha256=_file_sha256(args.audit),
        candidate_registry_file_sha256=_file_sha256(args.registry),
        candidate_registry_sha256=registry_sha256(registry),
        review_disposition_file_sha256=_file_sha256(args.review),
        eligible_pool_sha256=pool_sha256(hashes),
        authored_candidate_count=len(candidates),
        eligible_candidate_count=len(hashes),
        reserve_candidate_count=sum(
            record.status == "reserve" for record in registry.records
        ),
        frozen_at=args.frozen_at,
        future_seed_not_before=args.frozen_at + timedelta(hours=24),
        signed_by=args.signed_by,
    )
    with args.output.open("x", encoding="utf-8") as stream:
        stream.write(manifest.model_dump_json(indent=2) + "\n")


if __name__ == "__main__":
    main()
