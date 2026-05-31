# PATTERN_ENGINE_V2.1_CONTRASTIVE_RETRIEVAL.md

> InnerFlow — Pattern Engine V2.1 design contract.
>
> Status: **draft — eval-only contrastive retrieval**
> Date: 2026-05-31
> Depends on: `2026-05-30-pattern-engine-v2-ood-abstain.md`,
> `docs/postmortems/002-v2-abstain-roadmap-r1-to-r3.md`,
> `eval/RESULTS_V2_ABSTAIN_R3.md`

---

## 0. Why V2.1 Exists

V2 R1-R3 tested post-hoc LLM abstain gates after V1 had already recalled a
candidate, retrieved supportive evidence, and assembled a plausible evidence
chain.

That line failed:

- strict prompts suppressed false positives but collapsed true positives;
- label-biased prompts preserved Tier A recall but allowed full-decoy false
  positives;
- quote-level verification suppressed full-decoy false positives but killed true
  positives.

The conclusion is not "abstain is impossible." The conclusion is narrower:

> A final LLM gate over an already-supportive evidence chain is too late.

V2.1 moves the abstain signal earlier. Instead of asking only "is there support
for this pattern?", it asks:

> Is this evidence more specific to this pattern than to adjacent confusable
> patterns?

---

## 1. Hypothesis

V1 and V2 R1-R3 use mostly positive evidence. A candidate pattern surfaces when
its own supportive chain is strong enough.

But OOD / hard-negative text can be moderately supportive of many adjacent
patterns at the same time. That broad, non-specific support is itself an OOD
signal.

### V2.1 hypothesis

A candidate should surface only when:

```text
supportive_strength(candidate)
  - max(contrastive_strength(confusable patterns))
  >= margin
```

In words:

> The candidate must not only look plausible. It must look more plausible than
> its nearest alternatives.

### Expected behavior

- True positive examples should show a meaningful margin for the correct pattern.
- Full-decoy examples should show low margin because several adjacent patterns
  all look similarly plausible.

---

## 2. Scope

V2.1 is eval-only.

It may add test-scope classes and reports under `src/test/java/.../validation/`
and `eval/`, but must not modify production `PatternDiscoveryService` until the
R4 eval report exists and passes hard criteria.

### In scope

- contrastive retrieval / scoring inside validation runner;
- confusable pattern set definition;
- margin-based candidate filtering;
- R4 report generation;
- comparison against V1-full, V1-no-verify, R1.5, and R3 results.

### Out of scope

- frontend changes;
- database schema changes;
- user-facing Pattern Structure;
- new pattern taxonomy keys;
- modifying sealed answer keys;
- tuning on `ah-05` / `ah-06` as if they were still clean held-out proof.

---

## 3. Confusable Pattern Sets

V2.1 needs a fixed, pre-declared set of adjacent patterns for each candidate.
This is not learned from `ah-06` results.

### R4 default source

Use the existing 12 YAML pattern definitions and derive confusable sets from
three stable sources:

1. shared primary or `also_in` domain;
2. overlapping evidence-shape semantics in the YAML text;
3. Tier A decoy rationales only.

Do not use Tier A-H sealed answer keys or observed `ah-06` surfaced candidates
to define confusables.

### Initial manual matrix

For R4, use this fixed matrix:

| pattern | confusable patterns |
|---|---|
| `avoidance` | `conflict_aversion`, `emotional_suppression`, `perfectionism`, `rumination` |
| `boundary_difficulty` | `people_pleasing`, `over_responsibility`, `family_pressure`, `conflict_aversion` |
| `comparison_loop` | `worth_through_achievement`, `self_criticism`, `rumination`, `perfectionism` |
| `conflict_aversion` | `boundary_difficulty`, `people_pleasing`, `avoidance`, `emotional_suppression` |
| `emotional_suppression` | `conflict_aversion`, `avoidance`, `self_criticism`, `rumination` |
| `family_pressure` | `boundary_difficulty`, `over_responsibility`, `people_pleasing`, `worth_through_achievement` |
| `over_responsibility` | `boundary_difficulty`, `people_pleasing`, `family_pressure`, `self_criticism` |
| `people_pleasing` | `boundary_difficulty`, `conflict_aversion`, `over_responsibility`, `family_pressure` |
| `perfectionism` | `worth_through_achievement`, `avoidance`, `self_criticism`, `rumination` |
| `rumination` | `self_criticism`, `comparison_loop`, `avoidance`, `emotional_suppression` |
| `self_criticism` | `rumination`, `worth_through_achievement`, `comparison_loop`, `perfectionism` |
| `worth_through_achievement` | `perfectionism`, `comparison_loop`, `self_criticism`, `family_pressure` |

Any future change to this matrix must be made before viewing the next live R4
result and must be recorded in the report.

---

## 4. Differential Evidence Score

R4 should not depend on a final prompt's subjective confidence. It should compute
an explicit differential score from retrieval/evidence traces.

### Supportive strength

For candidate pattern `p`, use the existing pre-gate trace from the no-verify
pipeline:

```text
supportive_strength(p) = chain_strength(p)
```

For R4 eval-only implementation, `chain_strength` can be approximated with the
existing V1 chain score or with a deterministic count-based proxy:

```text
supportive_strength =
  0.50 * min(1.0, evidence_count / 5.0)
+ 0.30 * min(1.0, distinct_days / 4.0)
+ 0.20 * verbatim_ratio
```

If the existing `ConfidenceScorer` output is available in trace, use it as
`chain_strength` but report it as chain strength, not correctness confidence.

### Contrastive strength

For each confusable pattern `q` in `confusables(p)`, score the same candidate
evidence excerpts against `q`'s evidence shapes.

R4 starts with a lexical/semantic proxy before adding any new LLM call:

```text
contrastive_strength(q) = max similarity between candidate evidence excerpts
                          and q.evidence_shapes / q.lexical_cues
```

The implementation may use embeddings already available in the validation path.
If embeddings are reused, report token/cost separately.

### Margin

```text
contrastive_margin(p) = supportive_strength(p)
                      - max_q contrastive_strength(q)
```

A candidate surfaces only if:

```text
supportive_strength >= support_threshold
contrastive_margin >= margin_threshold
```

R4 thresholds must be pre-declared before live eval. Default:

```text
support_threshold = 0.50
margin_threshold = 0.15
```

Threshold rationale:

- `support_threshold=0.50` means the candidate must have at least moderate
  positive evidence before any contrastive margin is considered. This preserves
  the R1.5 lesson that a viable gate cannot demand near-perfect fit.
- `margin_threshold=0.15` is intentionally small: it asks for a visible
  specificity advantage over the strongest confusable pattern without requiring
  a large separation that would likely kill subtle true positives. It is a
  pre-registered engineering prior, not a tuned value from `ah-06`.
- R4 pass/fail uses these defaults only. Any threshold sweep is diagnostic and
  must not be used to retroactively redefine R4 success.

The report may include an offline sweep over thresholds, but the live R4 pass/fail
judgment uses the defaults above.

---

## 5. R4 Eval Runner

Add an eval-only runner variant:

```text
V2-contrastive-r4
```

Expected report:

```text
eval/RESULTS_V2_CONTRASTIVE_R4.md
```

The report must include:

- V1-full summary;
- V1-no-verify summary;
- V2-contrastive-r4 summary;
- full-decoy safety table;
- per-persona true hits before/after contrastive gate;
- surfaced candidates with supportive strength, max contrastive strength,
  margin, and strongest confusable pattern;
- filtered candidates with the same diagnostic fields;
- a conclusion section explicitly stating that `ah-06` is a dev-set metric, not
  evidence that V2.1 has solved NPD-style over-labeling;
- cost and runtime.

Do not include R1/R2/R3 prompt-gate variants in every R4 table unless needed for
context. The postmortem already records them.

---

## 6. R4 Acceptance Criteria

Before any live R4 run, these criteria are fixed:

### Hard criteria

- Tier A F1 must be at least `0.300`.
- Tier A killed true positives must be `0` relative to V1-no-verify pre-gate
  predictions.
- The contrastive gate must surface at least `12` labels across the full
  11-persona run.

### Soft criteria

- Full-decoy false positives (`ah-05 + ah-06`) should be at most `2`.
- Runtime should not exceed V1-full by more than 2x.
- Additional token cost should be lower than the R3 quote gate.

### Interpretation rules

- If hard criteria fail, R4 fails even if `ah-06` improves.
- If hard criteria pass and soft full-decoy safety fails, R4 is a partial success
  but not a shippable abstain mechanism.
- `ah-06` remains dev-set diagnostic, not final held-out proof.

---

## 7. Failure Handling

If R4 fails hard criteria, do not keep tuning the same matrix/margins against
`ah-06`.

Close the V2 abstain research line with the following conclusion:

> On the current 11-persona eval set, V1 closed-set pattern discovery cannot be
> made safely abstentive through post-hoc LLM gating or first-pass contrastive
> retrieval without either preserving full-decoy false positives or killing true
> positives.

Then shift effort to Phase 2 product planning:

- Pattern Structure only for user-confirmed patterns;
- no Structure generation for unconfirmed candidates;
- explicit UX copy that pattern cards are hypotheses until confirmed;
- future data collection for a larger hard-negative held-out set before any
  renewed abstain claim.

---

## 8. Why This Is Still Valuable If It Fails

A failed R4 is not project failure. It would complete a disciplined research
arc:

1. V1 discovered a dangerous false-positive mode.
2. V2 R1-R3 showed post-hoc LLM gating cannot resolve it cleanly.
3. V2.1 R4 tested whether moving the signal upstream helps.
4. If not, product design adapts: deep understanding layers require user
   confirmation instead of engine-only trust.

That is a strong engineering narrative: build, evaluate, find the limit, change
strategy.
