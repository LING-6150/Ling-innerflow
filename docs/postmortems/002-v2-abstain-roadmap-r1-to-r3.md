# Postmortem 002 — V2 Abstain Roadmap R1 to R3

> Date: 2026-05-31
> Status: Prompt-gate line closed as a research path
> Scope: Pattern Engine V2 OOD / abstain experiments after V1 LIVE validation

---

## 1. Why This Work Existed

V1 LIVE validation showed that the Pattern Engine's most dangerous failure was
not just low F1. It was confident mislabeling on full-decoy human prose:

- `ah-05`: V1-full surfaced 0 patterns.
- `ah-06`: V1-full surfaced 11 patterns, many with maximum chain strength.
- V1-no-verify surfaced 12 patterns on `ah-06`.

This made Pattern Structure unsafe as an immediate next product layer. If the
system cannot reliably decide when not to label, any richer structure view risks
making false labels more persuasive.

The V2 research question became:

> Can a post-hoc OOD / abstain gate suppress full-decoy false positives without
> killing true positives?

---

## 2. Pre-Registered Criteria Used for R3

Before R3, the V2 spec fixed these criteria:

- **Hard:** Tier A F1 must be at least `0.300`.
- **Hard:** the V2 gate must output at least `12` `LABEL` decisions across the
  11-persona eval run.
- **Hard:** Tier A killed true positives must be `0` relative to pre-gate
  predictions.
- **Soft:** full-decoy false positives (`ah-05 + ah-06`) should be at most `2`.

Also before R3, `ah-06` was explicitly demoted from final held-out proof to
**dev-set diagnostic**. R1 / R2 / R1.5 had already exposed its behavior to the
design loop.

---

## 3. Experiment Summary

| Run | Strategy | Tier A F1 | LABEL count | ah-05 FP | ah-06 FP | Killed Tier A TP | Decision |
|---|---|---:|---:|---:|---:|---:|---|
| V1-full | no abstain; full verifier | 0.111 | n/a | 0 | 11 | n/a | Too loose; unsafe |
| V1-no-verify | no abstain; verifier removed | 0.333 | n/a | 1 | 12 | n/a | Useful recall, unsafe |
| R1 | strict LLM abstain prompt | 0.000 | 0 | 0 | 0 | all available TP | Too strict |
| R2 | less strict prompt wording | 0.000 | 0 | 0 | 0 | all available TP | Same failure mode as R1 |
| R1.5 | label-biased sanity gate | 0.333 | 18 | 0 | 5 | 0 | Proved gate can label; still unsafe |
| R3 | quote-level LABEL verification | 0.111 | 1 | 0 | 0 | 3 | Safe but too strict |

Primary reports:

- `eval/RESULTS_V2_ABSTAIN.md`
- `eval/RESULTS_V2_ABSTAIN_R2.md`
- `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md`
- `eval/RESULTS_V2_ABSTAIN_R3.md`
- `eval/OFFLINE_RULE_SIMULATION.md`

---

## 4. What Each Run Taught Us

### R1 — strict abstain gate

R1 proved that an LLM gate can suppress `ah-06`, but only by suppressing
everything. It produced `LABEL=0` and Tier A F1 `0.000`.

This was not a usable safety mechanism. It was an all-reject classifier.

### R2 — prompt wording adjustment

R2 changed the rejection reason distribution but not the decision behavior:
`LABEL` remained `0`, and Tier A F1 remained `0.000`.

R2 showed that small prompt wording changes inside the strict-gate frame do not
solve the recall collapse.

### R1.5 — sanity label-biased gate

R1.5 intentionally biased the gate toward `LABEL`. This was a sanity check to
rule out parser bugs, schema bugs, or an unreachable `LABEL` branch.

It proved:

- `LABEL` can be emitted (`LABEL=18` on the latest run).
- JSON parsing is not the issue (`SYSTEM_ERROR=0`).
- Decoy guidance is usable (`DECOY_MATCH` appears).
- Tier A F1 can recover to `0.333`, matching V1-no-verify.

But R1.5 still surfaced 5 full-decoy false positives on `ah-06`. It restored
recall by accepting too many persuasive false labels.

### Offline numeric simulation

`eval/OFFLINE_RULE_SIMULATION.md` simulated pre-declared numeric gates over the
R1.5 surfaced-candidate table.

Best non-killing numeric rules kept all Tier A true positives but still kept 2
full-decoy false positives. The numeric scores (`fit`, `specificity`, products,
sums) did not cleanly separate Tier A true positives from all `ah-06` false
positives.

This suggested that score-threshold-only calibration was insufficient.

### R3 — quote-level verification

R3 required each `LABEL` to include:

- an exact YAML `evidence_shape`; and
- a supporting quote that is an exact substring of the candidate evidence.

Code verified both. This made the gate mechanically stricter than prompt-only
judgment.

R3 suppressed `ah-06` to 0 surfaced patterns, but it also killed 3 Tier A true
positives and emitted only 1 `LABEL` total. It failed all hard criteria:

- Tier A F1: `0.111` instead of required `>=0.300`.
- `LABEL`: `1` instead of required `>=12`.
- Tier A killed true positives: `3` instead of required `0`.

R3 proved that quote-level verification, as a post-hoc gate, is too blunt.

---

## 5. Rejected Hypotheses

The following explanations were tested and rejected:

1. **Parser / JSON bug.** R1.5 emitted `LABEL=18` and `SYSTEM_ERROR=0`.
2. **Prompt wording is the main issue.** R1 and R2 both collapsed to all-reject.
3. **Numeric fit/specificity thresholds are enough.** Offline simulation found
   no clean separation that preserves all true positives while eliminating all
   full-decoy false positives.
4. **Quote-level post-hoc verification solves it.** R3 eliminated `ah-06` but
   killed true positives and collapsed `LABEL` count.
5. **The verifier should be kept as-is.** V1 LIVE showed verifier ablation
   improved Tier A F1 and reduced cost.

---

## 6. Core Finding

The post-hoc LLM gate architecture has two attractors on this dataset:

1. **Too loose:** Tier A F1 around `0.333`, but `ah-06` false positives remain
   high.
2. **Too strict:** `ah-06` false positives drop to 0, but Tier A recall collapses.

The missing signal is not more careful final judgment over an already assembled
candidate. Once retrieval and chain assembly have created a plausible-looking
supportive chain, the final LLM gate is choosing between evidence bundles that
are already biased toward the candidate label.

This suggests the abstain signal must enter earlier: at retrieval, evidence
assembly, or candidate scoring time.

---

## 7. Methodology Notes

- `ah-06` is no longer valid as final held-out proof for V2. It has been used in
  multiple design iterations and is now a dev-set diagnostic.
- Sealed answer keys were not modified in response to failures. A proposed
  modification to `ah-06.answerkey.yaml` was rejected as data leakage.
- The failed R1-R3 line is preserved rather than hidden. It is evidence about
  the system's limits, not wasted work.

---

## 8. Decision

Stop prompt-gate micro-tuning.

Do not run R4 as another post-hoc prompt variant. The next engineering path is
V2.1 contrastive retrieval / differential evidence scoring:

> Evaluate whether a candidate pattern is specific to its own evidence, rather
> than merely plausible under many adjacent pattern definitions.

If V2.1 also fails under pre-registered criteria, close the abstain research
line as a completed milestone and shift the project focus to Phase 2 product
planning with stricter constraints: deep Pattern Structure only for
user-confirmed or high-trust patterns.

---

## 9. Next Artifact

Create:

`docs/superpowers/specs/2026-05-31-pattern-engine-v2.1-contrastive-retrieval.md`

It should define:

- the contrastive retrieval hypothesis;
- how confusable pattern sets are selected;
- the differential scoring formula;
- R4 eval-only implementation scope;
- pre-registered R4 acceptance criteria;
- failure handling and handoff to Phase 2 product planning.

---

## 10. R4 Result and V2 Abstain Research Line Closure

R4 contrastive retrieval (V2.1) ran on the same 11-persona eval. Result:
metric-equivalent to V1-no-verify on every persona. The contrastive gate
filtered zero candidates because supportive vs contrastive margins did not
separate Tier A true positives from `ah-06` full-decoy false positives at any
threshold:

- `ah-06` FP margins: 0.520 to 0.594.
- Tier A true positive margins: 0.446 to 0.526.

R4 hard criteria all passed (Tier A F1 = 0.333, LABEL count = 28, killed true
positives = 0), but the soft full-decoy target failed (`ah-05 + ah-06 = 13`
surfaced, target was at most 2). More importantly, the `Filtered Candidates`
table was empty: the gate produced no actual filtering work, only added
~4000 seconds of wall time and tokens.

This closes the V2 abstain research line. Three independent attractors have now
been mapped on this dataset:

1. R1, R2, R3: post-hoc LLM prompt gate. Either kills true positives or only
   relabels reasons.
2. R1.5 sanity: relaxed prompt gate. Restores Tier A recall but accepts five
   `ah-06` false positives, identical-shape failure to R4.
3. R4 contrastive retrieval: differential margin signal does not separate
   classes. Equivalent to no gate.

No further R-series work is planned on this dataset. Any future abstain claim
requires fresh held-out hard-negative personas authored after research-line
closure. `ah-06` is now permanently a dev-set diagnostic, not a held-out proof
artifact.

The next product-design direction is Pattern Structure MVP. See:

- `docs/product/phase-2-pattern-understanding-plan.md`
- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md`
- `docs/product/phase-2-design-round-1/01-pattern-structure-api-contract.md`
  through `06-pattern-structure-fixtures-acceptance.md`

Phase 2 is explicitly designed to not depend on abstain quality: Pattern
Structure only serves `confirmed` and `partially_confirmed` patterns. R4 does
not change that design; it confirms why it was correct.

R4 artifacts preserved in this PR:

- `eval/RESULTS_V2_CONTRASTIVE_R4.md` — full LIVE result report.
- `src/test/java/.../validation/ContrastiveStrengthCalculator.java` — eval
  harness implementing the §3 confusable matrix and §4 differential scoring
  from the V2.1 spec. Test scope only; no production code modified.
- `src/test/java/.../validation/ContrastiveStrengthCalculatorTest.java` —
  offline unit tests for the harness.
- `src/test/java/.../validation/V2AbstainValidationRunner.java` and
  `StandalonePipeline.java` — extended to add the
  `pattern.v2.contrastive.r4=true` runner mode.

These are kept as research artifacts rather than implementation code, so any
future contrastive-scoring exploration can reuse the harness without
redefining the V2.1 conceptual framework.
