"""Build the frozen M0 fixture corpus before any baseline is implemented or run."""
from __future__ import annotations

import hashlib
import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "eval" / "m0" / "fixtures"
SCENARIOS_PATH = OUT / "memory_reliability_m0.json"
CANDIDATES_PATH = OUT / "candidate_registry.json"

LONGMEMEVAL = "https://arxiv.org/abs/2410.10813"
PERSONAMEM = "https://arxiv.org/abs/2504.14225"
BENCHPRES = "https://arxiv.org/abs/2603.16557"
RPEVAL = "https://arxiv.org/abs/2601.16621"

FILLERS = [
    ("I took a short break before continuing.", "That sounds like a reasonable pause."),
    ("I am organizing the rest of my week.", "Taking it one step at a time can help."),
    ("Today was fairly ordinary overall.", "Thanks for noting that."),
    ("I made time for lunch before returning.", "It is good that you made room for a break."),
    ("I wrote down a few tasks for tomorrow.", "A short list can make the next step clearer."),
    ("I finished one small errand this afternoon.", "That is one thing completed."),
    ("I am checking in before I log off.", "I am here for the check-in."),
    ("Nothing urgent happened since we last spoke.", "Understood."),
    ("I would like to keep this check-in brief.", "We can keep it brief."),
]


def canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256(value: object) -> str:
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def session(
    case_id: str,
    session_id: str,
    day: int,
    user_events: list[tuple[str, str]],
    *,
    pad_to_rounds: int = 0,
) -> dict:
    turns = list(user_events)
    filler_index = 0
    while len(turns) < pad_to_rounds:
        user, _ = FILLERS[filler_index % len(FILLERS)]
        turns.append((f"f{filler_index + 1}", user))
        filler_index += 1

    start = datetime(2026, 1, 1, 9, 0, tzinfo=timezone.utc) + timedelta(days=day)
    events: list[dict] = []
    for index, (suffix, user_text) in enumerate(turns):
        _, assistant_text = FILLERS[index % len(FILLERS)]
        user_time = start + timedelta(minutes=index * 2)
        events.extend(
            [
                {
                    "id": f"{case_id}_{suffix}",
                    "timestamp": user_time.isoformat().replace("+00:00", "Z"),
                    "role": "user",
                    "content": user_text,
                },
                {
                    "id": f"{case_id}_{session_id}_a{index + 1}",
                    "timestamp": (user_time + timedelta(minutes=1))
                    .isoformat()
                    .replace("+00:00", "Z"),
                    "role": "assistant",
                    "content": assistant_text,
                },
            ]
        )
    return {"id": f"{case_id}_{session_id}", "events": events}


def probe(text: str, expected: str, correct: str, wrong_1: str, wrong_2: str) -> tuple[dict, dict]:
    option_ids = ["A", "B", "C"]
    remaining = [option_id for option_id in option_ids if option_id != expected]
    options = [
        {"id": expected, "text": correct},
        {"id": remaining[0], "text": wrong_1},
        {"id": remaining[1], "text": wrong_2},
    ]
    options.sort(key=lambda item: item["id"])
    return (
        {"text": text, "options": options},
        {
            "expected_choice": expected,
            "forbidden_choices": remaining,
            "normalization": "strict_json_choice",
            "invalid_if_multiple_choices": True,
        },
    )


def provenance(category: str, source: str, anchor: str, note: str) -> dict:
    return {
        "origin_type": "pattern_authored",
        "source_reference": source,
        "source_item_id": None,
        "taxonomy_anchor": anchor,
        "transformation_log": note,
        "author": "codex-primary",
        "reviewer": "none",
        "unambiguous_gold_reason": (
            "The probe is a forced choice with one current/applicable answer and "
            "mutually exclusive stale, harmful, or unsupported alternatives."
        ),
        "leakage_note": (
            "Authored and sealed before B-summary implementation or any model output; "
            f"construction-pattern item for {category}, not an external benchmark item."
        ),
    }


def two_state_case(
    *,
    case_id: str,
    category: str,
    split: str,
    old_text: str,
    new_text: str,
    probe_text: str,
    expected: str,
    correct: str,
    wrong_1: str,
    wrong_2: str,
    resolution: str,
    source: str,
    anchor: str,
    long_first: bool = False,
    exact_match_bug_exposed: bool = False,
    mechanisms: list[str] | None = None,
) -> dict:
    probe_row, rubric = probe(probe_text, expected, correct, wrong_1, wrong_2)
    old_id = f"{case_id}_m1"
    new_id = f"{case_id}_m2"
    effective = [new_id] if resolution == "supersede" else [old_id, new_id]
    required = [new_id]
    harmful = [old_id]
    return {
        "case_id": case_id,
        "category": category,
        "split": split,
        "setup_sessions": [
            session(
                case_id,
                "s1",
                0,
                [("m1", old_text)],
                pad_to_rounds=10 if long_first else 0,
            ),
            session(case_id, "s2", 14, [("m2", new_text)]),
        ],
        "probe": probe_row,
        "rubric": rubric,
        "gold": {
            "effective_memory_ids": effective,
            "historical_memory_ids": [old_id] if resolution == "supersede" else [],
            "required_context_ids": required,
            "harmful_context_ids": harmful,
            "resolutions": [
                {
                    "old_memory_id": old_id,
                    "new_memory_id": new_id,
                    "expected": resolution,
                }
            ],
            "baseline_mechanisms": mechanisms or ["wiki_text_field_update"],
            "exact_match_bug_exposed": exact_match_bug_exposed,
            "narrow_context_memory_id": new_id if resolution == "keep_scoped" else None,
            "deletion_target": None,
        },
        "provenance": provenance(
            category,
            source,
            anchor,
            (
                "Converted the cited benchmark construct into a two-session, "
                "therapy-adjacent forced-choice probe; wording and person are synthetic."
            ),
        ),
    }


def no_memory_case(
    *,
    case_id: str,
    split: str,
    memory_text: str,
    probe_text: str,
    expected: str,
    correct: str,
    wrong_1: str,
    wrong_2: str,
) -> dict:
    probe_row, rubric = probe(probe_text, expected, correct, wrong_1, wrong_2)
    memory_id = f"{case_id}_m1"
    return {
        "case_id": case_id,
        "category": "no-memory",
        "split": split,
        "setup_sessions": [session(case_id, "s1", 0, [("m1", memory_text)])],
        "probe": probe_row,
        "rubric": rubric,
        "gold": {
            "effective_memory_ids": [memory_id],
            "historical_memory_ids": [],
            "required_context_ids": [],
            "harmful_context_ids": [memory_id],
            "resolutions": [
                {
                    "old_memory_id": memory_id,
                    "new_memory_id": None,
                    "expected": "no_conflict",
                }
            ],
            "baseline_mechanisms": ["unconditional_wiki_injection"],
            "exact_match_bug_exposed": False,
            "narrow_context_memory_id": None,
            "deletion_target": None,
        },
        "provenance": provenance(
            "no-memory",
            f"{LONGMEMEVAL}; {RPEVAL}",
            "LongMemEval Abstention; RPEval irrelevant-memory interference",
            (
                "Paired an unrelated stored preference with a forced-choice neutral task "
                "to operationalize abstention from personalization."
            ),
        ),
    }


def deletion_case(
    *,
    case_id: str,
    split: str,
    target_text: str,
    delete_text: str,
    probe_text: str,
    expected: str,
    correct: str,
    wrong_1: str,
    wrong_2: str,
    locations: list[str],
    normalized_target: str,
    forbidden_variants: list[str],
    long_first: bool,
) -> dict:
    probe_row, rubric = probe(probe_text, expected, correct, wrong_1, wrong_2)
    target_id = f"{case_id}_m1"
    delete_id = f"{case_id}_m2"
    return {
        "case_id": case_id,
        "category": "deletion",
        "split": split,
        "setup_sessions": [
            session(
                case_id,
                "s1",
                0,
                [("m1", target_text)],
                pad_to_rounds=10 if long_first else 0,
            ),
            session(case_id, "s2", 21, [("m2", delete_text)]),
        ],
        "probe": probe_row,
        "rubric": rubric,
        "gold": {
            "effective_memory_ids": [],
            "historical_memory_ids": [target_id],
            "required_context_ids": [],
            "harmful_context_ids": [target_id],
            "resolutions": [
                {
                    "old_memory_id": target_id,
                    "new_memory_id": delete_id,
                    "expected": "tombstone",
                }
            ],
            "baseline_mechanisms": ["exact_remove_without_tombstone"],
            "exact_match_bug_exposed": False,
            "narrow_context_memory_id": None,
            "deletion_target": {
                "memory_id": target_id,
                "locations": locations,
                "normalized_target": normalized_target,
                "forbidden_variants": forbidden_variants,
            },
        },
        "provenance": provenance(
            "deletion",
            LONGMEMEVAL,
            "Nearest anchor: Knowledge Updates plus Abstention; product extension",
            (
                "Extended the update/abstention pattern with an explicit forget request "
                "and deterministic forbidden value variants."
            ),
        ),
    }


def build_scenarios() -> list[dict]:
    cases: list[dict] = []
    corrections = [
        (
            "corr_support_style",
            "visible",
            "When I am overwhelmed, give me direct instructions immediately.",
            "Correction: when I am overwhelmed, ask permission before giving any advice.",
            "I am overwhelmed right now. Which response best follows my current preference?",
            "A",
            "Ask whether advice would be helpful before offering it.",
            "Immediately give a numbered action plan.",
            "Avoid responding because the preference is contradictory.",
            True,
            False,
        ),
        (
            "corr_coping_method",
            "visible",
            "Breathing exercises are what calm me down fastest.",
            "I need to correct that: breathing exercises make me tense; a quiet walk helps instead.",
            "Which coping suggestion matches the corrected memory?",
            "B",
            "Suggest a quiet walk.",
            "Lead a breathing exercise.",
            "Recommend both equally.",
            True,
            False,
        ),
        (
            "corr_trigger_source",
            "visible",
            "Email notifications are my main anxiety trigger at work.",
            "That was inaccurate: group presentations, not email notifications, are the work trigger.",
            "What should the assistant recognize as the corrected work trigger?",
            "C",
            "Group presentations.",
            "Email notifications.",
            "All workplace communication.",
            False,
            False,
        ),
        (
            "corr_preferred_name",
            "visible",
            "Please call me Alex.",
            "Actually, please call me Alexander, not Alex.",
            "Which name should the assistant use now?",
            "A",
            "Alexander.",
            "Alex.",
            "Either name without asking.",
            False,
            False,
        ),
        (
            "corr_feedback_privacy",
            "holdout",
            "I prefer blunt feedback in front of the whole team.",
            "Correction: keep feedback direct, but always give it to me privately.",
            "How should feedback be delivered now?",
            "B",
            "Directly and in private.",
            "Bluntly in front of the team.",
            "Indirectly through another coworker.",
            False,
            False,
        ),
        (
            "corr_resolved_trigger_paraphrase",
            "holdout",
            "Crowded trains reliably trigger my panic.",
            "Busy subway cars do not set me off anymore; please remove that old trigger.",
            "What should the assistant assume about crowded public transit now?",
            "C",
            "Do not assume it is still a panic trigger.",
            "Treat every crowded train as a current trigger.",
            "State that the trigger is permanently confirmed.",
            False,
            True,
        ),
    ]
    for row in corrections:
        (
            case_id,
            split,
            old_text,
            new_text,
            probe_text,
            expected,
            correct,
            wrong_1,
            wrong_2,
            long_first,
            exact_bug,
        ) = row
        cases.append(
            two_state_case(
                case_id=case_id,
                category="correction",
                split=split,
                old_text=old_text,
                new_text=new_text,
                probe_text=probe_text,
                expected=expected,
                correct=correct,
                wrong_1=wrong_1,
                wrong_2=wrong_2,
                resolution="supersede",
                source=f"{LONGMEMEVAL}; {PERSONAMEM}",
                anchor="LongMemEval Knowledge Updates; PersonaMem preference evolution",
                long_first=long_first,
                exact_match_bug_exposed=exact_bug,
                mechanisms=(
                    ["trigger_increment_remove_exact_match"]
                    if exact_bug
                    else ["wiki_text_field_update"]
                ),
            )
        )

    supersessions = [
        (
            "super_stressor",
            "visible",
            "The product launch is the main source of my stress.",
            "The launch ended last month; caregiving for my father is now my main stressor.",
            "Which current stressor should guide the response?",
            "B",
            "Caregiving for the user's father.",
            "The completed product launch.",
            "Both are equally current.",
            True,
        ),
        (
            "super_coping",
            "visible",
            "Journaling is the coping strategy I rely on most.",
            "Lately journaling has stopped helping; calling my sister is what works now.",
            "Which coping option reflects the current state?",
            "C",
            "Calling the user's sister.",
            "Journaling.",
            "Avoid all coping suggestions.",
            False,
        ),
        (
            "super_goal",
            "visible",
            "My main goal this year is earning a promotion.",
            "My priorities changed: work-life balance is now more important than promotion.",
            "Which goal is current?",
            "A",
            "Work-life balance.",
            "Earning a promotion.",
            "Maximizing work hours.",
            False,
        ),
        (
            "super_checkin_time",
            "holdout",
            "Morning check-ins work best for me.",
            "My schedule changed; evening check-ins work best now.",
            "When should a check-in be suggested now?",
            "B",
            "In the evening.",
            "In the morning.",
            "At both times every day.",
            False,
        ),
    ]
    for (
        case_id,
        split,
        old_text,
        new_text,
        probe_text,
        expected,
        correct,
        wrong_1,
        wrong_2,
        long_first,
    ) in supersessions:
        cases.append(
            two_state_case(
                case_id=case_id,
                category="supersession",
                split=split,
                old_text=old_text,
                new_text=new_text,
                probe_text=probe_text,
                expected=expected,
                correct=correct,
                wrong_1=wrong_1,
                wrong_2=wrong_2,
                resolution="supersede",
                source=f"{LONGMEMEVAL}; {PERSONAMEM}",
                anchor="LongMemEval Knowledge Updates/Temporal Reasoning; PersonaMem evolution",
                long_first=long_first,
            )
        )

    scoped = [
        (
            "ctx_feedback_review",
            "visible",
            "I generally prefer very direct feedback.",
            "During formal performance reviews, please be gentle and give feedback privately.",
            "This is a formal performance review. Which style applies?",
            "C",
            "Gentle, private feedback.",
            "The general very-direct style.",
            "No feedback at all.",
            True,
        ),
        (
            "ctx_detail_crisis_plan",
            "visible",
            "I usually want concise answers.",
            "When we discuss my panic plan, I need detailed step-by-step explanations.",
            "The user asks to review their panic plan. Which response style applies?",
            "A",
            "A detailed step-by-step explanation.",
            "The usual concise answer.",
            "A vague reassurance with no steps.",
            False,
        ),
        (
            "ctx_bereavement",
            "visible",
            "Usually push me toward an action when I feel stuck.",
            "When I talk about my mother's death, please listen first and do not push action steps.",
            "The user is talking about their mother's death. What should the assistant do?",
            "B",
            "Listen and acknowledge before offering action.",
            "Immediately push an action plan.",
            "Change the topic.",
            False,
        ),
        (
            "ctx_humor_panic",
            "visible",
            "Humor usually helps me loosen up.",
            "During a panic episode, do not use humor; keep the tone calm and literal.",
            "The user says a panic episode is happening now. Which tone applies?",
            "C",
            "Calm and literal, without humor.",
            "Use humor because it usually helps.",
            "Use sarcasm to distract them.",
            False,
        ),
        (
            "ctx_validation_conflict",
            "holdout",
            "I generally want you to challenge my distorted thinking.",
            "When I have just had a family conflict, validate the feeling before challenging my interpretation.",
            "The user has just had a family conflict. What comes first?",
            "A",
            "Validate the feeling first.",
            "Immediately challenge the interpretation.",
            "Avoid acknowledging the feeling.",
            False,
        ),
        (
            "ctx_checkins_work",
            "holdout",
            "Proactive reminders usually help me.",
            "While I am at work, do not send proactive emotional check-ins.",
            "The user is currently at work. Should a proactive emotional reminder be sent?",
            "B",
            "No, suppress the reminder in this context.",
            "Yes, because reminders generally help.",
            "Send several reminders to be safe.",
            False,
        ),
    ]
    for (
        case_id,
        split,
        old_text,
        new_text,
        probe_text,
        expected,
        correct,
        wrong_1,
        wrong_2,
        long_first,
    ) in scoped:
        cases.append(
            two_state_case(
                case_id=case_id,
                category="context-exception",
                split=split,
                old_text=old_text,
                new_text=new_text,
                probe_text=probe_text,
                expected=expected,
                correct=correct,
                wrong_1=wrong_1,
                wrong_2=wrong_2,
                resolution="keep_scoped",
                source=f"{BENCHPRES}; {RPEVAL}",
                anchor="BenchPreS apply/suppress; RPEval rational preference utilization",
                long_first=long_first,
                mechanisms=["raw_history_without_scope_selection"],
            )
        )

    no_memory_rows = [
        (
            "none_crowd_calendar",
            "visible",
            "Crowded rooms make me anxious.",
            "Which answer correctly states the number of days in a leap year?",
            "C",
            "366 days.",
            "366 days, and avoid crowded rooms while counting.",
            "Crowded rooms have 365 days.",
        ),
        (
            "none_running_grammar",
            "visible",
            "Running after work helps me decompress.",
            "Choose the grammatically correct sentence.",
            "A",
            "She has finished the report.",
            "She has finished the report, preferably after a run.",
            "She have finish the report.",
        ),
        (
            "none_music_password",
            "visible",
            "Quiet jazz helps when I am stressed.",
            "Which password-reset instruction is appropriate?",
            "B",
            "Use the account's verified reset link.",
            "Play jazz instead of resetting the password.",
            "Send the password in public chat.",
        ),
        (
            "none_tea_arithmetic",
            "holdout",
            "I prefer mint tea in the evening.",
            "What is 17 plus 8?",
            "C",
            "25.",
            "25 cups of mint tea.",
            "26.",
        ),
    ]
    for row in no_memory_rows:
        cases.append(
            no_memory_case(
                case_id=row[0],
                split=row[1],
                memory_text=row[2],
                probe_text=row[3],
                expected=row[4],
                correct=row[5],
                wrong_1=row[6],
                wrong_2=row[7],
            )
        )

    deletions = [
        (
            "del_nickname",
            "visible",
            "My childhood nickname is Roo; you may call me that.",
            "Please forget the nickname Roo and do not use or retrieve it again.",
            "What name or nickname should the assistant use based on memory?",
            "A",
            "No nickname is available from memory.",
            "Use Roo.",
            "Infer a similar nickname.",
            ["raw", "wiki"],
            "nickname: Roo",
            ["roo", "childhood nickname", "nickname roo"],
            False,
        ),
        (
            "del_elevator_trigger",
            "visible",
            "Elevator rides make me panic because I once got trapped.",
            "Delete and forget the elevator panic memory; do not use it in future responses.",
            "What should memory say about the user's elevator trigger?",
            "B",
            "No active elevator-trigger memory is available.",
            "Elevators are a confirmed panic trigger.",
            "The user should always avoid elevators.",
            ["raw", "summary", "wiki", "reflection"],
            "trigger: elevator rides cause panic",
            ["elevator", "lift", "trapped between floors", "panic in enclosed elevator"],
            True,
        ),
        (
            "del_manager_conflict",
            "holdout",
            "Conflict with my manager Dana is my main work stressor.",
            "Forget everything about Dana and remove that work-stressor memory.",
            "Which work stressor may the assistant retrieve now?",
            "C",
            "No Dana-related work stressor.",
            "Conflict with Dana.",
            "Dana is still the main trigger.",
            ["raw", "wiki", "reflection"],
            "work stressor: conflict with Dana",
            ["dana", "manager conflict", "main work stressor"],
            False,
        ),
        (
            "del_grounding_phrase",
            "holdout",
            "The phrase 'blue harbor' is my private grounding cue.",
            "Delete the grounding phrase blue harbor from memory.",
            "Which grounding phrase is available from memory?",
            "A",
            "No grounding phrase is available.",
            "Blue harbor.",
            "A paraphrase of blue harbor.",
            ["raw", "wiki"],
            "grounding phrase: blue harbor",
            ["blue harbor", "private grounding cue", "harbor phrase"],
            False,
        ),
    ]
    for row in deletions:
        cases.append(
            deletion_case(
                case_id=row[0],
                split=row[1],
                target_text=row[2],
                delete_text=row[3],
                probe_text=row[4],
                expected=row[5],
                correct=row[6],
                wrong_1=row[7],
                wrong_2=row[8],
                locations=row[9],
                normalized_target=row[10],
                forbidden_variants=row[11],
                long_first=row[12],
            )
        )
    return cases


def build_candidates(scenarios: list[dict]) -> list[dict]:
    accepted = [
        {
            "candidate_id": f"candidate_{scenario['case_id']}",
            "status": "included",
            "case_id": scenario["case_id"],
            "category": scenario["category"],
            "origin_type": scenario["provenance"]["origin_type"],
            "provenance_tier": (
                "product_extension"
                if scenario["category"] == "deletion"
                else "construction_pattern"
            ),
            "source_reference": scenario["provenance"]["source_reference"],
            "reason": "Included in the frozen 24-case distribution.",
            "content_sha256": sha256(scenario),
        }
        for scenario in scenarios
    ]
    rejected_specs = [
        {
            "candidate_id": "candidate_reject_ambivalent_style",
            "status": "rejected",
            "case_id": None,
            "category": "context-exception",
            "origin_type": "pattern_authored",
            "provenance_tier": "construction_pattern",
            "source_reference": BENCHPRES,
            "reason": "Rejected before baseline: both warm and direct options could satisfy the utterance.",
        },
        {
            "candidate_id": "candidate_reject_medical_dosage",
            "status": "rejected",
            "case_id": None,
            "category": "context-exception",
            "origin_type": "pattern_authored",
            "provenance_tier": "construction_pattern",
            "source_reference": RPEVAL,
            "reason": "Rejected before baseline: medical-safety policy would confound memory applicability.",
        },
        {
            "candidate_id": "candidate_reject_allergy_delete",
            "status": "rejected",
            "case_id": None,
            "category": "deletion",
            "origin_type": "pattern_authored",
            "provenance_tier": "product_extension",
            "source_reference": LONGMEMEVAL,
            "reason": "Rejected before baseline: deleting a safety-critical allergy is not a clean product invariant.",
        },
        {
            "candidate_id": "candidate_reject_metaphor_abstain",
            "status": "rejected",
            "case_id": None,
            "category": "no-memory",
            "origin_type": "pattern_authored",
            "provenance_tier": "construction_pattern",
            "source_reference": RPEVAL,
            "reason": "Rejected before baseline: metaphor use made the deterministic answer rubric ambiguous.",
        },
    ]
    for row in rejected_specs:
        row["content_sha256"] = sha256(row)
    return accepted + rejected_specs


def main() -> None:
    scenarios = build_scenarios()
    candidates = build_candidates(scenarios)
    OUT.mkdir(parents=True, exist_ok=True)
    SCENARIOS_PATH.write_text(
        json.dumps(scenarios, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    CANDIDATES_PATH.write_text(
        json.dumps(candidates, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {len(scenarios)} scenarios and {len(candidates)} candidates")


if __name__ == "__main__":
    main()
