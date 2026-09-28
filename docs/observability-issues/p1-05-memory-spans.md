[P1-05] Spans for memory subsystem (long-memory / reflection / dedup / compression)

**Phase:** 1 · **Est:** ~1 day · **Risk:** Med · **Labels:** observability, memory

## Why
Memory does heavy LLM/embedding work, some on the session-close path and some async — none of it timed today.

## Scope
- `MemoryService.updateLongMemory` (`:172`, wiki merge LLM), `generateReflection` (`:780`), `findSimilarTrigger` (`:502`, batch embedding dedup) → manual spans.
- `MemoryCompressionService.generateSummary` (`:138`) runs on an async executor → capture and restore the observation context so the span nests correctly (or links to the originating trace).
- Tag `prompt.version` on the wiki-merge and reflection prompts.

## Acceptance
- End-session flow shows `memory.update_long` with `gen_ai.chat` (merge) + `gen_ai.chat` (reflection) + embed (dedup) children.
- Async compression appears as a span linked to / nested under its trigger.

## Depends on
P1-01, P1-02.
