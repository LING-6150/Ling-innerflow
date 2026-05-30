# PE-7 — Test: ConfidenceScorer

> Lane C. Independent — runs any time.
> Tracked by **#PE-0**.
> **Regression guard for V1.2 R13**: the LLM `strength` term must remain absent.

## Why

The confidence formula is the single most-debated number this system emits. The V1.2 R13
fix removed the LLM `strength` term — if anyone reintroduces it, every result becomes
suspect (uncalibrated LLM scalar laundering as a quantitative score). These tests lock
the formula and bound its values.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/scoring/ConfidenceScorerTest.java`

No main-source changes.

## Build

Pure-Java unit tests. No Spring. Pass `EvidenceItem` lists you craft inline.

Tests:

1. **`empty_chain_scores_zero`** — `score(List.of())` returns `0.0`.
2. **`five_items_four_distinct_days_today_max_value`** — craft 5 items across 4 distinct
   `occurredAt` days, latest = now → expect close to `w_e + w_r + w_t = 1.0` (allow ±0.01
   for `exp(-0/90) = 1`). Verifies all 3 terms contribute at full strength.
3. **`fewer_items_lowers_evidence_term_only`** — 3 items, 3 distinct days, latest = now →
   Evidence = 3/5 = 0.6, Recurrence = 3/4 = 0.75, Recency = 1.0 →
   confidence = 0.50·0.6 + 0.30·0.75 + 0.20·1.0 ≈ 0.725. Assert to 2 decimals.
4. **`recency_decays_by_90_day_halflife`** — 5 items all on a day 63 days ago →
   Recency = exp(-63/90) ≈ 0.4966 → confidence asymptote check.
5. **`single_day_evidence_lowers_recurrence`** — 5 items all on one day → Evidence = 1.0,
   Recurrence = 1/4 = 0.25, Recency ≈ 1 → confidence = 0.50 + 0.075 + 0.20 = 0.775.
6. **`should_surface_respects_threshold`** — set threshold to 0.6 via reflection /
   constructor wiring; assert `shouldSurface(0.6)` is true, `shouldSurface(0.59)` is false.
7. **`scoring_is_rounded_to_two_decimals`** — assert the returned value has at most 2
   decimal places (multiply by 100, check it's integral within 1e-9).
8. **`no_strength_term_in_implementation`** — open `ConfidenceScorer.class` (or its source
   resource if accessible) and assert the lowercase identifier `strength` does not appear
   among its method names / field names via reflection. (R13 regression guard, code-level.)

## Verification

- `./mvnw -q -Dtest='ConfidenceScorerTest' test` green.
- All 8 tests pass.

## Out of scope

- Calibration (PE-1's eval harness sweeps weights/threshold; this issue just locks the
  formula).
- Reliability diagrams.

---

## Drop-in prompt

```
Write the ConfidenceScorer unit tests in worktree .claude/worktrees/pe-7-test-confidence.
Branch: feature/pe-7-test-confidence (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-7-test-confidence.md
  - src/main/java/com/ling/linginnerflow/pattern/scoring/ConfidenceScorer.java
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.2.md (R13 — no strength)
  - src/main/java/com/ling/linginnerflow/pattern/entity/EvidenceItem.java

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/scoring/.
  - JUnit 5. No Spring context. Inline fixtures only.
  - Use small numeric tolerances (1e-2 for asserts on rounded numbers, 1e-9 for the
    "rounded to 2 decimals" structural check).
  - The R13 regression guard must inspect the class via reflection — no string scanning
    over source files.

DELIVERABLES:
  - ConfidenceScorerTest.java with the 8 tests listed in the issue.

VERIFY:
  ./mvnw -q -Dtest='ConfidenceScorerTest' test

REPORT and DO NOT push.
```
