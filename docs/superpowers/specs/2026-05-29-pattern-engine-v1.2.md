# PATTERN_ENGINE_V1.2.md

> InnerFlow — AI System Design, **amendment** to `PATTERN_ENGINE_V1.1.md`.
>
> Status: **V1.2 — P0-closure corrections only.**
> Date: 2026-05-29
> Companion to: `PATTERN_DISCOVERY_V1.md` (frozen product spec),
> `PATTERN_ENGINE_V1.md` (engine design), `PATTERN_ENGINE_V1.1.md` (P0 fixes).
> Where this doc conflicts with any prior, **V1.2 wins.**

## 0. Scope of This Amendment

V1.1 fixed the four P0 findings. The V1.1 review confirmed all four are closed,
but found that **the act of fixing them introduced four new defects**, all
sharing one meta-pattern:

> **The V1.1 fixes over-corrected — swinging from "trust synthetic / trust the
> LLM" to "trust a tiny human set / conservatively drop." The correct posture is
> never an extreme; it is *responsibility separation*: each data source and each
> check does only the one thing it can credibly do, and never more.**

This amendment closes those four defects (NEW-1 … NEW-4). It is **logic- and
responsibility-level tightening only — no architectural rewrite.** P1/P2 remain
deliberately untouched.

Out of scope (unchanged): all P1 (determinism, min-distinct-days, window length,
taxonomy-overlap dedup, κ level, ablation significance, cost/incremental), all
P2, and the product spec.

The governing principle for every clause below:

> **Each source does only what it can credibly do.**
> Synthetic data → statistical power. Human held-out → directional validation.
> Human labels → the single accuracy anchor. Cross-model → sample expansion.
> No source is permitted to exceed its competence.

---

## 1. NEW-1 — Verifier Position-Bias: Binary Decision, No Per-Item Drop

Closes the defect introduced by V1.1 **R16**.

**The defect introduced by R16:** R16 handled batch position-bias by
*re-running with shuffled order and dropping any item whose `supports` decision
flipped*. This per-item drop is itself a biased repair: subtle/borderline
evidence flips more often than textbook evidence, so the drop rule
**systematically removes evidence for subtle patterns** — re-introducing exactly
the kind of recall loss P0-2 just eliminated, in a new location. "Drop the
flipping item" is a biased patch over a *systematic* verifier property.

**The fix — R16 is replaced by R16′:**

- **R16′-a. Position-bias is a property of the verifier, not of an item.** It is
  measured at the batch level, not repaired at the item level.
- **R16′-b. Binary, data-driven mode selection.** On the entailment ground-truth
  set (R14/R25), measure the batch verifier's **position-flip rate** (fraction of
  `supports` decisions that change under order randomization):
    - If flip rate **≤ published bound** → the batched verifier is trusted as-is.
      **No per-item drop is performed.** All `supports=true` items stand.
    - If flip rate **> published bound** → the **entire verifier switches to
      independent single-item calls** (V1.1 R16 fallback). In single-item mode,
      position-bias cannot exist by construction, so there is nothing to "flip" and
      nothing to drop.
- **R16′-c. The intermediate "shuffle-and-drop-the-flippers" state is removed.**
  It is forbidden: it trades an honest systematic-bias signal for a hidden,
  pattern-correlated recall leak.
- **R16′-d.** The flip rate (the quantity that selects the mode) remains a
  reported eval metric.

Net effect: the verifier is either trusted whole or replaced whole. Recall is no
longer silently shaved by an order-sensitivity artifact.

---

## 2. NEW-2 — Tier A-H Is a Directional Validator, Not a Decision Arbiter

Closes the defect introduced by V1.1 **R5/R6/R8/R30/R32**.

**The defect introduced by V1.1:** to escape style-homophily (P0-1), V1.1 handed
*final decision authority* to Tier A-H — R32 ("any architectural decision must be
defensible by the Tier A-H column"), R30 (headline = "beat B1 on Tier A-H"). But
Tier A-H is 5–10 human personas; per `(pattern, domain)` cell it holds ~1–3
samples. That is **enough to detect a confound, nowhere near enough to arbitrate
a single-variable architectural decision.** V1.1 swapped "trust synthetic (has
power, has confound)" for "trust a tiny human set (no confound, no power)." Both
extremes are wrong; neither set can carry decisions *alone*.

**The fix — responsibility split between the two sets:**

- **R33. Statistical power lives on Tier A; directional truth lives on Tier A-H.**
    - **Tier A (synthetic, larger):** the set on which decisions are *made*. It has
      the sample size to produce statistically meaningful precision/recall/F1 and
      per-parameter sweeps.
    - **Tier A-H (human, sealed, small):** the set on which decisions are
      *direction-checked*. It does not arbitrate magnitude; it answers one
      yes/no question: **does the decision made on Tier A hold *directionally* on
      human prose?**
- **R34. The decision rule (replaces R32).** A mechanism is kept iff:
    1. it shows a statistically supported gain **on Tier A**, **AND**
    2. that gain does **not reverse direction** on Tier A-H.

  A gain on Tier A that *reverses sign* on Tier A-H → the Tier A result is
  declared **style-confounded and void** (the original R8 spirit, now correctly
  scoped). A gain on Tier A that merely *shrinks* on Tier A-H (same sign, smaller
  magnitude, within Tier A-H's wide error bars) is **retained** — small-sample
  magnitude shrinkage is expected and is not grounds for rejection.
- **R35. Headline claim (replaces R30).** The headline is no longer "beat B1 on
  Tier A-H" (which a 5–10 sample set cannot establish with significance).
  It becomes a **two-part statement**:
  > "Full pipeline beats B1 **with statistical support on Tier A**, and that
  > advantage **holds directionally on the sealed human set (Tier A-H)**."
  Both clauses are required; neither alone is the claim.
- **R36. Tier A-H is reported with explicit small-sample caveats** — direction and
  sign only, never a headline magnitude, never a significance claim. Its job is
  *falsification of confounds*, not *measurement of effect size*.

Net effect: synthetic data is no longer distrusted into uselessness; it does the
quantitative work it is statistically capable of, while the human set does the
one thing it is uniquely capable of — catching a confound by sign reversal.

---

## 3. NEW-3 — Per-Pattern Thresholds: Fit on Tier A, Sanity-Check on Tier A-H

Closes the defect introduced by V1.1 **R21**.

**The defect introduced by R21:** R21 made the S2 similarity threshold
*per-pattern*, fit on "that pattern's positive/negative examples." Combined with
R32 (decisions must be defensible on Tier A-H), this is impossible: Tier A-H has
~1–2 positives per pattern — far too few to fit a per-pattern cutoff. The
per-pattern threshold could therefore only be fit on style-confounded Tier A,
then "validated" on a statistically powerless set.

**The fix — apply the R33/R34 responsibility split to thresholds:**

- **R37. Per-pattern S2 thresholds are *fit* exclusively on Tier A.** Providing
  statistical power for parameter fitting is precisely what synthetic data is
  *legitimately for*. This is an explicitly sanctioned use of Tier A.
- **R38. Tier A-H performs a *collapse check*, not a re-fit.** On the sealed human
  set, verify only that the Tier A-fit per-pattern threshold does **not cause any
  pattern's recall to collapse** on human prose (operationalized: no pattern's
  Tier A-H recall retention falls below a published floor relative to its Tier A
  retention). If a pattern collapses, that pattern's threshold is flagged as
  style-confounded and that *single pattern* ships with a conservative fallback
  (or disabled, per product §17 flag posture) — the rest are unaffected.
- **R39.** This makes the per-pattern design (technically correct: ada-002 cosine
  distributions differ across patterns) compatible with the data reality:
  **fit where there is power (A), falsify where there is no confound (A-H).**

---

## 4. NEW-4 — Cross-Model Verifier: Sample-Expander, Not Correctness Proxy

Closes the defect introduced by V1.1 **R15**.

**The defect introduced by R15:** R15 used a non-OpenAI judge to "bound how much
the `supports` signal is OpenAI-specific" via agreement. But this silently
substitutes the question "is the verifier *correct*?" with "do two models
*agree*?" — and **agreement ≠ correctness**: two models can be wrong for the same
reason (e.g. both over-sensitive to the same LLM-ese cue). Worse, it creates an
infinite regress (who validates the second judge?).

**The fix — R15 is replaced by R15′, with strict role limits:**

- **R15′-a. The single accuracy anchor is the human entailment label set
  (R14/R25). Full stop.** Verifier precision/recall is measured **only** against
  human labels. No model-vs-model agreement number is ever reported as verifier
  accuracy.
- **R15′-b. Cross-model agreement is a *disagreement-surfacing* tool, nothing
  more.** The non-OpenAI judge re-labels a sample; **disagreements** between it
  and the OpenAI verifier are routed to human adjudication and **fold into the
  R14 human set**, cheaply growing the only set that actually anchors accuracy.
- **R15′-c. Agreement is explicitly forbidden as a correctness proxy.** A clause
  for the harness/report: *"Cross-model agreement may not be cited as evidence of
  verifier correctness. High agreement bounds nothing about correctness; it only
  reduces the yield of new human-label candidates."*
- **R15′-d.** The regress is closed by construction: no model validates another
  model. Models only *surface candidates* for human labeling; **humans are the
  terminal authority** on entailment correctness.

Net effect: cross-model checking earns its keep (it efficiently grows the human
anchor set by targeting disagreements) without ever pretending agreement is
truth.

---

## 5. Cross-Cutting: Reconciling R18 (recall-first) with the Verifier (NEW-1 tension)

Surfaced by the V1.1 review §3. Not a new defect — a coordination requirement
between two correct P0 fixes that pull opposite directions.

**The tension:** P0-2's R18 deliberately loosens early gates and **pushes recall
protection downstream to the verifier**. P0-4's verifier (now R16′) can switch to
a conservative single-item mode. If the verifier is net-conservative, the recall
R18 worked to preserve can be re-shaved at verification — the two P0 fixes
partially cancel.

**The fix — make the coordination observable and binding:**

- **R40. Recall retention (V1.1 R19) MUST be measured *through the verifier*, not
  only up to it.** The stage-wise retention report extends to a post-S4 column:
  of truly-present patterns (Tier A), what fraction still has a valid ≥3-item,
  ≥1-verbatim chain **after** verification. This is the number that proves R18's
  preserved recall was not silently consumed by R16′.
- **R41. R18's S2 operating point and R16′'s verifier mode are tuned as a
  *pair*, against post-verifier recall (R40), not independently.** The S2 gate is
  not considered "set" until the end-to-end (through-verifier) recall on Tier A
  meets the recall target. Tuning one while ignoring the other is forbidden.

This guarantees the two opposite-pulling P0 fixes are reconciled by a single
shared, measured quantity (post-verifier recall) rather than by hope.

---

## 6. Summary of Binding Changes (V1.1 → V1.2)

| # | Change | Replaces / amends | Closes |
|---|---|---|---|
| R16′ | Position-bias → binary mode select (trust-whole / single-item-whole); no per-item drop | V1.1 R16 | NEW-1 |
| R33–R36 | Tier A = statistical power (decide); Tier A-H = directional validator (falsify confound by sign reversal); two-part headline | V1.1 R5/R6/R8/R30/R32 | NEW-2 |
| R37–R39 | Per-pattern thresholds fit on Tier A, collapse-checked on Tier A-H | V1.1 R21 | NEW-3 |
| R15′ | Human labels = sole accuracy anchor; cross-model = disagreement-surfacing/sample-expander only; agreement ≠ correctness | V1.1 R15 | NEW-4 |
| R40–R41 | Recall retention measured *through* the verifier; S2 gate + verifier mode tuned as a pair on post-verifier recall | V1.1 R18/R19 + R16′ | cross-cut |

Everything not listed is unchanged from V1.1 / V1. P1 and P2 remain unaddressed
by design.

---

## 7. Closure Statement

With V1.2, the meta-defect the V1.1 review identified — **swinging between
extremes** — is resolved by a single consistent rule applied four times:

| Source / check | Sole legitimate job | Forbidden from |
|---|---|---|
| Tier A (synthetic) | statistical power: fit params, make decisions | being the confound-free truth |
| Tier A-H (human, sealed) | directional falsification of confounds (sign reversal, collapse) | arbitrating magnitude / significance |
| Human entailment labels | the one accuracy anchor for the verifier | (nothing — it is terminal) |
| Cross-model judge | surfacing disagreements to grow the human set | proxying correctness |
| Verifier mode (batch/single) | one honest system-level choice | per-item bias-shaving |

No source exceeds its competence. The four P0s are closed, and the four repairs
to those P0s are themselves now closed, without opening a fifth.

**This document is implementation-ready.** The next artifact is code: `S0`
corpus assembly and `S2` recall (now HyDE-exemplar-based, R17-RET), validated
end-to-end on one Tier A persona, with the recall-retention harness (R40) wired
from the first commit so the R18↔R16′ reconciliation is observable from day one.

---

*End of PATTERN_ENGINE_V1.2.md.*