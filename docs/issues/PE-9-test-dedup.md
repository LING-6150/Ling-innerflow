# PE-9 — Test: PatternDeduplicator (0.88, cross-key only)

> Lane C. Independent.
> Tracked by **#PE-0**.

## Why

The dedup behavior reuses the exact `DEDUP_THRESHOLD = 0.88` from `MemoryService` (P3-11)
and applies it across DIFFERENT `pattern_key`s only — same-key duplicates are handled by
the DB uniqueness constraint, and cross-key near-duplicates are what 0.88 is tuned for.
These tests lock both behaviors and verify graceful degradation when embedding fails.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/dedup/PatternDeduplicatorTest.java`

No main-source changes.

## Build

Pure unit tests. Mock `EmbeddingModel` with Mockito (`spring-boot-starter-test` includes
Mockito-core). Construct existing `PatternInstance` rows by hand.

Tests:

1. **`returns_null_on_empty_existing_list`** — empty list of active instances → null.
2. **`above_threshold_different_key_is_duplicate`** — mock embedder returns two unit
   vectors with cosine 0.95 (e.g. one component 1.0, others 0; close by construction).
   New summary's pattern_key = `self_criticism`; existing's = `worth_through_achievement`.
   → returns the existing instance.
3. **`above_threshold_same_key_is_NOT_duplicate`** — same vectors, but both
   `pattern_key = self_criticism` → returns null (same-key handled by DB constraint).
4. **`exactly_at_threshold_counts_as_match`** — design vectors so cosine ≈ 0.88 (e.g.
   `[0.88, sqrt(1-0.88²), 0]` vs `[1, 0, 0]`) → returns the existing instance (`>=` semantics).
5. **`below_threshold_no_match`** — cosine 0.85 → returns null.
6. **`graceful_degrade_on_embedding_exception`** — mock embedder throws → returns null,
   no exception propagated.
7. **`uses_batch_embed_once`** — mock embedder; verify `embed(List<String>)` was called
   exactly once with `[newSummary, existing1.summary, existing2.summary, ...]` in that
   order (matching `MemoryService.findSimilarTrigger`'s pattern).

## Verification

- `./mvnw -q -Dtest='PatternDeduplicatorTest' test` green.
- All 7 tests pass.
- Test runtime < 1s.

## Out of scope

- Testing the orchestrator's use of the deduplicator.
- End-to-end DB testing.

---

## Drop-in prompt

```
Write PatternDeduplicator tests in worktree .claude/worktrees/pe-9-test-dedup.
Branch: feature/pe-9-test-dedup (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-9-test-dedup.md
  - src/main/java/com/ling/linginnerflow/pattern/dedup/PatternDeduplicator.java
  - src/main/java/com/ling/linginnerflow/memory/MemoryService.java (look at findSimilarTrigger / 0.88)
  - src/main/java/com/ling/linginnerflow/pattern/entity/PatternInstance.java

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/dedup/.
  - JUnit 5 + Mockito. No Spring context.
  - Construct controlled vectors so cosine values are predictable to 2 decimals.
  - Test runtime budget: < 1s total.

DELIVERABLES:
  - PatternDeduplicatorTest.java with the 7 tests listed in the issue.

VERIFY:
  ./mvnw -q -Dtest='PatternDeduplicatorTest' test

REPORT and DO NOT push.
```
