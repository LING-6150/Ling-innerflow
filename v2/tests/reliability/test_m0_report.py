from __future__ import annotations

from pathlib import Path

from innerflow_v2.reliability.gate import G0Decision
from innerflow_v2.reliability.models import load_scenarios
from innerflow_v2.reliability.report import render_m0_report

ROOT = Path(__file__).resolve().parents[2]


def test_report_is_count_first_and_does_not_disclose_holdout_item_ids():
    scenarios = load_scenarios(
        ROOT / "eval/m0/fixtures/memory_reliability_m0.json"
    )
    records = [
        {
            "case_id": scenario.case_id,
            "split": scenario.split,
            "category": scenario.category,
            "policy": policy,
            "replicate": replicate,
            "correct": not (
                policy == "B-summary"
                and scenario.split == "visible"
                and scenario.case_id == "corr_support_style"
            ),
        }
        for scenario in scenarios
        for policy in ("B-summary", "B-full", "B-none")
        for replicate in (1, 2, 3)
    ]
    report = render_m0_report(
        scenarios,
        records,
        G0Decision(
            "STOP",
            "STOP_COMMON_FLOOR",
            ["visible floor failed"],
            {"B-summary majority failures": "1/24"},
        ),
        raw_sha256="a" * 64,
        model="test-model",
    )
    assert "1/16" in report
    assert "1/24" in report
    assert "%" not in report
    assert "corr_support_style" in report
    for scenario in scenarios:
        if scenario.split == "holdout":
            assert scenario.case_id not in report
