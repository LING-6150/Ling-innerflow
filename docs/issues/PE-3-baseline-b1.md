# PE-3 — Baseline B1: Lexical / BM25-style cue matching

> Lane B. Depends on **#PE-1**.
> Tracked by **#PE-0**.
> **HEADLINE FLOOR baseline per V1.2 R30.** The Pattern Engine must beat this on Tier A-H
> to be load-bearing — otherwise the LLM machinery is style homophily, not capability.

## Why

The only baseline immune to LLM homophily. Uses the YAML `lexical_cues` against each
persona's corpus, no embeddings, no LLM. "Full pipeline beats B1 on Tier A-H" is the
headline claim V1.2 R30 asks us to defend.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/eval/baseline/`:
  - `B1_LexicalBaseline.java`
  - `B1_LexicalBaselineTest.java`

## Build

```java
public class B1_LexicalBaseline implements Baseline {
    public B1_LexicalBaseline(PatternDefinitionLoader defs, int minCueHits);
    // For each pattern_key, count corpus records whose text contains >=1 lexical_cue.
    // Predict (pattern_key, primary_domain) iff hit count >= minCueHits (default 3).
}
```

- Case-insensitive substring match on Chinese and Latin cues alike.
- A single corpus record counts at most ONCE per pattern (even if it contains multiple cues).
- Domain choice: primary_domain only in V1; do not try to infer cross-domain from text.

## Verification

- Unit test: a hand-crafted persona with 4 records hitting `people_pleasing` cues → predicted.
- Unit test: same persona with 2 hits (below threshold) → not predicted.
- Unit test: a persona with cues from a key NOT in its `primary_domain` → still predicted at
  that key's `primary_domain` (V1 design choice; document the limit).
- `./mvnw -q -Dtest='*.B1_*' test` green.

## Out of scope

- Embedding-based recall (that's S2 of the real engine, not B1).
- BM25 scoring with IDF — V1 keeps B1 honest by using raw hit count (no advantage from
  shared TF-IDF tooling that would resemble the engine).

---

## Drop-in prompt

```
Implement Baseline B1 in worktree .claude/worktrees/pe-3-baseline-b1.
Branch: feature/pe-3-baseline-b1 (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-3-baseline-b1.md
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.md §9.2 (B1 design)
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.2.md (R30: headline claim "beat B1")
  - src/main/resources/patterns/people_pleasing.yaml (see lexical_cues field)
  - PE-1's DTOs in src/test/java/com/ling/linginnerflow/pattern/eval/

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/eval/baseline/.
  - NO embeddings, NO LLM calls. Pure substring matching. This is the "LLM-immune floor."
  - One cue match per record max (count distinct records, not distinct cues).
  - Use PatternDefinitionLoader to fetch primary_domain — do not hardcode taxonomy.

DELIVERABLES:
  - src/test/java/com/ling/linginnerflow/pattern/eval/baseline/B1_LexicalBaseline.java
  - src/test/java/com/ling/linginnerflow/pattern/eval/baseline/B1_LexicalBaselineTest.java (>=3 tests)

VERIFY:
  ./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.baseline.B1_*' test

REPORT and DO NOT push.
```
