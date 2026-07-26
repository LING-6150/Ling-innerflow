from __future__ import annotations

import json
from datetime import date

from innerflow_v2.reliability.baselines import (
    FaithfulSummaryPolicy,
    MemoryMessage,
    Trigger,
    atomic_summary_splice,
)
from innerflow_v2.reliability.client import Completion


EMPTY_EXTRACT = {
    "emotionPattern": None,
    "coreStruggles": None,
    "effectiveCoping": None,
    "languageStyle": None,
    "triggerUpdates": [],
    "conflicts": [],
    "newProgressNote": None,
    "changeLogEntry": "Initial profile created from first session",
}


class ScriptedBackend:
    def __init__(self, responses: dict[str, list[str]] | None = None):
        self.responses = responses or {}
        self.calls: list[tuple[str, str, float]] = []

    def complete(self, *, operation, prompt, temperature, max_tokens):
        self.calls.append((operation, prompt, temperature))
        queue = self.responses.get(operation, [])
        if not queue:
            raise AssertionError(f"unexpected call: {operation}")
        return Completion(queue.pop(0), f"req-{len(self.calls)}")


def messages(count: int, *, prefix: str = "m") -> list[MemoryMessage]:
    return [
        MemoryMessage(
            "user" if index % 2 == 0 else "assistant",
            f"{prefix}{index}",
            1_700_000_000_000 + index,
            (f"{prefix}{index}",),
        )
        for index in range(count)
    ]


def test_19_messages_do_not_compress_but_20_keep_exactly_8_recent_messages():
    backend = ScriptedBackend({"memory.compression.summary": ["summary"]})
    policy = FaithfulSummaryPolicy(backend, today=date(2026, 7, 24))
    for message in messages(19):
        policy.add_message(message)
    assert len(policy.short) == 19
    assert not backend.calls

    policy.add_message(messages(20)[-1])
    assert [message.content for message in policy.short] == [
        "[Conversation summary] summary",
        *[f"m{i}" for i in range(12, 20)],
    ]
    assert policy.wiki.conversation_summary == "summary"
    assert policy.wiki.compression_count == 1
    assert policy.trace.compression_applied == 1


def test_recursive_compression_preserves_the_sliding_tail():
    backend = ScriptedBackend(
        {"memory.compression.summary": ["first summary", "second summary"]}
    )
    policy = FaithfulSummaryPolicy(backend, today=date(2026, 7, 24))
    for message in messages(31):
        policy.add_message(message)
    assert policy.wiki.compression_count == 2
    assert policy.short[0].content == "[Conversation summary] second summary"
    assert [message.content for message in policy.short[1:]] == [
        f"m{i}" for i in range(23, 31)
    ]
    assert set(policy.short[0].source_ids) == {f"m{i}" for i in range(23)}


def test_lua_oracle_preserves_compatible_window_append_and_skips_rewritten_prefix():
    snapshot = messages(20)
    window = MemoryMessage("user", "window", 1_700_000_001_000, ("window",))
    summary = MemoryMessage("system", "summary", 1_700_000_001_001, ("m0",))

    compatible, applied = atomic_summary_splice(
        [*snapshot, window], snapshot, summary, 12
    )
    assert applied
    assert compatible[-1] is window
    assert len(compatible) == 10

    rewritten = list(snapshot)
    rewritten[0] = MemoryMessage("user", "changed", snapshot[0].timestamp, ("x",))
    unchanged, applied = atomic_summary_splice(rewritten, snapshot, summary, 12)
    assert not applied
    assert unchanged == rewritten


def test_first_session_predicate_merge_bug_and_context_allowlist_are_java_faithful():
    first = {
        **EMPTY_EXTRACT,
        "emotionPattern": "anxious under review",
        "coreStruggles": "performance pressure",
        "effectiveCoping": "short walks",
        "languageStyle": "uses weather metaphors",
        "triggerUpdates": [
            {
                "observation": "weekly status meetings",
                "action": "new",
                "confidence": "confirmed",
            }
        ],
        "newProgressNote": "Named the pressure clearly.",
        "conflicts": [{"field": "triggers", "existing": "", "observed": "meeting"}],
    }
    second = {
        "emotionPattern": None,
        "coreStruggles": None,
        "effectiveCoping": None,
        "languageStyle": None,
        # Paraphrase intentionally fails exact equalsIgnoreCase increment.
        "triggerUpdates": [
            {
                "observation": "the weekly status meeting",
                "action": "increment",
                "confidence": "high",
            }
        ],
        "conflicts": [],
        "newProgressNote": "Another observation.",
        "changeLogEntry": "No structural update.",
    }
    backend = ScriptedBackend(
        {
            "memory.wiki.first_extract": [json.dumps(first)],
            "memory.wiki.merge": [json.dumps(second)],
            "memory.reflection": ["reflection one", "reflection two"],
        }
    )
    policy = FaithfulSummaryPolicy(backend, today=date(2026, 7, 24))
    policy.short = messages(2, prefix="s1")
    policy.end_session()
    policy.short = messages(2, prefix="s2")
    policy.end_session()

    assert policy.wiki.triggers[0].count == 1
    context, _ = policy.build_context()
    assert "Core struggles: performance pressure" in context
    assert "Emotion pattern: anxious under review" in context
    assert "Known triggers: weekly status meetings (×1, score=1.0)" in context
    assert "What helps: short walks" in context
    assert "How they speak: uses weather metaphors" in context
    assert "Deep insight: reflection two" in context
    assert "Another observation." in context
    assert "No structural update." not in context
    assert "existing" not in context
    assert "conversationSummary" not in context

    merge_call = next(call for call in backend.calls if call[0] == "memory.wiki.merge")
    merge_text = merge_call[1]
    assert "reflection one" not in merge_text
    assert "conflicts" not in merge_text.split("New conversation:")[0]


def test_reflection_reads_only_four_java_fields_and_system_summary_renders_as_ai():
    backend = ScriptedBackend({"memory.reflection": ["insight"]})
    policy = FaithfulSummaryPolicy(backend, today=date(2026, 7, 24))
    policy.wiki.emotion_pattern = "emotion"
    policy.wiki.core_struggles = "struggle"
    policy.wiki.effective_coping = "coping"
    policy.wiki.conversation_summary = "summary"
    policy.wiki.language_style = "SECRET_STYLE"
    policy.wiki.triggers = [
        Trigger("SECRET_TRIGGER", 5, "2026-07-24", "2026-07-24", "high", 1.0)
    ]
    policy.generate_reflection()
    prompt = backend.calls[0][1]
    assert all(word in prompt for word in ("emotion", "struggle", "coping", "summary"))
    assert "SECRET_STYLE" not in prompt
    assert "SECRET_TRIGGER" not in prompt

    policy.short = [MemoryMessage("system", "[Conversation summary] x", 1)]
    context, _ = policy.build_context()
    assert "AI: [Conversation summary] x" in context
