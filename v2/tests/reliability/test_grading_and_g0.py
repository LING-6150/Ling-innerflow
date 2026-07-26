from __future__ import annotations

from pathlib import Path

from innerflow_v2.reliability.gate import Cell, evaluate_g0
from innerflow_v2.reliability.grading import grade_strict_choice
from innerflow_v2.reliability.models import load_scenarios

ROOT = Path(__file__).resolve().parents[2]
SCENARIOS = load_scenarios(
    ROOT / "eval" / "m0" / "fixtures" / "memory_reliability_m0.json"
)


def _cells() -> dict[tuple[str, str, int], Cell]:
    result = {}
    for scenario in SCENARIOS:
        for policy in ("B-summary", "B-full", "B-none"):
            for replicate in range(1, 4):
                sources = (
                    (scenario.gold.narrow_context_memory_id,)
                    if policy == "B-full"
                    and scenario.gold.narrow_context_memory_id is not None
                    else ()
                )
                key = (scenario.case_id, policy, replicate)
                result[key] = Cell(*key, True, sources)
    return result


def _set(
    cells: dict[tuple[str, str, int], Cell],
    case_ids: list[str],
    policy: str,
    correct: bool,
) -> None:
    for case_id in case_ids:
        for replicate in range(1, 4):
            old = cells[(case_id, policy, replicate)]
            cells[(case_id, policy, replicate)] = Cell(
                old.case_id,
                old.policy,
                old.replicate,
                correct,
                old.context_source_ids,
            )


def _common_floor_ids() -> list[str]:
    visible = [s for s in SCENARIOS if s.split == "visible"]
    holdout = [s for s in SCENARIOS if s.split == "holdout"]
    chosen = visible[:5] + holdout[:2]
    assert len({scenario.category for scenario in chosen}) >= 2
    return [scenario.case_id for scenario in chosen]


def test_strict_grader_rejects_markdown_extra_keys_and_multiple_choices():
    scenario = SCENARIOS[0]
    expected = scenario.rubric.expected_choice
    assert grade_strict_choice(
        f'{{"choice":"{expected}"}}', scenario
    ).correct
    assert not grade_strict_choice(
        f'```json\n{{"choice":"{expected}"}}\n```', scenario
    ).correct
    assert not grade_strict_choice(
        f'{{"choice":"{expected}","reason":"x"}}', scenario
    ).correct
    assert not grade_strict_choice('{"choice":["A","B"]}', scenario).correct


def test_g0_path_a_requires_same_scenario_and_replicate_pairs():
    cells = _cells()
    failures = _common_floor_ids()
    _set(cells, failures, "B-summary", False)
    decision = evaluate_g0(SCENARIOS, list(cells.values()))
    assert decision.decision == "GO"
    assert decision.reason == "GO_PATH_A"
    assert decision.counts["Path A majority paired cases"] == "7/24"


def test_g0_path_b_requires_joint_errors_both_categories_and_mechanism_checks():
    cells = _cells()
    failures = [
        "ctx_feedback_review",
        "ctx_detail_crisis_plan",
        "none_crowd_calendar",
        "corr_support_style",
        "super_stressor",
        "corr_feedback_privacy",
        "super_checkin_time",
    ]
    _set(cells, failures, "B-summary", False)
    joint = [
        "ctx_feedback_review",
        "ctx_detail_crisis_plan",
        "none_crowd_calendar",
    ]
    # Four B-full failures leave only three Path A pairs.
    all_joint_or_blocking = [*joint, "corr_support_style"]
    _set(cells, all_joint_or_blocking, "B-full", False)

    decision = evaluate_g0(SCENARIOS, list(cells.values()))
    assert decision.decision == "GO"
    assert decision.reason == "GO_PATH_B"
    assert decision.counts["Path B majority paired cases"] == "3/10"


def test_g0_does_not_sum_disjoint_policy_errors_or_ignore_missing_mechanism():
    cells = _cells()
    failures = _common_floor_ids()
    _set(cells, failures, "B-summary", False)
    # Leave only three Path A pairs; all other summary failures also fail B-full,
    # but strip the Path B mechanism evidence from their B-full cells.
    blocked = failures[3:]
    _set(cells, blocked, "B-full", False)
    for scenario in SCENARIOS:
        if scenario.case_id not in blocked:
            continue
        for replicate in range(1, 4):
            key = (scenario.case_id, "B-full", replicate)
            old = cells[key]
            cells[key] = Cell(
                old.case_id, old.policy, old.replicate, False, ()
            )
            if scenario.category == "no-memory":
                none_key = (scenario.case_id, "B-none", replicate)
                none = cells[none_key]
                cells[none_key] = Cell(
                    none.case_id, none.policy, none.replicate, False, ()
                )
    decision = evaluate_g0(SCENARIOS, list(cells.values()))
    assert decision.decision == "STOP"
    assert decision.reason == "STOP_NO_HEADROOM_PATH"


def test_missing_cell_is_inconclusive_not_a_smaller_denominator():
    cells = list(_cells().values())
    decision = evaluate_g0(SCENARIOS, cells[:-1])
    assert decision.decision == "INCONCLUSIVE"
    assert decision.reason == "INCONCLUSIVE_API_FAILURE"
