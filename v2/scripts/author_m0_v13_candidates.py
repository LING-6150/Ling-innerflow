#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from innerflow_v2.reliability.protocol_v13 import (
    REQUIRED_MEMORY_CATEGORIES,
    CandidateProvenanceV13,
    CandidateV13,
    CounterfactualWorld,
    GoldApplicability,
    ResponseAction,
    SetupMemoryEventV13,
    SignedAuthoringInventory,
    SymmetryCertificate,
    TrackedClaim,
    canonical_sha256,
    load_authoring_inventory,
)


SOURCE_REFERENCES = {
    "longmemeval": "https://github.com/xiaowu0162/LongMemEval",
    "personamem": "https://github.com/bowen-upenn/PersonaMem",
    "benchpres": "https://arxiv.org/abs/2603.16557",
    "rpeval": "https://github.com/XueyangFeng/RPEval",
    "innerflow": "docs/innerflow-memory-reliability-eval-v1.3.md",
}

AUTHORING_PROMPT = """Author one M0 v1.3 candidate in its signed inventory slot.
Use paired worlds with an identical probe and non-memory state. Change exactly
one effective setup-memory event. Do not inspect v1.2 item-level outputs,
baseline results, selection state, gate-world assignment, or treatment code."""

VISIBLE_MATERIALS = [
    "docs/innerflow-memory-reliability-eval-v1.3.md",
    "v2/eval/m0/authoring/M0_V13_CANDIDATE_AUTHORING_BRIEF.md",
    "v2/eval/m0/manifests/M0_V13_SIGNED_AUTHORING_INVENTORY.json",
    "v2/eval/m0/manifests/M0_V13_ACTION_EXECUTOR.json",
    *sorted(SOURCE_REFERENCES.values()),
]


SLOT_BLUEPRINTS = {
    "project_update": (
        "a routine internal project status update",
        "Select the response action for drafting a routine internal project update.",
        "a volunteer event progress update",
    ),
    "tutorial_explanation": (
        "a software tutorial explanation",
        "Select the response action for explaining how to configure a local development tool.",
        None,
    ),
    "itinerary_briefing": (
        "a weekend itinerary briefing",
        "Select the response action for presenting a weekend itinerary the user already approved.",
        None,
    ),
    "peer_review": (
        "private feedback on a colleague's draft",
        "Select the response action for giving private feedback on a colleague's ordinary draft.",
        "private feedback on a club newsletter draft",
    ),
    "planning_disagreement": (
        "a low-stakes planning disagreement",
        "Select the response action for discussing a low-stakes disagreement about a project plan.",
        None,
    ),
    "accountability_reminder": (
        "a routine accountability reminder",
        "Select the response action for giving the user a routine reminder about a self-chosen deadline.",
        None,
    ),
    "routine_setback": (
        "a minor setback in a personal routine",
        "Select the response action after the user says a personal routine slipped this week.",
        "a minor setback in a language-practice routine",
    ),
    "decision_uncertainty": (
        "uncertainty between two safe everyday options",
        "Select the response action after the user says they are undecided between two safe everyday options.",
        None,
    ),
    "creative_block": (
        "a non-urgent creative block",
        "Select the response action after the user reports a non-urgent creative block.",
        None,
    ),
    "meeting_recap": (
        "a recap of an ordinary team meeting",
        "Select the response action for recapping an ordinary team meeting.",
        "a recap of a neighborhood committee meeting",
    ),
    "technical_handoff": (
        "a technical handoff note",
        "Select the response action for preparing a technical handoff note for a teammate.",
        None,
    ),
    "options_comparison": (
        "a comparison of two acceptable tools",
        "Select the response action for comparing two acceptable software tools.",
        None,
    ),
    "performance_reflection": (
        "reflection on an ordinary completed task",
        "Select the response action for discussing the user's reflection on an ordinary completed task.",
        "reflection on a completed community presentation",
    ),
    "boundary_negotiation": (
        "a low-stakes scheduling boundary",
        "Select the response action for wording a low-stakes scheduling boundary.",
        None,
    ),
    "schedule_conflict": (
        "an ordinary calendar conflict",
        "Select the response action for discussing an ordinary calendar conflict.",
        None,
    ),
    "audience_scope": (
        "an update for a non-technical audience",
        "Select the response action for an update that will be read by a non-technical audience.",
        "a museum update for first-time volunteers",
    ),
    "work_personal_scope": (
        "an explanation in a personal rather than work setting",
        "Select the response action for explaining the topic in a personal, non-work setting.",
        None,
    ),
    "urgency_scope": (
        "a time-sensitive but non-emergency request",
        "Select the response action for a time-sensitive but non-emergency request.",
        None,
    ),
    "public_private_scope": (
        "feedback on a teammate's ordinary draft",
        "Select the response action for feedback on a teammate's ordinary draft; either a shared review thread or a private note would be acceptable.",
        "feedback on a hobby-group draft",
    ),
    "work_home_scope": (
        "a conversation at home rather than at work",
        "Select the response action for this conversation in a home rather than work context.",
        None,
    ),
    "celebration_debug_scope": (
        "a celebration after a successful debugging session",
        "Select the response action for a celebration after a successful debugging session.",
        None,
    ),
    "venting_planning_scope": (
        "a response to the user describing a minor setback",
        "Select the response action after the user describes a minor setback and asks for a response.",
        "a response to the user describing a frustrating afternoon",
    ),
    "ideation_decision_scope": (
        "an early ideation turn rather than a decision turn",
        "Select the response action during early ideation before the user is ready to decide.",
        None,
    ),
    "setback_next_scope": (
        "the first response to a minor setback",
        "Select the response action for the first response to a minor, non-safety-related setback.",
        None,
    ),
    "unrelated_scheduling": (
        "scheduling a routine appointment",
        "Select the response action for helping schedule a routine appointment.",
        "scheduling a library-room reservation",
    ),
    "neutral_summary": (
        "summarizing a neutral supplied paragraph",
        "Select the response action for summarizing a neutral paragraph supplied in the current turn.",
        None,
    ),
    "document_organization": (
        "organizing filenames in a supplied document list",
        "Select the response action for organizing filenames from a list supplied in the current turn.",
        None,
    ),
    "casual_greeting": (
        "replying to a casual greeting",
        "Select the response action for replying to a casual greeting with no request for personalization.",
        "replying to a brief good-morning message",
    ),
    "third_party_coordination": (
        "coordinating a neutral message for a third party",
        "Select the response action for coordinating a neutral logistical message for a third party.",
        None,
    ),
    "neutral_checkin": (
        "answering a neutral check-in",
        "Select the response action for answering a neutral check-in that contains no advice request.",
        None,
    ),
    "saved_report_format": (
        "the format of routine progress reports",
        "Select the response action for formatting a new routine progress report.",
        "the format of routine volunteer reports",
    ),
    "saved_reading_format": (
        "the format of reading notes",
        "Select the response action for formatting a new set of reading notes.",
        None,
    ),
    "saved_planning_format": (
        "the format of weekly planning notes",
        "Select the response action for formatting new weekly planning notes.",
        None,
    ),
    "saved_feedback_style": (
        "the tone used for ordinary feedback",
        "Select the response action for giving ordinary, private feedback.",
        "the tone used for feedback on hobby projects",
    ),
    "saved_reminder_style": (
        "the tone used for routine reminders",
        "Select the response action for giving a routine, non-urgent reminder.",
        None,
    ),
    "saved_collaboration_style": (
        "the tone used in ordinary collaboration",
        "Select the response action for an ordinary collaboration message.",
        None,
    ),
}

HISTORY_DETAILS = {
    "project_update": ("the milestone list is already current", "one dependency remains under routine review"),
    "tutorial_explanation": ("the tool is installed on a local machine", "the example uses a disposable sample project"),
    "itinerary_briefing": ("the destinations have already been approved", "transport times are recorded separately"),
    "peer_review": ("the draft is complete enough for ordinary feedback", "the author expects one revision round"),
    "planning_disagreement": ("both plans satisfy the stated deadline", "the disagreement concerns sequencing only"),
    "accountability_reminder": ("the deadline was chosen by the user", "one reminder is scheduled for this week"),
    "routine_setback": ("the routine was missed twice", "the user has resumed it before"),
    "decision_uncertainty": ("both options fit the available budget", "no irreversible choice is required today"),
    "creative_block": ("a rough outline already exists", "the next draft can remain experimental"),
    "meeting_recap": ("the agenda and attendee list are final", "two ordinary follow-up items were recorded"),
    "technical_handoff": ("the current build passes its routine checks", "the receiving teammate has repository access"),
    "options_comparison": ("both tools meet the minimum requirement", "pricing has already been checked"),
    "performance_reflection": ("the task finished without an incident", "the user wants to note one lesson"),
    "boundary_negotiation": ("the proposed time is flexible", "both people have an alternate slot"),
    "schedule_conflict": ("the two events overlap by thirty minutes", "either event can be moved safely"),
    "audience_scope": ("the audience knows the project goal", "specialist terminology is optional"),
    "work_personal_scope": ("the topic appears outside office hours", "no workplace policy governs the reply"),
    "urgency_scope": ("the response is useful today", "there is no emergency or safety deadline"),
    "public_private_scope": ("the draft is ordinary and non-confidential", "either review channel is available"),
    "work_home_scope": ("the conversation concerns an everyday household task", "no manager or coworker is involved"),
    "celebration_debug_scope": ("the defect has already been fixed", "the user is marking the completed work"),
    "venting_planning_scope": ("the setback is minor and already contained", "the user has not specified a preferred support mode"),
    "ideation_decision_scope": ("several ideas remain viable", "no option must be chosen in this turn"),
    "setback_next_scope": ("the immediate problem is no longer growing", "both reflection and action remain possible"),
    "unrelated_scheduling": ("the appointment type is routine", "two ordinary time slots are available"),
    "neutral_summary": ("the supplied paragraph is factual", "the user has not requested a personal style"),
    "document_organization": ("the filenames are supplied in the current turn", "the files contain no sensitive material"),
    "casual_greeting": ("the message contains only a greeting", "no advice or task request is present"),
    "third_party_coordination": ("the third party needs one logistical update", "the delivery channel is already chosen"),
    "neutral_checkin": ("the check-in asks for a brief status", "no support preference is stated"),
    "saved_report_format": ("the report uses already verified figures", "the reporting period has ended"),
    "saved_reading_format": ("the reading list is complete", "the notes concern non-sensitive material"),
    "saved_planning_format": ("the weekly tasks are already selected", "the plan covers an ordinary week"),
    "saved_feedback_style": ("the feedback concerns a finished draft", "no confidential issue is involved"),
    "saved_reminder_style": ("the reminder concerns a self-chosen task", "the due date remains flexible"),
    "saved_collaboration_style": ("the collaboration is routine", "both participants already share the same facts"),
}


IRRELEVANT_CLAIMS = {
    ("unrelated_scheduling", "primary"): (
        "For restaurant suggestions, prioritize quiet places.",
        "For exercise suggestions, prioritize outdoor activities.",
    ),
    ("unrelated_scheduling", "reserve"): (
        "For photography suggestions, prioritize black-and-white projects.",
        "For book suggestions, prioritize short essay collections.",
    ),
    ("neutral_summary", "primary"): (
        "For garden suggestions, prioritize plants that flower in spring.",
        "For music suggestions, prioritize instrumental tracks.",
    ),
    ("document_organization", "primary"): (
        "For hiking suggestions, prioritize short shaded trails.",
        "For tea suggestions, prioritize smoky flavors.",
    ),
    ("casual_greeting", "primary"): (
        "For travel packing lists, group items by bag.",
        "For recipe suggestions, avoid overly sweet desserts.",
    ),
    ("casual_greeting", "reserve"): (
        "For museum suggestions, prioritize places with audio guides.",
        "For cycling suggestions, prioritize scenic routes.",
    ),
    ("third_party_coordination", "primary"): (
        "For movie suggestions, prioritize films with subtitles.",
        "For desk setup suggestions, prioritize warm lighting.",
    ),
    ("neutral_checkin", "primary"): (
        "For podcast suggestions, prioritize interview formats.",
        "For houseplant suggestions, prioritize succulents.",
    ),
}


def _sha(value: object) -> str:
    if isinstance(value, str):
        return hashlib.sha256(value.encode("utf-8")).hexdigest()
    return canonical_sha256(value)


def _event_semantics(events: list[SetupMemoryEventV13]) -> list[dict]:
    semantics = []
    for event in events:
        payload = event.model_dump(mode="json")
        payload.pop("event_id")
        payload.pop("claim_ids")
        payload.pop("delete_target_claim_id")
        semantics.append(payload)
    return semantics


PRIMARY_HISTORY_BEATS = (
    "The assistant acknowledges the earlier note about {subject}.",
    "The user supplies this neutral background fact: {detail_a}.",
    "The assistant records {detail_a} without inferring a response preference.",
    "The user adds a separate logistical fact: {detail_b}.",
    "The assistant confirms {detail_b} and asks no leading question.",
    "The user says {subject} has no urgent deadline.",
    "The assistant confirms that {subject} can follow its ordinary process.",
    "The user explains that {detail_a} identifies the relevant setting.",
    "The assistant keeps both frozen response approaches open for {subject}.",
    "The user notes that {detail_b} will remain unchanged.",
    "The assistant records {detail_b} without recommending a format or tone.",
    "The user confirms that the request concerns {subject}, not a safety decision.",
    "The assistant acknowledges the low-stakes boundary around {subject}.",
    "The user says {detail_a} is already settled.",
    "The assistant marks {detail_a} settled without selecting an action.",
    "The user confirms that {detail_b} creates no additional constraint.",
    "The assistant records that both ordinary response approaches remain feasible for {subject}.",
    "The user says the concrete request about {subject} will arrive later.",
    "The assistant waits for the request about {subject} without choosing a response action.",
)

RESERVE_HISTORY_BEATS = (
    "The assistant opens a neutral working note about {subject}.",
    "The user describes a different workflow detail: {detail_b}.",
    "The assistant mirrors {detail_b} without suggesting a presentation style.",
    "The user identifies this resolved dependency: {detail_a}.",
    "The assistant marks {detail_a} complete.",
    "The user says {subject} may be revisited in an ordinary follow-up.",
    "The assistant records the follow-up about {subject} without choosing a tone or detail level.",
    "The user explains that {detail_b} is stable.",
    "The assistant confirms that no update to {detail_b} is pending.",
    "The user says {subject} is useful but not urgent.",
    "The assistant acknowledges the priority of {subject} without selecting an action.",
    "The user states that {detail_a} can stay as recorded.",
    "The assistant preserves {detail_a} without moving the conversation.",
    "The user says either ordinary response approach remains feasible for {subject}.",
    "The assistant confirms that the neutral state of {subject} does not decide between them.",
    "The user rules out confidential or safety-sensitive content in {subject}.",
    "The assistant notes the low-stakes constraint around {subject}.",
    "The user says the concrete request involving {detail_b} will arrive next.",
    "The assistant waits for {detail_b} without selecting an action in advance.",
)


def _filler_events(
    candidate_id: str,
    subject: str,
    details: tuple[str, str],
    narrative_variant: str,
    *,
    start: int,
    count: int,
) -> list[SetupMemoryEventV13]:
    beats = (
        RESERVE_HISTORY_BEATS
        if narrative_variant == "reserve"
        else PRIMARY_HISTORY_BEATS
    )
    return [
        SetupMemoryEventV13(
            event_id=f"{candidate_id}-filler-{start + index}",
            sequence_index=start + index,
            role="assistant" if (start + index) % 2 else "user",
            operation="observe",
            content=beats[start + index - 1].format(
                subject=subject,
                detail_a=details[0],
                detail_b=details[1],
            ),
        )
        for index in range(count)
    ]


def _action_claim(action: ResponseAction, subject: str) -> str:
    return {
        ResponseAction.USE_CONCISE_DETAIL: (
            f"For {subject}, use at most three compact bullets and omit background."
        ),
        ResponseAction.USE_EXPANDED_DETAIL: (
            f"For {subject}, include context, reasoning, and step-by-step detail."
        ),
        ResponseAction.USE_GENTLE_PRIVATE: (
            f"For {subject}, use a gentle private tone and soften criticism."
        ),
        ResponseAction.USE_DIRECT_TONE: (
            f"For {subject}, be direct and state the main point plainly."
        ),
        ResponseAction.ASK_PERMISSION: (
            f"For {subject}, ask whether suggestions are wanted before giving advice."
        ),
        ResponseAction.GIVE_DIRECT_STEPS: (
            f"For {subject}, give concrete next steps immediately without first asking permission."
        ),
    }[action]


def _surface_forms(claim: str) -> list[str]:
    return [claim, claim.removesuffix(".")]


def _provenance(
    assignment,
    *,
    source_reference: str,
) -> CandidateProvenanceV13:
    return CandidateProvenanceV13(
        origin_type=(
            "construction_pattern"
            if assignment.provenance_tier == "E"
            else "product_extension"
        ),
        source_reference=source_reference,
        taxonomy_anchors=assignment.taxonomy_anchors,
        transformation_log=(
            "Authored from the named taxonomy pattern with new people, events, "
            "wording, action binding, and probe; no benchmark item was copied."
            if assignment.provenance_tier == "E"
            else "Authored from the disclosed InnerFlow memory-lifecycle product invariant."
        ),
        author="codex-context-isolated-author",
        reviewer="pending-whole-pool-review",
        unambiguous_gold_reason=(
            "The world-local current-effective or deleted claim explicitly binds "
            "one frozen downstream action while the probe supplies no competing norm."
        ),
        leakage_note=(
            "The author used public taxonomy descriptions and the frozen v1.3 "
            "protocol, but not v1.2 item-level outputs or model results."
        ),
    )


def _required_candidate(assignment, subject: str, probe: str, variant: str) -> CandidateV13:
    row_actions = {
        "detail": (
            ResponseAction.USE_CONCISE_DETAIL,
            ResponseAction.USE_EXPANDED_DETAIL,
        ),
        "tone": (
            ResponseAction.USE_GENTLE_PRIVATE,
            ResponseAction.USE_DIRECT_TONE,
        ),
        "support": (
            ResponseAction.ASK_PERMISSION,
            ResponseAction.GIVE_DIRECT_STEPS,
        ),
    }
    actions = row_actions[assignment.action_band]
    candidate_id = assignment.candidate_id
    details = HISTORY_DETAILS[assignment.situation_slot]
    claim_ids = (f"{candidate_id}-claim-a", f"{candidate_id}-claim-b")
    claims = tuple(_action_claim(action, subject) for action in actions)
    old_claim_id = f"{candidate_id}-prior-claim"
    old_claim = {
        "correction": (
            f"For {subject}, always use one medium-length paragraph; "
            "this was recorded as a factual instruction."
        ),
        "supersession": (
            f"Through last month, my valid preference for {subject} was "
            "one medium-length paragraph."
        ),
        "context-exception": (
            f"Across other contexts involving {subject}, use a balanced default."
        ),
    }[assignment.category]
    context_event = SetupMemoryEventV13(
        event_id=f"{candidate_id}-prior-event",
        sequence_index=0,
        role="user",
        operation="observe",
        content=old_claim,
        claim_ids=[old_claim_id],
    )
    fillers = _filler_events(
        candidate_id,
        subject,
        details,
        variant,
        start=1,
        count=8,
    )
    post_update_fillers = _filler_events(
        candidate_id,
        subject,
        details,
        variant,
        start=10,
        count=10,
    )
    operation = {
        "correction": "correct",
        "supersession": "supersede",
        "context-exception": "scope",
    }[assignment.category]
    prefix = {
        "correction": (
            "Correction: the earlier single-paragraph instruction was mistaken "
            "and should not be treated as true."
        ),
        "supersession": (
            "Starting today, replace the formerly valid preference with this "
            "new version."
        ),
        "context-exception": "For this specific context only, apply this exception.",
    }[assignment.category]

    worlds = []
    for world_id, action, claim_id, claim in zip(
        ("world_a", "world_b"),
        actions,
        claim_ids,
        claims,
        strict=True,
    ):
        events = [
            context_event,
            *fillers,
            SetupMemoryEventV13(
                event_id=f"{candidate_id}-effective-event",
                sequence_index=9,
                role="user",
                operation=operation,
                content=f"{prefix} {claim}",
                claim_ids=[claim_id],
            ),
            *post_update_fillers,
        ]
        worlds.append(
            CounterfactualWorld(
                world_id=world_id,
                probe=probe,
                non_memory_state={
                    "task_description": subject,
                    "scenario_variant": variant,
                    "task_safety": "ordinary_non_safety_sensitive",
                },
                setup_memory_events=events,
                effective_claim=claim,
                applicable_claim_id=claim_id,
                gold_response_action=action,
            )
        )

    tracked_claims = [
        TrackedClaim(
            claim_id=claim_ids[index],
            canonical_value=claims[index],
            source_event_ids=[f"{candidate_id}-effective-event"],
            gold_applicability=GoldApplicability.CURRENT_EFFECTIVE,
            surface_forms=_surface_forms(claims[index]),
            active_cues=["current preference", "specific context", "correcting"],
            historical_cues=["used to", "previously", "earlier preference"],
        )
        for index in range(2)
    ]
    tracked_claims.append(
        TrackedClaim(
            claim_id=old_claim_id,
            canonical_value=old_claim,
            source_event_ids=[f"{candidate_id}-prior-event"],
            gold_applicability=(
                GoldApplicability.OUT_OF_SCOPE
                if assignment.category == "context-exception"
                else GoldApplicability.HISTORICAL
            ),
            surface_forms=_surface_forms(old_claim),
            active_cues=["current"],
            historical_cues=["earlier", "previously", "usually"],
        )
    )
    source_reference = "; ".join(
        SOURCE_REFERENCES[anchor] for anchor in assignment.taxonomy_anchors
    )
    return CandidateV13(
        candidate_id=candidate_id,
        category=assignment.category,
        action_band=assignment.action_band,
        situation_slot=assignment.situation_slot,
        provenance_tier=assignment.provenance_tier,
        provenance_reference=source_reference,
        provenance_artifact=_provenance(
            assignment,
            source_reference=source_reference,
        ),
        underlying_event_fingerprint=_sha(
            _event_semantics(worlds[0].setup_memory_events)
        ),
        probe_template_fingerprint=_sha({"probe": probe}),
        semantic_overlap_fingerprint=_sha(
            {
                "subject": subject,
                "claims": claims,
            }
        ),
        authoring_task_id=f"m0v13-author-{candidate_id}",
        authoring_prompt_sha256=_sha(AUTHORING_PROMPT),
        visible_materials_sha256=_sha(VISIBLE_MATERIALS),
        worlds=worlds,
        tracked_claims=tracked_claims,
        symmetry_certificate=SymmetryCertificate(
            controlled_claim=f"Current action preference for {subject}.",
            action_a_binding=actions[0].value,
            action_b_binding=actions[1].value,
            both_plausible_and_safe_reason=(
                "Both frozen actions are ordinary, safe ways to handle this "
                "low-stakes task when no memory preference is supplied."
            ),
            no_non_memory_preference_reason=(
                "The probe states the task and scope but contains no wording "
                "that selects either action."
            ),
            reviewer_id="pending-whole-pool-review",
            disposition="pass",
        ),
    )


def _no_memory_candidate(assignment, subject: str, probe: str, variant: str) -> CandidateV13:
    candidate_id = assignment.candidate_id
    details = HISTORY_DETAILS[assignment.situation_slot]
    claims = IRRELEVANT_CLAIMS[(assignment.situation_slot, variant)]
    claim_ids = (f"{candidate_id}-irrelevant-a", f"{candidate_id}-irrelevant-b")
    worlds = []
    for world_id, claim_id, claim in zip(
        ("variant_a", "variant_b"),
        claim_ids,
        claims,
        strict=True,
    ):
        events = [
            SetupMemoryEventV13(
                event_id=f"{candidate_id}-irrelevant-event",
                sequence_index=0,
                role="user",
                operation="observe",
                content=claim,
                claim_ids=[claim_id],
            ),
            *_filler_events(
                candidate_id,
                subject,
                details,
                variant,
                start=1,
                count=19,
            ),
        ]
        worlds.append(
            CounterfactualWorld(
                world_id=world_id,
                probe=probe,
                non_memory_state={
                    "task_description": subject,
                    "scenario_variant": variant,
                    "current_domain": subject,
                },
                setup_memory_events=events,
                effective_claim=claim,
                applicable_claim_id=None,
                gold_response_action=ResponseAction.USE_UNPERSONALIZED_DEFAULT,
            )
        )
    source_reference = "; ".join(
        SOURCE_REFERENCES[anchor] for anchor in assignment.taxonomy_anchors
    )
    return CandidateV13(
        candidate_id=candidate_id,
        category=assignment.category,
        action_band=assignment.action_band,
        situation_slot=assignment.situation_slot,
        provenance_tier=assignment.provenance_tier,
        provenance_reference=source_reference,
        provenance_artifact=_provenance(
            assignment,
            source_reference=source_reference,
        ),
        underlying_event_fingerprint=_sha(
            _event_semantics(worlds[0].setup_memory_events)
        ),
        probe_template_fingerprint=_sha({"probe": probe}),
        semantic_overlap_fingerprint=_sha(
            {"subject": subject, "irrelevant_claims": claims}
        ),
        authoring_task_id=f"m0v13-author-{candidate_id}",
        authoring_prompt_sha256=_sha(AUTHORING_PROMPT),
        visible_materials_sha256=_sha(VISIBLE_MATERIALS),
        worlds=worlds,
        tracked_claims=[
            TrackedClaim(
                claim_id=claim_ids[index],
                canonical_value=claims[index],
                source_event_ids=[f"{candidate_id}-irrelevant-event"],
                gold_applicability=GoldApplicability.IRRELEVANT,
                surface_forms=_surface_forms(claims[index]),
                active_cues=["preference"],
                historical_cues=[],
            )
            for index in range(2)
        ],
    )


def _deletion_candidate(assignment, subject: str, probe: str, variant: str) -> CandidateV13:
    candidate_id = assignment.candidate_id
    details = HISTORY_DETAILS[assignment.situation_slot]
    action_pair = (
        (
            ResponseAction.USE_CONCISE_DETAIL,
            ResponseAction.USE_EXPANDED_DETAIL,
        )
        if assignment.action_band == "detail"
        else (
            ResponseAction.USE_GENTLE_PRIVATE,
            ResponseAction.USE_DIRECT_TONE,
        )
    )
    action = action_pair[
        sorted(
            item.situation_slot
            for item in _CURRENT_INVENTORY.assignments
            if item.category == "deletion"
            and item.action_band == assignment.action_band
            and item.role == "primary"
        ).index(assignment.situation_slot)
        % 2
    ]
    claim_id = f"{candidate_id}-deleted-claim"
    claim = _action_claim(action, subject)
    stored_event = SetupMemoryEventV13(
        event_id=f"{candidate_id}-stored-event",
        sequence_index=0,
        role="user",
        operation="observe",
        content=claim,
        claim_ids=[claim_id],
    )
    pre_events = [
        stored_event,
        *_filler_events(
            candidate_id,
            subject,
            details,
            variant,
            start=1,
            count=19,
        ),
    ]
    post_events = [
        *pre_events,
        SetupMemoryEventV13(
            event_id=f"{candidate_id}-delete-event",
            sequence_index=20,
            role="user",
            operation="delete",
            content=f"Forget my saved preference about {subject}.",
            delete_target_claim_id=claim_id,
        ),
    ]
    source_reference = "; ".join(
        SOURCE_REFERENCES[anchor] for anchor in assignment.taxonomy_anchors
    )
    return CandidateV13(
        candidate_id=candidate_id,
        category=assignment.category,
        action_band=assignment.action_band,
        situation_slot=assignment.situation_slot,
        provenance_tier=assignment.provenance_tier,
        provenance_reference=source_reference,
        provenance_artifact=_provenance(
            assignment,
            source_reference=source_reference,
        ),
        underlying_event_fingerprint=_sha(
            _event_semantics(post_events)
        ),
        probe_template_fingerprint=_sha({"probe": probe}),
        semantic_overlap_fingerprint=_sha(
            {"subject": subject, "deleted_claim": claim}
        ),
        authoring_task_id=f"m0v13-author-{candidate_id}",
        authoring_prompt_sha256=_sha(AUTHORING_PROMPT),
        visible_materials_sha256=_sha(VISIBLE_MATERIALS),
        worlds=[
            CounterfactualWorld(
                world_id="pre_delete",
                probe=probe,
                non_memory_state={
                    "task_description": subject,
                    "scenario_variant": variant,
                    "task_safety": "ordinary_non_safety_sensitive",
                },
                setup_memory_events=pre_events,
                effective_claim=claim,
                applicable_claim_id=claim_id,
                gold_response_action=action,
            ),
            CounterfactualWorld(
                world_id="post_delete",
                probe=probe,
                non_memory_state={
                    "task_description": subject,
                    "scenario_variant": variant,
                    "task_safety": "ordinary_non_safety_sensitive",
                },
                setup_memory_events=post_events,
                effective_claim=None,
                applicable_claim_id=None,
                gold_response_action=ResponseAction.USE_UNPERSONALIZED_DEFAULT,
            ),
        ],
        tracked_claims=[
            TrackedClaim(
                claim_id=claim_id,
                canonical_value=claim,
                source_event_ids=[stored_event.event_id],
                gold_applicability=GoldApplicability.DELETED,
                surface_forms=_surface_forms(claim),
                active_cues=["saved preference", "currently"],
                historical_cues=["forgot", "deleted", "no longer"],
            )
        ],
        deletion_storage_locations=["summary", "wiki"],
    )


def _author_candidate(assignment) -> CandidateV13:
    primary_subject, primary_probe, reserve_subject = SLOT_BLUEPRINTS[
        assignment.situation_slot
    ]
    is_reserve = assignment.role == "reserve"
    subject = reserve_subject if is_reserve else primary_subject
    if subject is None:
        raise ValueError(f"{assignment.candidate_id}: missing reserve blueprint")
    probe = (
        f"For the current task—{subject}—which frozen response action should govern the reply?"
        if is_reserve
        else primary_probe
    )
    variant = "reserve" if is_reserve else "primary"
    if assignment.category in REQUIRED_MEMORY_CATEGORIES:
        return _required_candidate(assignment, subject, probe, variant)
    if assignment.category == "no-memory":
        return _no_memory_candidate(assignment, subject, probe, variant)
    return _deletion_candidate(assignment, subject, probe, variant)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Author the frozen M0 v1.3 candidate inventory offline."
    )
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    global _CURRENT_INVENTORY
    _CURRENT_INVENTORY = load_authoring_inventory(args.inventory)
    candidates = [
        _author_candidate(assignment)
        for assignment in _CURRENT_INVENTORY.assignments
    ]
    if [candidate.candidate_id for candidate in candidates] != (
        _CURRENT_INVENTORY.candidate_ids
    ):
        raise ValueError("authored candidate order/identity drift")
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(
            [candidate.model_dump(mode="json") for candidate in candidates],
            stream,
            ensure_ascii=False,
            indent=2,
        )
        stream.write("\n")


_CURRENT_INVENTORY: SignedAuthoringInventory


if __name__ == "__main__":
    main()
