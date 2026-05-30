# PE-2 — Baseline B0: Prior / chance level

> Lane B. Depends on **#PE-1** (needs `GroundTruthLoader` + `MetricsCalculator` + DTOs).
> Tracked by **#PE-0**.

## Why

The chance-level baseline. Surfaces each `(pattern_key, domain)` with probability equal
to its base rate in the Tier A ground truth. Establishes the floor any real method must
beat to be non-degenerate, and confirms the eval set isn't pathological.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/eval/baseline/` — create:
  - `Baseline.java` (interface)
  - `B0_PriorBaseline.java`
  - `B0_PriorBaselineTest.java`

No other file may be touched. No main-source code.

## Build

```java
public interface Baseline {
    Set<PredictedPattern> predict(GTPersona persona);
    String name();
}

public class B0_PriorBaseline implements Baseline {
    public B0_PriorBaseline(long seed, Map<String, Double> baseRatesPerKey);
    // baseRatesPerKey: for each pattern_key, P(present somewhere across all domains)
    // estimated from the truePatterns of the passed-in fitting set (NOT the persona being predicted).
}
```

- Deterministic: same `seed` → same predictions.
- Predicts independently per `(pattern_key, domain)`: `Bernoulli(baseRate / numDomains)` so
  totals match the base rate.
- For domains the engine never assigns a pattern to (per the YAML `primary_domain` +
  `also_in`), set probability to 0 — chance level should respect taxonomy structure.

## Verification

- `Baseline` interface compiles; B0 implements it.
- B0 with seed=42 on a fixed fitting set produces deterministic output across 3 runs.
- Unit test: with all base rates = 0 → predicts empty set. With all = 1 → predicts every
  `(key, domain)` permitted by taxonomy (use `PatternDefinitionLoader`).
- `./mvnw -q -Dtest='*.B0_*' test` green.

## Out of scope

- Running B0 against the real personas (the harness, not the baseline, drives runs).
- Reporting metrics (PE-1's `MetricsCalculator` does that).

---

## Drop-in prompt

```
Implement Baseline B0 in worktree .claude/worktrees/pe-2-baseline-b0.
Branch: feature/pe-2-baseline-b0 (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-2-baseline-b0.md
  - docs/issues/README.md
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.md §9.1 (B0 design)
  - The DTOs PE-1 created: GTPersona, GTLabel, PredictedPattern, MetricReport
    (read them in src/test/java/com/ling/linginnerflow/pattern/eval/)

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/eval/baseline/.
  - Test scope. Offline. No live API.
  - Deterministic with explicit seed.
  - Respect taxonomy: 0 probability for (key, domain) outside primary_domain ∪ also_in
    from PatternDefinitionLoader.

DELIVERABLES:
  - src/test/java/com/ling/linginnerflow/pattern/eval/baseline/Baseline.java (interface)
  - src/test/java/com/ling/linginnerflow/pattern/eval/baseline/B0_PriorBaseline.java
  - src/test/java/com/ling/linginnerflow/pattern/eval/baseline/B0_PriorBaselineTest.java (>=3 tests)

VERIFY:
  ./mvnw -q -DskipTests compile
  ./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.baseline.B0_*' test

REPORT: files created, tests pass/fail, anything skipped.
DO NOT push or open PR.
```
