from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Any, Literal

from innerflow_v2.reliability.client import (
    ChatBackend,
    Completion,
    EmbeddingBackend,
    parse_json_object,
)
from innerflow_v2.reliability.models import MemoryEvent, ReliabilityScenario
from innerflow_v2.reliability.prompts import (
    first_extract_prompt,
    merge_prompt,
    reflection_prompt,
    response_prompt,
    summary_prompt,
)

PolicyName = Literal["B-summary", "B-full", "B-none"]


@dataclass
class MemoryMessage:
    role: str
    content: str
    timestamp: int
    source_ids: tuple[str, ...] = ()


@dataclass
class Trigger:
    observation: str
    count: int
    first_seen: str
    last_seen: str
    confidence: str
    score: float
    source_ids: set[str] = field(default_factory=set)


@dataclass
class WikiState:
    emotion_pattern: str | None = None
    core_struggles: str | None = None
    effective_coping: str | None = None
    language_style: str | None = None
    triggers: list[Trigger] = field(default_factory=list)
    progress_notes: list[dict[str, str]] = field(default_factory=list)
    change_log: list[dict[str, str]] = field(default_factory=list)
    conflicts: list[dict[str, Any]] = field(default_factory=list)
    conversation_summary: str | None = None
    reflection: str | None = None
    compression_count: int = 0
    field_sources: dict[str, set[str]] = field(default_factory=dict)


@dataclass(frozen=True)
class ModelCall:
    operation: str
    request_id: str | None
    prompt: str
    response: str


@dataclass
class PolicyTrace:
    calls: list[ModelCall] = field(default_factory=list)
    compression_attempts: int = 0
    compression_applied: int = 0
    compression_skipped_prefix_mismatch: int = 0
    context_source_ids: list[str] = field(default_factory=list)
    provenance_note: str = (
        "Source IDs are conservative container-level attribution for generated "
        "summary/wiki text, not claim-level provenance."
    )


@dataclass
class PolicyOutput:
    case_id: str
    policy: PolicyName
    replicate: int
    context: str
    raw_answer: str
    request_id: str | None
    context_source_ids: list[str]
    trace: PolicyTrace


def event_to_message(event: MemoryEvent) -> MemoryMessage:
    return MemoryMessage(
        role=event.role,
        content=event.content,
        timestamp=int(event.timestamp.timestamp() * 1000),
        source_ids=(event.id,),
    )


def snapshot_prefix_matches(
    current: list[MemoryMessage], snapshot: list[MemoryMessage]
) -> bool:
    if len(current) < len(snapshot):
        return False
    return all(
        left.role == right.role
        and left.content == right.content
        and left.timestamp == right.timestamp
        for left, right in zip(current, snapshot, strict=False)
    )


def atomic_summary_splice(
    current: list[MemoryMessage],
    snapshot: list[MemoryMessage],
    summary: MemoryMessage,
    summarized_messages: int,
) -> tuple[list[MemoryMessage], bool]:
    """Python oracle for the production Lua's prefix-check-and-splice semantics."""
    if not snapshot_prefix_matches(current, snapshot):
        return current, False
    return [summary, *current[summarized_messages:]], True


class FaithfulSummaryPolicy:
    name: PolicyName = "B-summary"

    def __init__(
        self,
        backend: ChatBackend,
        *,
        today: date,
        compression_threshold_rounds: int = 10,
        keep_recent_rounds: int = 4,
        formation_temperature: float = 0.2,
        response_temperature: float = 0.4,
        embedding_backend: EmbeddingBackend | None = None,
    ) -> None:
        self.backend = backend
        self.embedding_backend = embedding_backend
        self.today = today
        self.compression_threshold = compression_threshold_rounds * 2
        self.keep_messages = keep_recent_rounds * 2
        self.formation_temperature = formation_temperature
        self.response_temperature = response_temperature
        self.short: list[MemoryMessage] = []
        self.wiki = WikiState()
        self.trace = PolicyTrace()

    def _complete(self, operation: str, prompt: str, max_tokens: int) -> Completion:
        completion = self.backend.complete(
            operation=operation,
            prompt=prompt,
            temperature=self.formation_temperature,
            max_tokens=max_tokens,
        )
        self.trace.calls.append(
            ModelCall(operation, completion.request_id, prompt, completion.content)
        )
        return completion

    def add_message(self, message: MemoryMessage) -> None:
        self.short.append(message)
        if len(self.short) >= self.compression_threshold:
            self.compress_settled()

    def compress_settled(self) -> bool:
        snapshot = list(self.short)
        self.trace.compression_attempts += 1
        if len(snapshot) <= self.keep_messages:
            return False
        summarized_count = len(snapshot) - self.keep_messages
        to_summarize = snapshot[:summarized_count]
        completion = self._complete(
            "memory.compression.summary",
            summary_prompt(to_summarize),
            300,
        )
        source_ids = tuple(
            source_id for message in to_summarize for source_id in message.source_ids
        )
        summary = MemoryMessage(
            role="system",
            content="[Conversation summary] " + completion.content,
            timestamp=max((message.timestamp for message in snapshot), default=0) + 1,
            source_ids=source_ids,
        )
        updated, applied = atomic_summary_splice(
            self.short, snapshot, summary, summarized_count
        )
        if not applied:
            self.trace.compression_skipped_prefix_mismatch += 1
            return False
        self.short = updated
        self.wiki.conversation_summary = completion.content
        self.wiki.field_sources["conversation_summary"] = set(source_ids)
        self.wiki.compression_count += 1
        self.trace.compression_applied += 1
        return True

    def _is_first_session(self) -> bool:
        return (
            self.wiki.emotion_pattern is None
            and self.wiki.core_struggles is None
            and not self.wiki.triggers
        )

    def _wiki_prompt_text(self) -> str:
        rows: list[str] = []
        if self.wiki.emotion_pattern is not None:
            rows.append(f"emotionPattern: {self.wiki.emotion_pattern}")
        if self.wiki.core_struggles is not None:
            rows.append(f"coreStruggles: {self.wiki.core_struggles}")
        if self.wiki.effective_coping is not None:
            rows.append(f"effectiveCoping: {self.wiki.effective_coping}")
        if self.wiki.language_style is not None:
            rows.append(f"languageStyle: {self.wiki.language_style}")
        if self.wiki.triggers:
            rendered = ", ".join(
                f"{trigger.observation}(×{trigger.count})"
                for trigger in self.wiki.triggers
            )
            rows.append(f"triggers: {rendered}")
        if self.wiki.progress_notes:
            notes = "; ".join(note["note"] for note in self.wiki.progress_notes)
            rows.append(f"progressNotes: {notes}")
        return "\n".join(rows) + ("\n" if rows else "")

    def update_long_memory(self, history: list[MemoryMessage]) -> None:
        if not history:
            return
        first = self._is_first_session()
        prompt = (
            first_extract_prompt(history)
            if first
            else merge_prompt(self._wiki_prompt_text(), history)
        )
        operation = "memory.wiki.first_extract" if first else "memory.wiki.merge"
        result = parse_json_object(self._complete(operation, prompt, 900).content)
        session_sources = {
            source_id for message in history for source_id in message.source_ids
        }
        self._apply_merge(result, session_sources)
        self.generate_reflection()

    def _apply_merge(self, result: dict[str, Any], sources: set[str]) -> None:
        field_map = {
            "emotionPattern": "emotion_pattern",
            "coreStruggles": "core_struggles",
            "effectiveCoping": "effective_coping",
            "languageStyle": "language_style",
        }
        for external, internal in field_map.items():
            value = result.get(external)
            if value is not None:
                setattr(self.wiki, internal, str(value))
                self.wiki.field_sources[internal] = set(sources)
        updates = result.get("triggerUpdates")
        if isinstance(updates, list):
            self._merge_triggers(updates, sources)
        note = result.get("newProgressNote")
        if note is not None:
            self.wiki.progress_notes.append(
                {"date": self.today.isoformat(), "note": str(note)}
            )
            self.wiki.progress_notes = self.wiki.progress_notes[-20:]
            self.wiki.field_sources["progress_notes"] = set(sources)
        conflicts = result.get("conflicts")
        if isinstance(conflicts, list) and conflicts:
            self.wiki.conflicts = conflicts
            self.wiki.field_sources["conflicts"] = set(sources)
        change = result.get("changeLogEntry")
        if change is not None:
            self.wiki.change_log.insert(
                0, {"date": self.today.isoformat(), "entry": str(change)}
            )
            self.wiki.change_log = self.wiki.change_log[:50]
            self.wiki.field_sources["change_log"] = set(sources)

    def _merge_triggers(
        self, updates: list[Any], session_sources: set[str]
    ) -> None:
        for raw in updates:
            if not isinstance(raw, dict):
                continue
            observation = raw.get("observation")
            action = raw.get("action")
            if not isinstance(observation, str) or not isinstance(action, str):
                continue
            if action == "new":
                similar = self._find_similar_trigger(observation)
                if similar is not None:
                    similar.count += 1
                    similar.last_seen = self.today.isoformat()
                    similar.score = self.compute_score(
                        similar.count, similar.last_seen, similar.confidence
                    )
                    similar.source_ids.update(session_sources)
                else:
                    confidence = raw.get("confidence") or "medium"
                    self.wiki.triggers.append(
                        Trigger(
                            observation=observation,
                            count=1,
                            first_seen=self.today.isoformat(),
                            last_seen=self.today.isoformat(),
                            confidence=str(confidence),
                            score=self.compute_score(
                                1, self.today.isoformat(), str(confidence)
                            ),
                            source_ids=set(session_sources),
                        )
                    )
            elif action == "increment":
                # Deliberately faithful to the known Java exact-match bug.
                for trigger in self.wiki.triggers:
                    if trigger.observation.casefold() == observation.casefold():
                        trigger.count += 1
                        trigger.last_seen = self.today.isoformat()
                        trigger.score = self.compute_score(
                            trigger.count, trigger.last_seen, trigger.confidence
                        )
                        trigger.source_ids.update(session_sources)
                        break
            elif action == "remove":
                # Deliberately faithful to the known Java exact-match bug.
                self.wiki.triggers = [
                    trigger
                    for trigger in self.wiki.triggers
                    if trigger.observation.casefold() != observation.casefold()
                ]

    def _find_similar_trigger(self, observation: str) -> Trigger | None:
        if not self.wiki.triggers or self.embedding_backend is None:
            return None
        try:
            vectors = self.embedding_backend.embed(
                [observation, *(trigger.observation for trigger in self.wiki.triggers)]
            )
        except Exception:
            return None
        best: Trigger | None = None
        best_similarity = 0.88
        for trigger, vector in zip(self.wiki.triggers, vectors[1:], strict=True):
            similarity = _cosine(vectors[0], vector)
            if similarity > best_similarity:
                best_similarity = similarity
                best = trigger
        return best

    def compute_score(self, count: int, last_seen: str | None, confidence: str) -> float:
        if confidence == "confirmed":
            return 1.0
        recency = 1.0
        if last_seen is not None:
            try:
                days = (self.today - date.fromisoformat(last_seen)).days
                recency = math.exp(-days / 90.0)
            except ValueError:
                pass
        # Java Math.round is positive half-up; Python round is bankers rounding.
        value = min(1.0, count / 5.0) * recency
        return math.floor(value * 100.0 + 0.5) / 100.0

    def generate_reflection(self) -> None:
        rows: list[str] = []
        if self.wiki.emotion_pattern is not None:
            rows.append(f"Emotion pattern: {self.wiki.emotion_pattern}")
        if self.wiki.core_struggles is not None:
            rows.append(f"Core struggles: {self.wiki.core_struggles}")
        if self.wiki.effective_coping is not None:
            rows.append(f"Effective coping: {self.wiki.effective_coping}")
        if self.wiki.conversation_summary is not None:
            rows.append(f"Recent summary: {self.wiki.conversation_summary}")
        if not rows:
            return
        memory_text = "\n".join(rows) + "\n"
        completion = self._complete(
            "memory.reflection", reflection_prompt(memory_text), 400
        )
        self.wiki.reflection = completion.content
        sources: set[str] = set()
        for key in (
            "emotion_pattern",
            "core_struggles",
            "effective_coping",
            "conversation_summary",
        ):
            sources.update(self.wiki.field_sources.get(key, set()))
        self.wiki.field_sources["reflection"] = sources

    def end_session(self) -> None:
        self.update_long_memory(list(self.short))
        self.short = []

    def build_context(self) -> tuple[str, list[str]]:
        rows: list[str] = []
        sources: set[str] = set()
        has_wiki = any(
            (
                self.wiki.core_struggles,
                self.wiki.emotion_pattern,
                self.wiki.triggers,
                self.wiki.effective_coping,
                self.wiki.progress_notes,
                self.wiki.language_style,
                self.wiki.reflection,
            )
        )
        if has_wiki:
            rows.append("=== USER WIKI ===")
            self._append_wiki_field(
                rows, sources, "Core struggles", "core_struggles"
            )
            self._append_wiki_field(
                rows, sources, "Emotion pattern", "emotion_pattern"
            )
            active = [
                trigger for trigger in self.wiki.triggers
                if self.compute_score(
                    trigger.count, trigger.last_seen, trigger.confidence
                ) >= 0.3
            ]
            active.sort(
                key=lambda trigger: self.compute_score(
                    trigger.count, trigger.last_seen, trigger.confidence
                ),
                reverse=True,
            )
            if active:
                rendered = ", ".join(
                    f"{trigger.observation} (×{trigger.count}, "
                    f"score={self.compute_score(trigger.count, trigger.last_seen, trigger.confidence)})"
                    for trigger in active
                )
                rows.append(f"Known triggers: {rendered}")
                for trigger in active:
                    sources.update(trigger.source_ids)
            self._append_wiki_field(rows, sources, "What helps", "effective_coping")
            notes = self.wiki.progress_notes[-3:]
            if notes:
                rows.append(
                    "Progress: "
                    + " | ".join(
                        f"{note['date']}: {note['note']}" for note in notes
                    )
                )
                sources.update(self.wiki.field_sources.get("progress_notes", set()))
            self._append_wiki_field(rows, sources, "How they speak", "language_style")
            self._append_wiki_field(rows, sources, "Deep insight", "reflection")
            rows.extend(["=================", ""])
        if self.short:
            rows.append("[Recent Conversation]")
            for message in self.short[-10:]:
                label = "User" if message.role == "user" else "AI"
                rows.append(f"{label}: {message.content}")
                sources.update(message.source_ids)
            rows.append("")
        context = "\n".join(rows)
        if context:
            context += "\n"
        source_list = sorted(sources)
        self.trace.context_source_ids = source_list
        return context, source_list

    def _append_wiki_field(
        self, rows: list[str], sources: set[str], label: str, field_name: str
    ) -> None:
        value = getattr(self.wiki, field_name)
        if value is not None:
            rows.append(f"{label}: {value}")
            sources.update(self.wiki.field_sources.get(field_name, set()))

    def run(
        self, scenario: ReliabilityScenario, *, replicate: int
    ) -> PolicyOutput:
        for session in scenario.setup_sessions:
            for event in session.events:
                self.add_message(event_to_message(event))
            self.end_session()
        context, source_ids = self.build_context()
        prompt = response_prompt(context, scenario.probe)
        answer = self.backend.complete(
            operation="memory.probe.response",
            prompt=prompt,
            temperature=self.response_temperature,
            max_tokens=40,
        )
        self.trace.calls.append(
            ModelCall(
                "memory.probe.response",
                answer.request_id,
                prompt,
                answer.content,
            )
        )
        return PolicyOutput(
            case_id=scenario.case_id,
            policy=self.name,
            replicate=replicate,
            context=context,
            raw_answer=answer.content,
            request_id=answer.request_id,
            context_source_ids=source_ids,
            trace=self.trace,
        )


class FullHistoryPolicy:
    name: PolicyName = "B-full"

    def __init__(self, backend: ChatBackend, *, response_temperature: float = 0.4):
        self.backend = backend
        self.response_temperature = response_temperature

    def run(
        self, scenario: ReliabilityScenario, *, replicate: int
    ) -> PolicyOutput:
        events = [
            event for session in scenario.setup_sessions for event in session.events
        ]
        rows = ["[Complete Memory History]"]
        for event in events:
            label = "User" if event.role == "user" else "AI"
            rows.append(f"{label}: {event.content}")
        context = "\n".join(rows) + "\n"
        prompt = response_prompt(context, scenario.probe)
        answer = self.backend.complete(
            operation="memory.probe.response",
            prompt=prompt,
            temperature=self.response_temperature,
            max_tokens=40,
        )
        trace = PolicyTrace(
            calls=[
                ModelCall(
                    "memory.probe.response",
                    answer.request_id,
                    prompt,
                    answer.content,
                )
            ],
            context_source_ids=[event.id for event in events],
        )
        return PolicyOutput(
            scenario.case_id,
            self.name,
            replicate,
            context,
            answer.content,
            answer.request_id,
            trace.context_source_ids,
            trace,
        )


class NoMemoryPolicy:
    name: PolicyName = "B-none"

    def __init__(self, backend: ChatBackend, *, response_temperature: float = 0.4):
        self.backend = backend
        self.response_temperature = response_temperature

    def run(
        self, scenario: ReliabilityScenario, *, replicate: int
    ) -> PolicyOutput:
        prompt = response_prompt("", scenario.probe)
        answer = self.backend.complete(
            operation="memory.probe.response",
            prompt=prompt,
            temperature=self.response_temperature,
            max_tokens=40,
        )
        trace = PolicyTrace(
            calls=[
                ModelCall(
                    "memory.probe.response",
                    answer.request_id,
                    prompt,
                    answer.content,
                )
            ]
        )
        return PolicyOutput(
            scenario.case_id,
            self.name,
            replicate,
            "",
            answer.content,
            answer.request_id,
            [],
            trace,
        )


def _cosine(left: list[float], right: list[float]) -> float:
    dot = sum(a * b for a, b in zip(left, right, strict=True))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    return dot / (left_norm * right_norm + 1e-10)
