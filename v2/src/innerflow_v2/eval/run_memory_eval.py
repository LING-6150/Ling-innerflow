"""Run the Stage 2 memory eval: drive each system over the cases and aggregate.

Cheating isolation: a system is handed ONLY `case.observations`. Gold
(current facts, conflicts, relevant ids) never reaches the system — it is used
here, by the evaluator, to score outputs.
"""
from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field

from innerflow_v2.eval.memory_eval import (
    MemoryEvalCase,
    conflict_resolution_counts,
    contradiction_counts,
    coverage_counts,
    extra_claim_counts,
    false_conflict_counts,
    recall_at_k,
)


@dataclass
class SystemReport:
    name: str
    contradictory: int = 0
    evaluated_facts: int = 0
    covered: int = 0
    correct_conflicts: int = 0
    gold_conflicts: int = 0
    recall_current: list[float] = field(default_factory=list)
    recall_historical: list[float] = field(default_factory=list)
    # precision guards (catch over-emission once an LLM is wired in PR-2)
    extra_claims: int = 0
    total_claims: int = 0
    false_conflicts: int = 0

    @property
    def contradiction_rate(self) -> float:
        return self.contradictory / self.evaluated_facts if self.evaluated_facts else 0.0

    @property
    def coverage_rate(self) -> float:
        return self.covered / self.evaluated_facts if self.evaluated_facts else 0.0

    @property
    def conflict_resolution_accuracy(self) -> float:
        return self.correct_conflicts / self.gold_conflicts if self.gold_conflicts else 0.0

    @staticmethod
    def _avg(xs: list[float]) -> float:
        return sum(xs) / len(xs) if xs else 0.0

    @property
    def recall_current_at_k(self) -> float:
        return self._avg(self.recall_current)

    @property
    def recall_historical_at_k(self) -> float:
        return self._avg(self.recall_historical)

    @property
    def extra_claim_rate(self) -> float:
        return self.extra_claims / self.total_claims if self.total_claims else 0.0

    @property
    def false_conflict_count(self) -> int:
        return self.false_conflicts


def run_memory_eval(
    cases: Sequence[MemoryEvalCase], system_classes: Sequence[type], k: int = 3
) -> dict[str, SystemReport]:
    reports: dict[str, SystemReport] = {}
    for cls in system_classes:
        system = cls()
        rep = SystemReport(name=getattr(cls, "name", cls.__name__))
        for case in cases:
            system = cls()
            system.ingest(case.observations)  # ONLY observations — no gold
            claims = system.profile()
            cons = system.resolved_conflicts()

            c_bad, c_eval = contradiction_counts(claims, case.gold_current_facts)
            cov, _ = coverage_counts(claims, case.gold_current_facts)
            cr_ok, cr_total = conflict_resolution_counts(cons, case.gold_conflicts)
            extra, n_claims = extra_claim_counts(claims, case.gold_current_facts)
            false_c, _ = false_conflict_counts(cons, case.gold_conflicts)
            rep.contradictory += c_bad
            rep.evaluated_facts += c_eval
            rep.covered += cov
            rep.correct_conflicts += cr_ok
            rep.gold_conflicts += cr_total
            rep.extra_claims += extra
            rep.total_claims += n_claims
            rep.false_conflicts += false_c

            for q in case.retrieval_queries:
                r = recall_at_k(system.retrieve(q.query, k), q.relevant_observation_ids, k)
                (rep.recall_current if q.kind == "current" else rep.recall_historical).append(r)
        reports[rep.name] = rep
    return reports


def render_markdown(reports: dict[str, SystemReport], k: int, split: str) -> str:
    md = [
        "# Stage 2 Memory Eval — Diagnostic Floor (deterministic)",
        "",
        f"Split: `{split}` · retrieval k={k}. "
        "Systems receive only the observation stream; gold is held by the evaluator. "
        "Deterministic floor — NOT a proof; the real (LLM/embedding) result is PR-2.",
        "",
        "| system | contradiction_rate ↓ | coverage ↑ | conflict_acc ↑ | recall@k current ↑ | recall@k historical ↑ | extra_claim_rate ↓ | false_conflicts ↓ |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in reports.values():
        md.append(
            f"| {r.name} | {r.contradiction_rate:.3f} | {r.coverage_rate:.3f} | "
            f"{r.conflict_resolution_accuracy:.3f} | {r.recall_current_at_k:.3f} | "
            f"{r.recall_historical_at_k:.3f} | {r.extra_claim_rate:.3f} | "
            f"{r.false_conflict_count} |"
        )
    md += [
        "",
        "Reading: B-latest-by-key is strong on current facts but loses keep_both "
        "(context-specific exceptions) and historical recall; B-full/B-extract keep "
        "stale facts as current; B-rag builds no profile. The kernel should be the "
        "only system strong on all columns. The precision guards "
        "(extra_claim_rate / false_conflicts) read 0 for these deterministic "
        "systems — they exist to catch an LLM over-emitting in PR-2.",
        "",
    ]
    return "\n".join(md)
