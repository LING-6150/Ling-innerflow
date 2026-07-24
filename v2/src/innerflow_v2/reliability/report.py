from __future__ import annotations

from collections import Counter
from dataclasses import asdict
from typing import Any

from innerflow_v2.reliability.gate import G0Decision
from innerflow_v2.reliability.models import ReliabilityScenario


def render_m0_report(
    scenarios: list[ReliabilityScenario],
    records: list[dict[str, Any]],
    decision: G0Decision,
    *,
    raw_sha256: str,
    model: str,
) -> str:
    scenario_by_id = {scenario.case_id: scenario for scenario in scenarios}
    replicates = sorted({int(record["replicate"]) for record in records})
    policies = ("B-summary", "B-full", "B-none")
    lines = [
        "# InnerFlow Memory Reliability — M0 Results",
        "",
        "> Protocol: v1.2 frozen. Counts are the scenario unit; no percentages or "
        "LLM judge are used.",
        "",
        "## Run identity",
        "",
        f"- Model: `{model}`",
        f"- Complete replicates: `{len(replicates)}`",
        f"- Sealed raw artifact SHA-256: `{raw_sha256}`",
        "- Holdout disclosure: aggregate only; item-level holdout traces remain sealed.",
        "",
        "## Per-replicate answer errors",
        "",
        "| Policy | Split | " + " | ".join(f"R{r}" for r in replicates) + " |",
        "|---|---|" + "---|" * len(replicates),
    ]
    for policy in policies:
        for split, denominator in (("visible", 16), ("holdout", 8)):
            values = []
            for replicate in replicates:
                matching = [
                    record
                    for record in records
                    if record["policy"] == policy
                    and record["replicate"] == replicate
                    and record["split"] == split
                ]
                unavailable = sum(record["correct"] is None for record in matching)
                wrong = sum(record["correct"] is False for record in matching)
                values.append(
                    f"{wrong}/{denominator}"
                    + (f" (+{unavailable} unavailable)" if unavailable else "")
                )
            lines.append(
                f"| {policy} | {split} | " + " | ".join(values) + " |"
            )

    lines.extend(
        [
            "",
            "## Visible scenario-majority results",
            "",
            "| Scenario | Category | B-summary | B-full | B-none |",
            "|---|---|---|---|---|",
        ]
    )
    for scenario in scenarios:
        if scenario.split != "visible":
            continue
        labels = [
            _majority_label(records, scenario.case_id, policy, replicates)
            for policy in policies
        ]
        lines.append(
            f"| {scenario.case_id} | {scenario.category} | "
            + " | ".join(labels)
            + " |"
        )

    lines.extend(
        [
            "",
            "## Sealed-holdout aggregates",
            "",
            "| Policy | Majority errors | Non-unanimous |",
            "|---|---:|---:|",
        ]
    )
    holdout_ids = {
        scenario.case_id for scenario in scenarios if scenario.split == "holdout"
    }
    for policy in policies:
        majority_errors = sum(
            _majority_label(records, case_id, policy, replicates) == "wrong"
            for case_id in holdout_ids
        )
        non_unanimous = sum(
            _non_unanimous(records, case_id, policy)
            for case_id in holdout_ids
        )
        lines.append(
            f"| {policy} | {majority_errors}/8 | {non_unanimous}/8 |"
        )

    lines.extend(
        [
            "",
            "## G0",
            "",
            f"- Decision: **{decision.decision}**",
            f"- Reason code: `{decision.reason}`",
            f"- Selected path: `{decision.selected_path or 'none'}`",
            "",
            "| Predicate/count | Result |",
            "|---|---:|",
        ]
    )
    for name, value in decision.counts.items():
        lines.append(f"| {name} | {value} |")
    if decision.failed_predicates:
        lines.extend(["", "Failed predicates:", ""])
        lines.extend(f"- {predicate}" for predicate in decision.failed_predicates)
    lines.extend(
        [
            "",
            "## Interpretation boundary",
            "",
            "- M0 answers only whether the faithful baseline exhibits stable failures "
            "and whether pre-registered headroom exists.",
            "- It does not estimate population prevalence, statistical significance, "
            "or treatment effectiveness.",
            "- No aware lifecycle, external memory system, or external benchmark item "
            "was implemented in M0.",
            "",
        ]
    )
    return "\n".join(lines)


def _majority_label(
    records: list[dict[str, Any]],
    case_id: str,
    policy: str,
    replicates: list[int],
) -> str:
    cells = [
        record
        for record in records
        if record["case_id"] == case_id and record["policy"] == policy
    ]
    if len(cells) != len(replicates) or any(
        record["correct"] is None for record in cells
    ):
        return "unavailable"
    correct = sum(bool(record["correct"]) for record in cells)
    threshold = 2 if len(replicates) == 3 else 3
    return "correct" if correct >= threshold else "wrong"


def _non_unanimous(
    records: list[dict[str, Any]], case_id: str, policy: str
) -> bool:
    values = [
        record["correct"]
        for record in records
        if record["case_id"] == case_id and record["policy"] == policy
    ]
    return len(set(values)) > 1
