from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.client import OpenAICompatibleBackend
from innerflow_v2.reliability.models import load_scenarios
from innerflow_v2.reliability.report import render_m0_report
from innerflow_v2.reliability.run import (
    RunConfig,
    build_run_freeze,
    load_raw_records,
    run_m0,
    verify_run_freeze,
    write_raw_artifact,
)

FIXTURE = ROOT / "eval/m0/fixtures/memory_reliability_m0.json"
CORPUS_MANIFEST = ROOT / "eval/m0/manifests/M0_CORPUS_FREEZE.json"
RUN_MANIFEST = ROOT / "eval/m0/manifests/M0_RUN_FREEZE_V3.json"
RAW_PATH = ROOT / "eval/m0/private/M0_RAW.json"
RAW_HASH_PATH = ROOT / "eval/m0/manifests/M0_RAW_SHA256.txt"
RESULTS_PATH = ROOT / "eval/m0/RESULTS_M0.md"
REPO_ROOT = ROOT.parent


def config_from_env() -> RunConfig:
    return RunConfig(
        provider=os.environ.get("M0_PROVIDER", "ModelVerse"),
        base_url=os.environ.get("M0_BASE_URL", "https://api.modelverse.cn/v1"),
        model=os.environ.get("M0_MODEL", "gemini-2.5-flash"),
        embedding_model=os.environ.get(
            "M0_EMBED_MODEL", "text-embedding-3-large"
        ),
        run_date=date.fromisoformat(os.environ.get("M0_RUN_DATE", "2026-07-24")),
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("prepare", "run"))
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    config = config_from_env()

    if args.command == "prepare":
        if RUN_MANIFEST.exists():
            raise SystemExit(f"refusing to overwrite {RUN_MANIFEST}")
        manifest = build_run_freeze(
            repo_root=REPO_ROOT,
            fixture_path=FIXTURE,
            corpus_manifest_path=CORPUS_MANIFEST,
            config=config,
        )
        RUN_MANIFEST.parent.mkdir(parents=True, exist_ok=True)
        RUN_MANIFEST.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"frozen run manifest: {RUN_MANIFEST}")
        return

    api_key = os.environ.get("MODELVERSE_API_KEY")
    if not api_key:
        raise SystemExit("MODELVERSE_API_KEY is required")
    manifest = json.loads(RUN_MANIFEST.read_text(encoding="utf-8"))
    verify_run_freeze(
        manifest,
        repo_root=REPO_ROOT,
        fixture_path=FIXTURE,
        corpus_manifest_path=CORPUS_MANIFEST,
        config=config,
    )
    scenarios = load_scenarios(FIXTURE)
    backend = OpenAICompatibleBackend(
        api_key=api_key,
        model=config.model,
        base_url=config.base_url,
        embedding_model=config.embedding_model,
    )
    initial = load_raw_records(RAW_PATH) if args.resume else []
    records, decision = run_m0(
        scenarios,
        backend=backend,
        embedding_backend=backend if config.embedding_model else None,
        config=config,
        initial_records=initial,
    )
    raw_hash = write_raw_artifact(
        RAW_PATH,
        run_manifest=manifest,
        records=records,
        decision=decision,
    )
    RAW_HASH_PATH.write_text(raw_hash + "\n", encoding="utf-8")
    RESULTS_PATH.write_text(
        render_m0_report(
            scenarios,
            records,
            decision,
            raw_sha256=raw_hash,
            model=config.model,
        ),
        encoding="utf-8",
    )
    print(f"{decision.decision}: {decision.reason}")
    print(f"raw SHA-256: {raw_hash}")
    print(f"report: {RESULTS_PATH}")


if __name__ == "__main__":
    main()
