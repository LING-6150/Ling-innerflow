from __future__ import annotations

import json
from typing import Iterable, Protocol

from innerflow_v2.reliability.models import Probe


class PromptMessage(Protocol):
    role: str
    content: str


def _conversation(messages: Iterable[PromptMessage], *, summary_labels: bool) -> str:
    rows: list[str] = []
    for message in messages:
        if summary_labels:
            label = {"user": "User", "assistant": "AI"}.get(message.role, "Context")
        else:
            label = message.role
        rows.append(f"{label}: {message.content}")
    return "\n".join(rows) + ("\n" if rows else "")


def summary_prompt(messages: Iterable[PromptMessage]) -> str:
    dialogue = _conversation(messages, summary_labels=True)
    return (
        "You are compressing a therapeutic conversation log to preserve "
        "essential context for future sessions.\n\n"
        "Conversation to compress:\n"
        f"{dialogue}\n"
        "Write a structured summary under 150 words covering:\n"
        "1. Emotional arc — how the user's distress or mood shifted\n"
        "2. Key disclosures — the core struggles or situations the user revealed\n"
        "3. What helped — any responses or moments the user engaged with positively\n"
        "4. Open threads — unresolved themes that should be followed up\n\n"
        "Rules:\n"
        '- Write in past tense, third person ("The user expressed…")\n'
        '- Be specific, not generic ("mentioned feeling trapped at work" not "had work stress")\n'
        "- Output ONLY the summary text, no headers or bullet points\n"
    )


def first_extract_prompt(messages: Iterable[PromptMessage]) -> str:
    conv = _conversation(messages, summary_labels=False)
    return (
        "You are building an initial psychological profile from a first therapy-adjacent conversation.\n"
        "Be specific and evidence-based — only record what is clearly evidenced.\n"
        "Return ONLY valid JSON, no markdown:\n"
        "{\n"
        '  "emotionPattern": "specific patterns observed, or null",\n'
        '  "coreStruggles": "specific stressors/pain points, or null",\n'
        '  "effectiveCoping": "what seemed to help in this session, or null",\n'
        '  "languageStyle": "how this person expresses inner states, or null",\n'
        '  "triggerUpdates": [\n'
        '    {"observation": "specific trigger", "action": "new", "confidence": "high|medium|low"}\n'
        "  ],\n"
        '  "conflicts": [],\n'
        '  "newProgressNote": "any notable first-session insight, or null",\n'
        '  "changeLogEntry": "Initial profile created from first session"\n'
        "}\n"
        "Conversation:\n"
        f"{conv}"
    )


def merge_prompt(wiki_text: str, messages: Iterable[PromptMessage]) -> str:
    conv = _conversation(messages, summary_labels=False)
    existing = (
        "EXISTING WIKI: (empty — first session)"
        if not wiki_text
        else f"EXISTING WIKI:\n{wiki_text}"
    )
    return (
        "You are a clinical psychologist maintaining a structured patient wiki.\n\n"
        f"{existing}\n\n"
        "New conversation:\n"
        f"{conv}\n"
        "Rules:\n"
        "- Only record what is evidenced in THIS conversation\n"
        '- For triggers: "increment" if same trigger recurred, "new" if novel, "remove" only if explicitly resolved\n'
        "- For text fields: return updated value if meaningfully changed; null if no change needed\n"
        "- Be specific, not generic. Avoid boilerplate phrases.\n"
        "- conflicts: list only genuine contradictions between the existing wiki and THIS session\n"
        '  (e.g. wiki says "avoids exercise" but this session shows active jogging habit).\n'
        '  resolution must be one of: "updated" (wiki changed), "kept" (wiki unchanged, one-off), "both noted"\n'
        "- changeLogEntry: 1-2 sentences describing what changed this session\n\n"
        "Return ONLY valid JSON, no markdown:\n"
        "{\n"
        '  "emotionPattern": "updated or null",\n'
        '  "coreStruggles": "updated or null",\n'
        '  "effectiveCoping": "updated or null",\n'
        '  "languageStyle": "updated or null",\n'
        '  "triggerUpdates": [\n'
        '    {"observation": "specific trigger", "action": "new|increment|remove", "confidence": "high|medium|low"}\n'
        "  ],\n"
        '  "conflicts": [\n'
        '    {"field": "emotionPattern|coreStruggles|effectiveCoping|triggers", "existing": "...", "observed": "...", "resolution": "updated|kept|both noted"}\n'
        "  ],\n"
        '  "newProgressNote": "specific growth observation, or null",\n'
        '  "changeLogEntry": "brief summary"\n'
        "}\n"
    )


def reflection_prompt(memory_text: str) -> str:
    return (
        "Based on the following user memory data, generate a \n"
        "high-level insight about this user's emotional patterns \n"
        "and progress. Focus on trends, triggers, and what helps.\n"
        "Keep it under 200 words, written as a clinical observation.\n\n"
        "Memory data:\n"
        f"{memory_text}\n\n"
        "Return only the insight text, no labels or formatting.\n"
    )


def response_prompt(context: str, probe: Probe) -> str:
    options = "\n".join(f"{option.id}. {option.text}" for option in probe.options)
    return (
        "You are responding to the current user. Use memory only when it is relevant "
        "and currently applicable. Select the single best response option.\n\n"
        "Memory context (it may be empty, stale, conflicting, or inapplicable):\n"
        f"{context or '(none)'}\n"
        "Current user message:\n"
        f"{probe.text}\n\n"
        "Options:\n"
        f"{options}\n\n"
        'Return ONLY JSON in this exact shape: {"choice":"A"}\n'
        "The choice must be exactly one of A, B, or C."
    )


def canonical_prompt_payload(operation: str, prompt: str) -> str:
    return json.dumps(
        {"operation": operation, "prompt": prompt},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
