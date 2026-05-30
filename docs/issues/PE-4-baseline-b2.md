# PE-4 — Baseline B2: Single-prompt LLM (network-gated)

> Lane B. Depends on **#PE-1**.
> Tracked by **#PE-0**.

## Why

The baseline most teams ship without realizing it: stuff the corpus + 12-pattern taxonomy
into one `gpt-4o-mini` prompt and ask for present patterns + quoted evidence. Exposes
exactly the failures the real engine's retrieval + verification stages exist to fix:
citation hallucination, context overflow, non-determinism.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/eval/baseline/`:
  - `B2_SinglePromptBaseline.java`
  - `B2_SinglePromptBaselineTest.java`

## Build

```java
public class B2_SinglePromptBaseline implements Baseline {
    public B2_SinglePromptBaseline(ChatClient.Builder builder, PatternDefinitionLoader defs);

    // The actual LLM call is gated behind a flag so the suite stays offline.
    @Value("${pattern.eval.b2.live:false}") boolean live;

    String buildPrompt(GTPersona persona);                          // package-private, testable
    Set<PredictedPattern> parseResponse(String llmRaw);             // package-private, testable
    Set<PredictedPattern> predict(GTPersona persona);               // throws if !live
}
```

- The prompt explicitly enumerates the 12 `pattern_key` (closed set; LLM may not invent).
- Response format: strict JSON `[{"pattern_key": "...", "domain": "..."}]`.
- `parseResponse` tolerates: extra prose around the JSON, missing fields (skip), unknown
  keys (filter against `PatternDefinitionLoader.keys()`).
- When `live=false` (default), `predict()` throws a clear `IllegalStateException` with
  message "B2 requires -Dpattern.eval.b2.live=true (consumes network/tokens)." This keeps
  default test runs offline.

## Verification

- `buildPrompt` test: produced prompt contains all 12 pattern_keys verbatim.
- `parseResponse` test on fixtures: valid JSON, JSON wrapped in markdown fences, JSON with
  garbage prefix/suffix, unknown key in the JSON (must be filtered).
- `parseResponse` test: predicted set never contains a pattern outside the 12.
- The live `predict()` path is covered by `@Disabled("requires live LLM, see PE-4 issue")`
  with a comment explaining how to enable.
- `./mvnw -q -Dtest='*.B2_*' test` green.

## Out of scope

- Caching, retries, structured-output mode (the point of B2 is to be the dumb single prompt).
- Verbatim verification (B2 is allowed to hallucinate citations — that's exactly the failure
  we're measuring against the full pipeline).

---

## Drop-in prompt

```
Implement Baseline B2 in worktree .claude/worktrees/pe-4-baseline-b2.
Branch: feature/pe-4-baseline-b2 (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-4-baseline-b2.md
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.md §9.3 (B2 design)
  - PE-1's DTOs in src/test/java/com/ling/linginnerflow/pattern/eval/

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/eval/baseline/.
  - Test scope. Default OFFLINE: predict() must throw without -Dpattern.eval.b2.live=true.
  - Prompt building + JSON parsing are package-private, deterministic, fully unit-tested.
  - Filter LLM response against PatternDefinitionLoader.keys() — never return unknown keys.

DELIVERABLES:
  - B2_SinglePromptBaseline.java (with buildPrompt + parseResponse package-private)
  - B2_SinglePromptBaselineTest.java with >=4 fixture-based tests for the parser
    + @Disabled live integration test

VERIFY:
  ./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.baseline.B2_*' test

REPORT and DO NOT push.
```
