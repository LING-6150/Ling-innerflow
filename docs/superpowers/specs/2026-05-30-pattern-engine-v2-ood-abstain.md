# PATTERN_ENGINE_V2_OOD_ABSTAIN.md

> InnerFlow — Pattern Engine V2 design contract.
>
> Status: **V2 draft — OOD / abstain only**
> Date: 2026-05-30
> Depends on: `2026-05-29-pattern-engine-v1.2.md`, `eval/RESULTS_LIVE.md`,
> `eval/RESULTS_DECOY.md`, `docs/STATE.md`
>
> Where this document conflicts with V1 / V1.1 / V1.2, this document wins for
> V2 work only.

---

## 0. Why V2 Exists

V1 validation answered the wrong question decisively enough to reveal the right
one.

The V1 pipeline does not merely underperform a simple single-prompt baseline.
Its most important failure is that it confidently labels text that should not be
classified inside the V1 pattern taxonomy at all:

- `ah-05` correctly surfaced 0 patterns.
- `ah-06` surfaced 11 patterns even though the answer key is full-decoy.
- 8 of those 11 `ah-06` false positives had confidence `1.000`.
- The false-positive interpretations looked reasonable in isolation, making the
  error persuasive rather than obviously wrong.

This is the central V2 problem:

> V1 is a closed-set pattern classifier. V2 must become a pattern mirror that can
> say: **"this does not belong to any pattern I am allowed to label."**

The V2 goal is not higher complexity. The goal is reliable abstention.

---

## 1. Scope

### In scope

V2 adds an explicit **OOD / abstain gate** to the pattern pipeline.

The gate decides, before a candidate is surfaced, whether the system has enough
positive evidence that the user's text belongs to one of the 12 V1 pattern
definitions. If not, the candidate is suppressed and recorded as an abstention.

V2 may change:

- evaluation metrics and reports;
- validation runners;
- candidate surfacing logic;
- confidence interpretation;
- verifier usage, if it is reframed as an abstain signal rather than a
  confidence booster;
- tests around decoy personas and hard negatives.

### Out of scope

V2 must not attempt these until abstention is working:

- new user-facing pattern structure graphs;
- joint calibration across multiple surfaced patterns;
- taxonomy expansion beyond the 12 V1 pattern keys;
- therapist-style interpretation or causal diagnosis;
- broad frontend redesign;
- rewriting the whole pattern engine.

Reason: V1 evidence chains are not reliable enough on human prose. Structural
features built on unreliable labels become noise amplifiers.

---

## 2. Product Principle

InnerFlow is a mirror, not a therapist.

For V2, this means:

> The system must prefer silence over a persuasive false mirror.

A candidate pattern is allowed to surface only when the system can defend all
three claims:

1. **Fit:** the evidence directly matches the named pattern definition.
2. **Specificity:** the evidence is more consistent with this pattern than with
   adjacent patterns or with no V1 pattern.
3. **Non-decoy:** the evidence is not better explained as a known hard-negative
   structure from the decoy answer keys.

If any claim fails, V2 abstains.

---

## 3. Definitions

### Closed-set prediction

The V1 behavior: every recalled candidate is forced through one of the 12 known
pattern labels if enough retrieved evidence passes chain assembly and confidence
thresholds.

### OOD

Out-of-distribution for this project does not mean "weird text". It means:

> The text may be psychologically meaningful, but it does not safely match any
> V1 pattern label the system is authorized to surface.

`ah-06` is OOD with respect to the V1 taxonomy even though its text is coherent,
emotionally loaded, and semantically close to several V1 patterns.

### Abstain

A deliberate non-surfacing decision with a reason code. Abstention is not a
runtime failure and not an empty result. It is a successful safety behavior.

### Hard negative

A corpus item or persona intentionally designed to look close to one or more V1
patterns while not being a true positive. `ah-05` and `ah-06` are full-persona
hard negatives.

---

## 4. Required V2 Decision Shape

V2 changes the surfacing decision from:

```text
surface = confidence >= threshold
```

to:

```text
surface = positive_fit_passes
       && specificity_passes
       && decoy_guard_passes
       && calibrated_surface_score >= threshold
```

This is intentionally conjunctive. A high confidence score cannot override a
failed abstain gate.

### Required reason codes

Every suppressed candidate must record exactly one primary reason code:

- `INSUFFICIENT_POSITIVE_FIT` — evidence does not directly support the pattern
  definition.
- `LOW_SPECIFICITY` — evidence is generic or equally plausible under multiple
  labels.
- `DECOY_MATCH` — evidence resembles a known answer-key decoy structure.
- `CHAIN_TOO_WEAK` — evidence fails chain invariants.
- `MODEL_UNCERTAIN` — LLM judge explicitly cannot choose label over abstain.
- `SYSTEM_ERROR` — infrastructure or parsing failure; this is not counted as a
  valid abstention in metrics.

The report must distinguish valid abstentions from system errors.

---

## 5. OOD / Abstain Gate Design

V2 should be implemented as the smallest useful gate after retrieval and before
surfacing. It may run before or after the existing verifier during experiments,
but the final report must compare the variants.

### Gate A — positive fit

Question:

> Does this evidence directly instantiate the pattern definition, using the
> definition's observable criteria rather than the model's broad psychological
> interpretation?

Required behavior:

- reject metaphorical, causal, or diagnostic leaps;
- require quote-level support for the core pattern behavior;
- treat a polished interpretation as insufficient without direct textual fit;
- prefer false negatives over persuasive false positives.

### Gate B — label vs abstain forced choice

For each candidate, ask the model to choose between:

1. the candidate pattern label;
2. `ABSTAIN_NO_SAFE_V1_LABEL`.

The prompt must include:

- the exact pattern definition;
- the candidate evidence snippets;
- at least 2 adjacent/confusable pattern definitions when available;
- the rule: "If the evidence could be meaningful but not safely this label,
  choose abstain."

The output must be structured and parser-tested.

### Gate C — decoy guard

The system must use `decoys[].why_not` from answer keys as first-class negative
guidance in eval, not as labels to fit.

For live product code, do not hard-code persona-specific decoys. Instead, derive
general decoy signatures from the answer-key rationale, such as:

- performed self-criticism followed by self-exoneration;
- manipulative silence misread as conflict aversion;
- external blame or image management misread as self-reflection;
- emotional intensity misread as recurrence.

The V2 spec does not require a perfect decoy library. It requires the eval to
show whether adding one reduces hard-negative false positives without collapsing
true-positive recall.

### Gate D — calibrated score reinterpretation

V1 confidence is not evidence of correctness. It is mostly evidence-chain
quantity, recurrence, and recency. V2 must rename or reinterpret it in reports as
`chain_strength` unless and until it is calibrated against correctness.

V2 surfacing must not call a score `confidence=1.000` unless that score is
calibrated to correctness on held-out labels.

---

## 6. Metrics

V2 headline metrics must include safety, not only F1.

### Required metrics

Report these for Tier A, Tier A-H, and full-decoy subsets:

- precision;
- recall;
- F1;
- abstain rate;
- hard-negative false-positive rate;
- decoy persona surfaced-count distribution;
- verifier/gate token cost;
- runtime per persona.

### Headline success metric

The primary V2 success metric is:

```text
full_decoy_fp_count = surfaced patterns on ah-05 + surfaced patterns on ah-06
```

V1 value:

```text
ah-05: 0
ah-06: 11
total: 11
```

V2 target:

```text
total <= 2
```

Stretch target:

```text
total == 0
```

### Anti-cheat metric

A system that abstains on everything is not useful. Therefore V2 must also
retain meaningful recall:

```text
Tier A recall >= 0.50 * B2 Tier A recall
```

and must not sign-reverse against B2 on Tier A-H:

```text
Tier A-H F1 should not be lower than V1 full pipeline unless full-decoy safety
improves materially.
```

Because Tier A-H is small, this is a directional guard, not a statistical claim.

---

## 7. Baselines and Ablations

V2 evaluation must compare at least these systems:

1. `B2` — single-prompt baseline from V1.
2. `V1-full` — existing full pipeline.
3. `V1-no-verify` — existing verifier ablation.
4. `V2-fit-gate` — positive-fit gate only.
5. `V2-fit-plus-abstain` — positive-fit + label-vs-abstain forced choice.
6. `V2-fit-abstain-decoy` — all gates enabled.

The report must answer:

- Does V2 reduce `ah-06` false positives?
- Does V2 reduce confident false positives specifically?
- Does V2 preserve enough Tier A recall to remain useful?
- Does verifier still hurt once abstention is added?
- Which gate contributes the biggest safety gain per token?

---

## 8. Implementation Plan

### Step 1 — eval-first contract

Add an eval runner that can replay V1 reports and compare V2 variants without
touching production persistence.

Expected files:

- `src/test/java/com/ling/linginnerflow/pattern/validation/V2AbstainValidationRunner.java`
- `src/test/java/com/ling/linginnerflow/pattern/validation/AbstainGate.java`
- `src/test/java/com/ling/linginnerflow/pattern/validation/AbstainDecision.java`
- `eval/RESULTS_V2_ABSTAIN.md`

### Step 2 — structured abstain prompt

Create a parser-tested structured output contract:

```json
{
  "decision": "LABEL" | "ABSTAIN_NO_SAFE_V1_LABEL",
  "primary_reason": "INSUFFICIENT_POSITIVE_FIT" | "LOW_SPECIFICITY" | "DECOY_MATCH" | "MODEL_UNCERTAIN",
  "fit_score": 0.0,
  "specificity_score": 0.0,
  "rationale": "short, evidence-grounded explanation"
}
```

Scores are diagnostic only in the first implementation. They must not become
user-facing confidence.

### Step 3 — report hard negatives first

Before optimizing aggregate F1, make a short report section for only `ah-05` and
`ah-06`:

- surfaced candidates;
- abstained candidates;
- reason-code counts;
- before/after false-positive count;
- examples of prevented persuasive false positives.

### Step 4 — production hook only after eval passes

Only after V2 eval shows improvement should production
`PatternDiscoveryService` add the abstain gate before persistence.

Production persistence must store suppressed candidate metadata separately from
surfaced `PatternInstance` records, or log it behind an eval/debug flag. Do not
show abstained candidates to users.

---

## 9. Acceptance Criteria

V2 is acceptable only if all are true:

1. `eval/RESULTS_V2_ABSTAIN.md` exists and compares V1, B2, and V2 variants.
2. Full-decoy false positives drop from 11 to at most 2.
3. The report includes reason-code counts for abstentions.
4. Tier A recall does not collapse below the anti-cheat threshold.
5. No user-facing text calls an uncalibrated chain score `confidence=1.000`.
6. Tests cover structured-output parsing and at least one hard-negative case.
7. Production code is not changed until eval evidence justifies the gate.

### R3 pre-registered acceptance criteria

Before running any R3 live eval, the following criteria are fixed and must not
be changed after seeing R3 results:

- **Required:** Tier A F1 must be at least `0.300`.
- **Required:** the V2 gate must output at least `12` `LABEL` decisions across
  the 11-persona eval run, proving it did not regress to all-reject behavior.
- **Required:** Tier A killed true positives must be `0` relative to the
  pre-gate predictions.
- **Expected:** full-decoy false positives (`ah-05 + ah-06`) should be at most
  `2`.
- **Failure handling:** if R3 misses the expected full-decoy target, do not keep
  tuning on `ah-05` / `ah-06`. Treat the remaining gap as evidence that V2 needs
  a larger held-out set or a new non-test tuning set.

Because R1 / R2 / R1.5 exposed `ah-06` surfaced-candidate behavior to the design
loop, R3 `ah-06` numbers are **dev-set diagnostics**, not final held-out proof.
R3 may report them, but must not claim them as sealed validation evidence.

R3 may use only these sources for new generic decision rules:

- Tier A decoy rationales;
- V1 / V1.2 / V2 specs;
- public, non-persona-specific domain knowledge.

R3 must not derive new rules from `ah-05`, `ah-06`, or their observed surfaced
false positives. That would turn the sealed set into a dev set.

### R3 offline simulation discipline

Before any additional live R3 run, use the existing R1.5 surfaced-candidate
report to simulate only pre-declared numeric gates:

- `fit >= threshold` for thresholds `0.0..1.0` in `0.1` increments;
- `specificity >= threshold` for thresholds `0.0..1.0` in `0.1` increments;
- `fit * specificity >= threshold` for thresholds `0.0..1.0` in `0.05`
  increments;
- `fit + specificity >= threshold` for thresholds `0.0..2.0` in `0.1`
  increments.

The report must show a Pareto-style table with at least Tier A true positives
kept, Tier A killed true positives, and full-decoy false positives kept. Do not
cherry-pick a single rule without reporting the alternatives.

The first non-numeric R3 gate may be quote-level verification only: when the LLM
returns `LABEL`, it must also return a quoted evidence span and a matched
`evidence_shape`. Code must verify that the quote is present in the candidate
evidence excerpts and that the shape is one of the YAML-defined shapes for that
pattern.

---

## 10. Non-Goals and Failure Conditions

### Non-goals

- Do not make the model diagnose narcissism, personality disorders, or causes.
- Do not add a thirteenth pattern key to catch `ah-06`.
- Do not tune on Tier A-H as if it were a large decision set.
- Do not hide false positives by deleting reports or changing answer keys.
- Do not modify sealed answer keys or sealed decoy rationales in response to a
  specific eval failure.

### Failure conditions

V2 fails if any of these happen:

- `ah-06` still surfaces more than 2 patterns.
- false positives remain high-confidence in reports.
- abstention reduces every true-positive persona to zero surfaced patterns.
- the only improvement comes from a hard-coded persona ID check.
- an AI assistant proposes changing sealed answer keys after seeing eval
  failures; that suggestion is a methodological failure mode, not a fix, and
  must be rejected and documented.
- a held-out persona is used to inform more than two system-adjustment
  iterations but is still described as held-out. It must be promoted to dev-set
  status and replaced by fresh held-out personas before final V2 claims.
- the verifier remains expensive and harmful but is kept because it was part of
  V1.

---

## 11. Recommended Next Task

Open a small implementation branch for V2 eval only:

```text
feature/v2-abstain-eval
```

First deliverable:

```text
V2AbstainValidationRunner + RESULTS_V2_ABSTAIN.md
```

Do not modify frontend. Do not modify production persistence. Do not redesign
the taxonomy. The first V2 milestone is a measured answer to one question:

> Can InnerFlow abstain on `ah-06` without becoming useless on true positives?
