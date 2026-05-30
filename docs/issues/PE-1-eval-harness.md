# PE-1 — Eval harness: GroundTruthLoader + MetricsCalculator + R40 recall-retention

> Lane A. Blocks Lane B (PE-2 … PE-5).
> Tracked by **#PE-0**.

## Why this exists

The Pattern Engine cannot be evaluated until ground-truth labels and metric
machinery are in place. Six Tier A synthetic personas and the sealed Tier A-H
templates already exist under `eval/groundtruth/`. This issue turns them into
test-scope Java objects + the metric calculators every baseline (PE-2..PE-5)
will call. Critically it implements V1.2 **R40**: recall is measured *through*
the verifier, not only up to it.

## Scope (touch ONLY these dirs)

- `src/test/java/com/ling/linginnerflow/pattern/eval/` — create:
  - `GroundTruthLoader.java`
  - `MetricsCalculator.java`
  - DTOs in the same package: `GTPersona`, `GTLabel`, `CorpusRecord`, `PredictedPattern`, `MetricReport`
- `eval/README.md` — short doc, see below.
- `pom.xml` — only if a YAML test dependency must be added (SnakeYAML is already
  pulled in transitively by Spring Boot; verify before adding anything).

Do NOT touch any main-source Java. Do NOT modify the YAML files or the
`eval/groundtruth/` contents.

## What to build

### `GroundTruthLoader.java`

```java
public class GroundTruthLoader {
    public List<GTPersona> loadTierA();            // eval/groundtruth/tierA/*
    public List<GTPersona> loadTierAH();           // eval/groundtruth/sealed/ah-*.* — SEALED, see guard below
}
```

- Parses, for each persona:
  - `<id>.answerkey.yaml` → `GTPersona.id`, `generatorModel`, `truePatterns`, `decoyPatterns`, `crisisSeeds`.
  - `<id>.corpus.md` → list of `CorpusRecord { LocalDate date, String type, String text }`, parsing
    lines of the form `YYYY-MM-DD [chat|journal|checkin]  <text>`. Skip blank lines and lines
    starting with `#` (comments) or `<` (placeholder markers in templates).
- Skip files named `TEMPLATE_*` (they are scaffolds, not data).
- For Tier A-H, skip any persona whose corpus consists entirely of placeholder lines
  (still-unfilled by the human) and log `INFO` for each. A persona with zero real records
  must NOT enter the returned list.

#### Sealing guard (V1.2 R5)

`loadTierAH()` must enforce that Tier A-H is **never read during a tuning/calibration run**:

```java
// system property "pattern.eval.tuning" — if set to "true", loadTierAH() must throw.
if ("true".equalsIgnoreCase(System.getProperty("pattern.eval.tuning", "false"))) {
    throw new IllegalStateException(
        "Tier A-H is sealed (V1.2 R5). Calibration must not read it. " +
        "Unset -Dpattern.eval.tuning=true.");
}
```

This is the only access control the codebase needs for sealing; rely on convention
(and CI) to set the tuning flag during sweeps.

### `MetricsCalculator.java`

Inputs (per persona):
- `Set<PredictedPattern> predicted` — what some pipeline / baseline surfaced.
- `GTPersona truth` — `truePatterns` + `decoyPatterns`.

Compute and return a `MetricReport`:

```
precision        = |predicted ∩ trueSet|  / max(1, |predicted|)
recall           = |predicted ∩ trueSet|  / max(1, |trueSet|)
f1               = 2·P·R / (P+R)         (0 if P+R = 0)
hardNegativeFPR  = |predicted ∩ decoySet| / max(1, |decoySet|)
```

A `(patternKey, domain)` is a hit only when BOTH match. (No partial credit on
wrong domain — domain reassignment is the engine's job.)

#### V1.2 R40 — recall retention THROUGH the verifier

Add a second method on `MetricsCalculator`:

```java
public RecallRetention recallRetention(
    GTPersona truth,
    Set<PredictedPattern> afterRecallStage,        // S2 candidate set
    Set<PredictedPattern> afterRetrievalGate,      // S3 corpus-support-gate survivors
    Set<PredictedPattern> afterVerifierChain       // S4 valid >=3/>=1-verbatim chains
);
```

`RecallRetention` carries 4 numbers — fraction of `truePatterns` retained after each stage
(S0 = baseline of 1.0, S2, S3, S4). The `afterVerifierChain` number is the V1.2 R40 reported
metric: "preserved recall not silently consumed by R16′."

### `eval/README.md` (short)

Document the **metric triple** reporting convention (Tier A | Tier A-H | Tier B), and the
**V1.2 R34 decision rule**: a mechanism is kept iff statistically supported on Tier A AND
not sign-reversed on Tier A-H. Reference R30/R40 by name. Two to three paragraphs is enough.

## Verification (must all hold to merge)

- `./mvnw -q -DskipTests compile` — clean.
- `./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.**' test` — green (write 4-6 focused
  tests for the loader + the metric math; use small inline fixtures, not the real eval set).
- `GroundTruthLoader.loadTierA()` returns 6 personas (a-01..a-06) with non-empty corpora.
- `GroundTruthLoader.loadTierAH()` throws when `-Dpattern.eval.tuning=true`.
- `MetricsCalculator.score(...)` on a hand-crafted case returns expected values to 3 decimals.
- `RecallRetention` numbers monotone-non-increasing across stages on a hand-crafted case.
- Code has zero references to LLM strength scoring (regression guard for R13).

## Contracts to call (already on disk; do not re-define)

- `com.ling.linginnerflow.pattern.domain.Domain` — enum, 6 values.
- 12 `pattern_key` strings — read at runtime from `PatternDefinitionLoader.keys()`, do
  NOT hard-code them in the harness (the closed set is taxonomy, not eval).
- Source YAML / md files under `eval/groundtruth/tierA/` and `eval/groundtruth/sealed/`.

## Out of scope

- Running any baseline (PE-2..PE-5).
- Reading the engine's main-source services (orchestrator, verifier). Harness only depends
  on `Domain` and `PatternDefinitionLoader` from main sources.
- Reliability diagrams / ECE. (P1; not in V1.2 P0 scope.)

---

## Drop-in prompt — copy this whole block into Codex or Claude

```
Implement the eval harness in the worktree at .claude/worktrees/pe-1-eval-harness.
Branch: feature/pe-1-eval-harness (already created from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-1-eval-harness.md (this issue spec — single source of truth)
  - docs/issues/README.md (operations manual)
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.2.md (R40, R5, R34)
  - docs/HANDOFF_pattern_engine.md (existing contracts)
  - eval/groundtruth/tierA/a-01.answerkey.yaml + a-01.corpus.md (real example of input)

CONSTRAINTS (non-negotiable):
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/eval/ and eval/README.md.
  - Test scope, not main. No new main-source classes.
  - Tests offline (no network, no DB). Use inline fixtures, not the real eval set.
  - Use the existing PatternDefinitionLoader.keys() at runtime; never hardcode the 12 keys.
  - SnakeYAML is on the classpath via Spring Boot — verify in pom.xml; do not add deps.
  - Java 21, Spring Boot 3.2.5. Lombok available.

DELIVERABLES (exact paths):
  - src/test/java/com/ling/linginnerflow/pattern/eval/GroundTruthLoader.java
  - src/test/java/com/ling/linginnerflow/pattern/eval/MetricsCalculator.java
  - src/test/java/com/ling/linginnerflow/pattern/eval/{GTPersona,GTLabel,CorpusRecord,PredictedPattern,MetricReport,RecallRetention}.java
  - src/test/java/com/ling/linginnerflow/pattern/eval/GroundTruthLoaderTest.java   (4-6 focused tests)
  - src/test/java/com/ling/linginnerflow/pattern/eval/MetricsCalculatorTest.java   (4-6 focused tests)
  - eval/README.md (metric triple + R34 decision rule, 2-3 paragraphs)

ENFORCE:
  - loadTierAH() throws IllegalStateException when System.getProperty("pattern.eval.tuning") == "true".
  - MetricsCalculator emits precision, recall, F1, hardNegativeFPR + recallRetention (4 stages).
  - A scripted regression assertion: the verbatim string "strength" does not appear in your new files
    (R13 regression guard) — express this in a unit test that does a classpath scan of the eval
    package (use ClassLoader/Resources, not file IO).

VERIFY before reporting done:
  ./mvnw -q -DskipTests compile
  ./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.**' test
  Both must succeed.

REPORT BACK:
  - Final list of files created.
  - Test run summary (count pass/fail).
  - Anything you couldn't satisfy + why.

DO NOT push or open the PR — the human handles that.
```
