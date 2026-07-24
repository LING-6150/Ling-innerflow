from __future__ import annotations

import hashlib
import json
import random
import subprocess
import time
from dataclasses import asdict, dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any

from innerflow_v2.reliability.baselines import (
    FaithfulSummaryPolicy,
    FullHistoryPolicy,
    NoMemoryPolicy,
    PolicyOutput,
)
from innerflow_v2.reliability.client import ChatBackend, EmbeddingBackend
from innerflow_v2.reliability.gate import Cell, G0Decision, evaluate_g0
from innerflow_v2.reliability.grading import Grade, grade_strict_choice
from innerflow_v2.reliability.models import ReliabilityScenario, load_scenarios


@dataclass(frozen=True)
class RunConfig:
    provider: str
    base_url: str
    model: str
    embedding_model: str | None
    run_date: date
    formation_temperature: float = 0.2
    response_temperature: float = 0.4
    replicates: int = 3
    retry_attempts: int = 3
    retry_backoff_seconds: float = 2.0
    order_seed: int = 6150


class RetryingBackend:
    def __init__(
        self,
        backend: ChatBackend,
        *,
        attempts: int,
        backoff_seconds: float,
    ) -> None:
        self.backend = backend
        self.attempts = attempts
        self.backoff_seconds = backoff_seconds

    def complete(self, **kwargs):
        last_error: Exception | None = None
        for attempt in range(1, self.attempts + 1):
            try:
                return self.backend.complete(**kwargs)
            except Exception as error:
                last_error = error
                if attempt < self.attempts:
                    time.sleep(self.backoff_seconds * attempt)
        assert last_error is not None
        raise last_error


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def current_commit(repo_root: Path) -> str:
    return subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=repo_root,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def build_run_freeze(
    *,
    repo_root: Path,
    fixture_path: Path,
    corpus_manifest_path: Path,
    config: RunConfig,
) -> dict[str, Any]:
    reliability_root = repo_root / "v2/src/innerflow_v2/reliability"
    implementation_files = (
        "baselines.py",
        "client.py",
        "gate.py",
        "grading.py",
        "prompts.py",
        "report.py",
        "run.py",
    )
    lock_path = repo_root / "v2/uv.lock"
    return {
        "protocol_version": "v1.2",
        "status": "frozen-before-baseline-output",
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source_commit": current_commit(repo_root),
        "provider": config.provider,
        "base_url": config.base_url,
        "model": config.model,
        "embedding_model": config.embedding_model,
        "run_date": config.run_date.isoformat(),
        "parameters": {
            "formation_temperature": config.formation_temperature,
            "response_temperature": config.response_temperature,
            "initial_replicates": config.replicates,
            "retry_attempts": config.retry_attempts,
            "retry_backoff_seconds": config.retry_backoff_seconds,
            "policy_order_seed": config.order_seed,
            "compression_threshold_rounds": 10,
            "keep_recent_rounds": 4,
        },
        "fixture_sha256": sha256_file(fixture_path),
        "corpus_manifest_sha256": sha256_file(corpus_manifest_path),
        "implementation_source_hashes": {
            name: sha256_file(reliability_root / name)
            for name in implementation_files
        },
        "dependency_lock_sha256": sha256_file(lock_path),
        "holdout_disclosure": "aggregate-only",
        "raw_output_retention": (
            "local sealed artifact; SHA-256 is published, item-level holdout is not"
        ),
    }


def verify_run_freeze(
    manifest: dict[str, Any],
    *,
    repo_root: Path,
    fixture_path: Path,
    corpus_manifest_path: Path,
    config: RunConfig,
) -> None:
    checks = {
        "provider": config.provider,
        "base_url": config.base_url,
        "model": config.model,
        "embedding_model": config.embedding_model,
        "run_date": config.run_date.isoformat(),
        "fixture_sha256": sha256_file(fixture_path),
        "corpus_manifest_sha256": sha256_file(corpus_manifest_path),
        "dependency_lock_sha256": sha256_file(repo_root / "v2/uv.lock"),
    }
    drift = {
        key: {"frozen": manifest.get(key), "current": value}
        for key, value in checks.items()
        if manifest.get(key) != value
    }
    if drift:
        raise ValueError(f"run freeze drift: {drift}")
    current_freeze = build_run_freeze(
        repo_root=repo_root,
        fixture_path=fixture_path,
        corpus_manifest_path=corpus_manifest_path,
        config=config,
    )
    if manifest.get("parameters") != current_freeze["parameters"]:
        raise ValueError("run parameter drift")
    if (
        manifest.get("implementation_source_hashes")
        != current_freeze["implementation_source_hashes"]
    ):
        raise ValueError("run implementation drift")


def run_m0(
    scenarios: list[ReliabilityScenario],
    *,
    backend: ChatBackend,
    embedding_backend: EmbeddingBackend | None,
    config: RunConfig,
    initial_records: list[dict[str, Any]] | None = None,
) -> tuple[list[dict[str, Any]], G0Decision]:
    retrying = RetryingBackend(
        backend,
        attempts=config.retry_attempts,
        backoff_seconds=config.retry_backoff_seconds,
    )
    records = list(initial_records or [])
    completed_replicates = {
        int(record["replicate"]) for record in records
    }
    target_replicates = list(range(1, config.replicates + 1))
    for replicate in target_replicates:
        if replicate in completed_replicates:
            continue
        records.extend(
            _run_replicate(
                scenarios,
                replicate=replicate,
                backend=retrying,
                embedding_backend=embedding_backend,
                config=config,
            )
        )
    decision = evaluate_g0(scenarios, records_to_cells(records))
    if decision.decision == "NEEDS_FIVE_RUNS":
        for replicate in (4, 5):
            records.extend(
                _run_replicate(
                    scenarios,
                    replicate=replicate,
                    backend=retrying,
                    embedding_backend=embedding_backend,
                    config=config,
                )
            )
        decision = evaluate_g0(scenarios, records_to_cells(records))
    return records, decision


def _run_replicate(
    scenarios: list[ReliabilityScenario],
    *,
    replicate: int,
    backend: ChatBackend,
    embedding_backend: EmbeddingBackend | None,
    config: RunConfig,
) -> list[dict[str, Any]]:
    work = [
        (scenario, policy)
        for scenario in scenarios
        for policy in ("B-summary", "B-full", "B-none")
    ]
    random.Random(config.order_seed + replicate).shuffle(work)
    records: list[dict[str, Any]] = []
    for sequence, (scenario, policy_name) in enumerate(work, start=1):
        try:
            if policy_name == "B-summary":
                policy = FaithfulSummaryPolicy(
                    backend,
                    today=config.run_date,
                    formation_temperature=config.formation_temperature,
                    response_temperature=config.response_temperature,
                    embedding_backend=embedding_backend,
                )
            elif policy_name == "B-full":
                policy = FullHistoryPolicy(
                    backend,
                    response_temperature=config.response_temperature,
                )
            else:
                policy = NoMemoryPolicy(
                    backend,
                    response_temperature=config.response_temperature,
                )
            output = policy.run(scenario, replicate=replicate)
            grade = grade_strict_choice(output.raw_answer, scenario)
            records.append(
                _successful_record(output, grade, sequence=sequence)
            )
        except Exception as error:
            records.append(
                {
                    "case_id": scenario.case_id,
                    "split": scenario.split,
                    "category": scenario.category,
                    "policy": policy_name,
                    "replicate": replicate,
                    "sequence": sequence,
                    "status": "api_failure",
                    "error_type": type(error).__name__,
                    "error": str(error),
                    "correct": None,
                    "choice": None,
                    "grade_reason": "unavailable",
                    "context_source_ids": [],
                }
            )
    return records


def _successful_record(
    output: PolicyOutput, grade: Grade, *, sequence: int
) -> dict[str, Any]:
    value = asdict(output)
    value.update(
        {
            "sequence": sequence,
            "status": "complete",
            "correct": grade.correct,
            "choice": grade.choice,
            "grade_reason": grade.reason,
        }
    )
    return value


def records_to_cells(records: list[dict[str, Any]]) -> list[Cell]:
    return [
        Cell(
            case_id=record["case_id"],
            policy=record["policy"],
            replicate=int(record["replicate"]),
            correct=record.get("correct"),
            context_source_ids=tuple(record.get("context_source_ids", [])),
        )
        for record in records
    ]


def write_raw_artifact(
    path: Path,
    *,
    run_manifest: dict[str, Any],
    records: list[dict[str, Any]],
    decision: G0Decision,
) -> str:
    payload = {
        "run_manifest": run_manifest,
        "records": records,
        "g0": asdict(decision),
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return sha256_file(path)


def load_raw_records(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    value = json.loads(path.read_text(encoding="utf-8"))
    records = value.get("records", [])
    if not isinstance(records, list):
        raise ValueError("raw artifact records must be a list")
    return records
