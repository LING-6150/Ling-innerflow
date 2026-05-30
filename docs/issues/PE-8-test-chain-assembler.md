# PE-8 — Test: EvidenceChainAssembler (4 invariants)

> Lane C. Independent.
> Tracked by **#PE-0**.

## Why

The 4 evidence-chain invariants (≥3 items, ≥1 verbatim, ≥2 distinct days, 0 crisis docs)
are the **only thing standing between the engine and product §17 "label without evidence"
hard-fail mode**. They must be tested explicitly, by hand-crafted positive and negative
fixtures, and they must fail loudly with the documented reason codes.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/verify/EvidenceChainAssemblerTest.java`

No main-source changes.

## Build

Pure unit tests. Build `VerificationResult` lists + `CorpusDoc` lists in-test.

Each test asserts the assembler returns `Optional.empty()` AND emits the documented reason
code (DROP_INSUFFICIENT_EVIDENCE / DROP_NO_VERBATIM / DROP_SINGLE_DAY / DROP_CRISIS).
Use a log appender (e.g. `ListAppender<ILoggingEvent>` from logback) or expose the reason
via the assembler API if it doesn't already — but DO NOT add new public methods just for
testability; if necessary, wrap the appender check.

1. **`happy_path_assembles`** — 4 supports, 1 verbatim, 3 distinct days, 0 crisis →
   returns `AssembledChain` with 4 items.
2. **`fewer_than_three_supports_drops`** — 2 supports → empty + DROP_INSUFFICIENT_EVIDENCE.
3. **`zero_verbatim_drops`** — 4 supports all non-verbatim → empty + DROP_NO_VERBATIM.
4. **`single_day_drops`** — 4 supports, all same `occurredAt.toLocalDate()` → empty +
   DROP_SINGLE_DAY.
5. **`crisis_flagged_source_drops_whole_chain`** — 4 supports but ONE source `CorpusDoc`
   has `crisisFlag=true` → empty + DROP_CRISIS. (V1.2: crisis bombs the whole chain, it
   doesn't just remove the one item.)
6. **`dedup_by_source_ref_before_count`** — 4 results, but 2 share the same
   `CorpusDoc.sourceRef` (different chunks of the same record) → after dedup only 3
   distinct results; if still ≥3/≥1 verbatim/≥2 days → assemble; if dedup drops below
   3 → DROP_INSUFFICIENT_EVIDENCE.
7. **`verbatim_must_match_source_text_exactly`** — feed a result with `verbatimSpan` that
   is NOT a substring of the corresponding `CorpusDoc.text` → the assembler must NOT
   treat it as verbatim (this verifies the cooperation with `EvidenceVerifier`'s
   substring check; if the assembler relies on the verifier already having corrected the
   flag, document that and assert via the verifier path instead).
8. **`majority_vote_domain_with_tiebreak_to_primary`** — 4 results: 2 vote `family`,
   2 vote `self`, primary_domain=`family` → assembled `domain` = `family`.

## Verification

- `./mvnw -q -Dtest='EvidenceChainAssemblerTest' test` green.
- All 8 tests pass.

## Out of scope

- Testing the verifier itself (PE-1 contracts may exist; the verifier is hand-tested
  separately if needed — out of scope for this issue).
- Testing persistence (assembler does not persist).

---

## Drop-in prompt

```
Write EvidenceChainAssembler tests in worktree .claude/worktrees/pe-8-test-chain-assembler.
Branch: feature/pe-8-test-chain-assembler (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-8-test-chain-assembler.md
  - src/main/java/com/ling/linginnerflow/pattern/verify/EvidenceChainAssembler.java
  - src/main/java/com/ling/linginnerflow/pattern/verify/VerificationResult.java
  - src/main/java/com/ling/linginnerflow/pattern/corpus/CorpusDoc.java
  - docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md §9 (chain rules)

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/verify/.
  - JUnit 5. Inline fixtures. No Spring context.
  - For DROP_* reason verification: use logback ListAppender (assertj-logback or manual).
    Do not add new public methods to the assembler just to expose the reason.

DELIVERABLES:
  - EvidenceChainAssemblerTest.java with the 8 tests listed in the issue.

VERIFY:
  ./mvnw -q -Dtest='EvidenceChainAssemblerTest' test

REPORT and DO NOT push.
```
