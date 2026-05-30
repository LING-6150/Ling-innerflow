# PE-5 — Baseline B3: Retrieval-no-verify

> Lane B. Depends on **#PE-1**.
> Tracked by **#PE-0**.

## Why

B3 uses the engine's S2 recall + S3 retrieval, then takes top-k retrieved docs as evidence
DIRECTLY — no S4 verification. The B3→full gap is the headline number that justifies the
cost of the verification stage. If B3 ≈ full, the verifier is dead weight.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/eval/baseline/`:
  - `B3_RetrievalNoVerifyBaseline.java`
  - `B3_RetrievalNoVerifyBaselineTest.java`

## Build

```java
public class B3_RetrievalNoVerifyBaseline implements Baseline {
    public B3_RetrievalNoVerifyBaseline(
        CorpusAssemblyService corpus,
        PatternRecallService recall,
        EvidenceRetrievalService retrieval,
        PatternDefinitionLoader defs);

    // Per persona:
    //   1. Convert GTPersona.corpus → in-memory List<CorpusDoc> (no DB read).
    //      Re-use the persona's CorpusRecord → CorpusDoc mapping; embeddings can be stubbed.
    //   2. recall(docs) → candidate keys
    //   3. for each candidate: retrieval.retrieve(key, docs)
    //      if >= 3 docs returned → predict (key, primaryDomainOfKey).
    //      else drop.
    //   4. Skip verification entirely (that's the point).
    Set<PredictedPattern> predict(GTPersona persona);

    // Test seam: allow injecting a fake embeddingProvider so unit tests stay offline.
    @Value("${pattern.eval.b3.live:false}") boolean live;
}
```

- Must reuse the EXISTING services from the engine package (DI-friendly), not reimplement.
- When `live=false`, the embedding-dependent S2/S3 path must accept stubbed embeddings:
  add a constructor taking an `EmbeddingProvider` functional interface that the tests can
  fake. (Don't modify the main services — wrap them.)

## Verification

- Construction succeeds with all real beans + stub embedder.
- A small fixture persona with 5 records that trivially match `people_pleasing` lexical
  cues + a stub embedder that returns identical vectors → B3 predicts people_pleasing.
- B3 with embedder returning all-zero vectors → predicts nothing (no recall).
- Live path `@Disabled("requires live embeddings, see PE-5 issue")`.
- `./mvnw -q -Dtest='*.B3_*' test` green.

## Out of scope

- Calling the real verifier (B3 is the no-verifier ablation by definition).
- Persisting anything to DB.
- Confidence scoring (B3 is a binary surface decision: ≥3 retrieved docs).

---

## Drop-in prompt

```
Implement Baseline B3 in worktree .claude/worktrees/pe-5-baseline-b3.
Branch: feature/pe-5-baseline-b3 (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-5-baseline-b3.md
  - docs/superpowers/specs/2026-05-29-pattern-engine-v1.md §9.4 (B3 design — isolates verifier value)
  - The main-source services it composes: CorpusAssemblyService, PatternRecallService,
    EvidenceRetrievalService (read their public APIs only; do not modify them).
  - PE-1's DTOs.

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/eval/baseline/.
  - Reuse main-source services via DI; do NOT reimplement S2/S3.
  - Default OFFLINE: when -Dpattern.eval.b3.live is unset, use a stubbed EmbeddingProvider
    so unit tests are deterministic and require no network.
  - Skip S4 verification entirely — that is the entire point of this baseline.

DELIVERABLES:
  - B3_RetrievalNoVerifyBaseline.java
  - B3_RetrievalNoVerifyBaselineTest.java (>=3 tests: positive, negative, live-disabled)

VERIFY:
  ./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.baseline.B3_*' test

REPORT and DO NOT push.
```
