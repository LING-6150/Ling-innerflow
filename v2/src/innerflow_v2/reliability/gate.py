from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from typing import Literal

from innerflow_v2.reliability.models import ReliabilityScenario

GateReason = Literal[
    "GO_PATH_A",
    "GO_PATH_B",
    "STOP_COMMON_FLOOR",
    "STOP_NO_HEADROOM_PATH",
    "INCONCLUSIVE_API_FAILURE",
    "INCONCLUSIVE_MODEL_VARIANCE",
]


@dataclass(frozen=True)
class Cell:
    case_id: str
    policy: str
    replicate: int
    correct: bool | None
    context_source_ids: tuple[str, ...] = ()


@dataclass
class G0Decision:
    decision: Literal["GO", "STOP", "INCONCLUSIVE", "NEEDS_FIVE_RUNS"]
    reason: GateReason | Literal["NEEDS_FIVE_RUNS"]
    failed_predicates: list[str] = field(default_factory=list)
    counts: dict[str, str] = field(default_factory=dict)
    selected_path: Literal["A", "B"] | None = None


def evaluate_g0(
    scenarios: list[ReliabilityScenario], cells: list[Cell]
) -> G0Decision:
    expected_policies = {"B-summary", "B-full", "B-none"}
    replicate_ids = sorted({cell.replicate for cell in cells})
    if len(replicate_ids) not in {3, 5}:
        raise ValueError("G0 requires exactly three or five complete replicates")
    expected_keys = {
        (scenario.case_id, policy, replicate)
        for scenario in scenarios
        for policy in expected_policies
        for replicate in replicate_ids
    }
    by_key = {(cell.case_id, cell.policy, cell.replicate): cell for cell in cells}
    if set(by_key) != expected_keys or any(cell.correct is None for cell in cells):
        return G0Decision(
            "INCONCLUSIVE",
            "INCONCLUSIVE_API_FAILURE",
            ["one or more policy/scenario/replicate cells are unavailable"],
        )

    required_majority = 2 if len(replicate_ids) == 3 else 3
    required_replicates = 2 if len(replicate_ids) == 3 else 3
    scenario_by_id = {scenario.case_id: scenario for scenario in scenarios}

    def majority(case_id: str, policy: str) -> bool:
        return (
            sum(
                bool(by_key[(case_id, policy, replicate)].correct)
                for replicate in replicate_ids
            )
            >= required_majority
        )

    def unstable(case_id: str, policy: str) -> bool:
        correct_count = sum(
            bool(by_key[(case_id, policy, replicate)].correct)
            for replicate in replicate_ids
        )
        if len(replicate_ids) == 3:
            return correct_count not in {0, 3}
        return correct_count not in {0, 1, 4, 5}

    summary_majority_failures = {
        scenario.case_id
        for scenario in scenarios
        if not majority(scenario.case_id, "B-summary")
    }
    visible = [scenario for scenario in scenarios if scenario.split == "visible"]
    holdout = [scenario for scenario in scenarios if scenario.split == "holdout"]

    visible_rep_pass = sum(
        sum(
            not bool(by_key[(scenario.case_id, "B-summary", replicate)].correct)
            for scenario in visible
        )
        >= 5
        for replicate in replicate_ids
    )
    holdout_rep_pass = sum(
        sum(
            not bool(by_key[(scenario.case_id, "B-summary", replicate)].correct)
            for scenario in holdout
        )
        >= 2
        for replicate in replicate_ids
    )
    failure_categories = {
        scenario_by_id[case_id].category for case_id in summary_majority_failures
    }
    common_failures: list[str] = []
    if visible_rep_pass < required_replicates:
        common_failures.append(
            f"visible B-summary error floor failed in {visible_rep_pass}/{len(replicate_ids)} replicates"
        )
    if holdout_rep_pass < required_replicates:
        common_failures.append(
            f"holdout B-summary error floor failed in {holdout_rep_pass}/{len(replicate_ids)} replicates"
        )
    if len(failure_categories) < 2:
        common_failures.append("B-summary majority failures span fewer than two categories")
    if failure_categories == {"deletion"}:
        common_failures.append("B-summary majority failures are deletion-only")

    path_a_cases = {
        scenario.case_id
        for scenario in scenarios
        if not majority(scenario.case_id, "B-summary")
        and majority(scenario.case_id, "B-full")
    }
    path_a_rep_pass = sum(
        sum(
            not bool(by_key[(scenario.case_id, "B-summary", replicate)].correct)
            and bool(by_key[(scenario.case_id, "B-full", replicate)].correct)
            for scenario in scenarios
        )
        >= 4
        for replicate in replicate_ids
    )
    path_a_evidence = (
        len(path_a_cases) >= 4 and path_a_rep_pass >= required_replicates
    )

    applicability = [
        scenario
        for scenario in scenarios
        if scenario.category in {"context-exception", "no-memory"}
    ]

    def path_b_mechanism(scenario: ReliabilityScenario, replicate: int) -> bool:
        if scenario.category == "no-memory":
            return bool(by_key[(scenario.case_id, "B-none", replicate)].correct)
        narrow_id = scenario.gold.narrow_context_memory_id
        return bool(narrow_id) and narrow_id in by_key[
            (scenario.case_id, "B-full", replicate)
        ].context_source_ids

    path_b_cases: set[str] = set()
    for scenario in applicability:
        if (
            not majority(scenario.case_id, "B-summary")
            and not majority(scenario.case_id, "B-full")
        ):
            mechanism_count = sum(
                path_b_mechanism(scenario, replicate)
                for replicate in replicate_ids
            )
            if mechanism_count >= required_majority:
                path_b_cases.add(scenario.case_id)
    path_b_categories = {
        scenario_by_id[case_id].category for case_id in path_b_cases
    }
    path_b_rep_pass = sum(
        (
            lambda supporting: len(supporting) >= 3
            and {
                scenario_by_id[case_id].category for case_id in supporting
            }
            == {"context-exception", "no-memory"}
        )(
            {
                scenario.case_id
                for scenario in applicability
                if not bool(
                    by_key[(scenario.case_id, "B-summary", replicate)].correct
                )
                and not bool(
                    by_key[(scenario.case_id, "B-full", replicate)].correct
                )
                and path_b_mechanism(scenario, replicate)
            }
        )
        for replicate in replicate_ids
    )
    path_b_evidence = (
        len(path_b_cases) >= 3
        and path_b_categories == {"context-exception", "no-memory"}
        and path_b_rep_pass >= required_replicates
    )

    instability = {
        policy: sum(
            unstable(scenario.case_id, policy) for scenario in scenarios
        )
        for policy in expected_policies
    }
    path_a_variance_ok = all(
        instability[policy] <= 6 for policy in ("B-summary", "B-full")
    )
    path_b_variance_ok = all(
        instability[policy] <= 6
        for policy in ("B-summary", "B-full", "B-none")
    )
    counts = {
        "B-summary visible error floor replicates": f"{visible_rep_pass}/{len(replicate_ids)}",
        "B-summary holdout error floor replicates": f"{holdout_rep_pass}/{len(replicate_ids)}",
        "B-summary majority failures": f"{len(summary_majority_failures)}/24",
        "Path A majority paired cases": f"{len(path_a_cases)}/24",
        "Path A replicate threshold passes": f"{path_a_rep_pass}/{len(replicate_ids)}",
        "Path B majority paired cases": f"{len(path_b_cases)}/10",
        "Path B replicate threshold passes": f"{path_b_rep_pass}/{len(replicate_ids)}",
        **{
            f"{policy} unstable scenarios": f"{count}/24"
            for policy, count in sorted(instability.items())
        },
    }

    if len(replicate_ids) == 3 and (
        (path_a_evidence and not path_a_variance_ok)
        or (path_b_evidence and not path_b_variance_ok)
    ):
        return G0Decision(
            "NEEDS_FIVE_RUNS",
            "NEEDS_FIVE_RUNS",
            ["selected evidence path exceeds the 6/24 non-unanimous ceiling"],
            counts,
        )
    if len(replicate_ids) == 5 and (
        (path_a_evidence and not path_a_variance_ok)
        or (path_b_evidence and not path_b_variance_ok)
    ):
        return G0Decision(
            "INCONCLUSIVE",
            "INCONCLUSIVE_MODEL_VARIANCE",
            ["more than 6/24 scenarios remain unstable on every evidence path"],
            counts,
        )
    if common_failures:
        return G0Decision(
            "STOP", "STOP_COMMON_FLOOR", common_failures, counts
        )
    if path_a_evidence and path_a_variance_ok:
        return G0Decision("GO", "GO_PATH_A", [], counts, "A")
    if path_b_evidence and path_b_variance_ok:
        return G0Decision("GO", "GO_PATH_B", [], counts, "B")
    path_failures = []
    if not path_a_evidence:
        path_failures.append("Path A paired headroom predicates failed")
    if not path_b_evidence:
        path_failures.append("Path B paired applicability predicates failed")
    return G0Decision(
        "STOP", "STOP_NO_HEADROOM_PATH", path_failures, counts
    )
