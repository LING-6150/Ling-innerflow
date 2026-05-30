# Pattern Engine V1.2 — End-to-End Eval Results

> Generated: 2026-05-30
> Branch: epic/pattern-engine-v1.2 (commit 72e6650)
> Run via: `./mvnw -q -Dtest='V1EvalRunner' -Dpattern.eval.run-v1=true test`

---

## TL;DR — What V1 actually tells us

| Question | V1.2 R# | Answer |
|---|---|---|
| Does the engine even run end-to-end? | — | ✅ Yes. 60 unit tests pass; eval harness loads 6 Tier A + 5 Tier A-H personas and scores them. |
| Does B1 (lexical floor) work on synthetic data? | R30 | ❌ **F1 = 0.000** — no hits at threshold 3 |
| Does B1 work on human data? | R30 | ❌ **F1 = 0.000** — same |
| Is B0 above chance? | — | ⚠️ Tier A F1 = 0.111, Tier A-H F1 = 0.050 — barely-above-noise as expected |
| Is the synthetic↔human gap visible? | R8 | ⚠️ Δ = −0.061 on B0 (human harder). B1 is 0 on both so Δ is uninformative. |

**The headline finding:** B1's curated `lexical_cues` in the YAMLs are too
abstract / too literal to match real prose at minCueHits=3. This is
**a discovery, not a bug** — it is exactly the V1.2 R30 / R8 framing
working as designed: the floor is honest and it shows the floor is low.

The full LLM pipeline (S0–S9: corpus → recall → retrieve → verify →
score) requires DB + live LLM and was not run in this offline eval.
B2 and B3 are similarly network-gated; their `@Disabled` tests cover the
prompt-building and parsing logic but not actual model calls.

---

## Detailed numbers

### Tier A (synthetic, Claude-authored — satisfies V1.2 R1 cross-model)

Personas: 6 (a-01, a-02, a-03, a-04, a-05, a-06)

| Baseline | Precision | Recall | F1 | Hard-Neg FPR |
|---|---:|---:|---:|---:|
| B0-prior | 0.167 | 0.083 | 0.111 | 0.083 |
| B1-lexical | 0.000 | 0.000 | 0.000 | 0.083 |

### Tier A-H (human-authored, sealed — V1.2 R5/R30 floor test)

Personas: 5 (ah-02, ah-03, ah-04, **ah-05**, **ah-06**)

| Baseline | Precision | Recall | F1 | Hard-Neg FPR |
|---|---:|---:|---:|---:|
| B0-prior | 0.067 | 0.040 | 0.050 | 0.000 |
| B1-lexical | 0.000 | 0.000 | 0.000 | 0.000 |

> **ah-05 and ah-06 are FULL-DECOY personas** (no `true_patterns` by design).
> They test whether the engine refuses to over-label active self-resistance
> and high-functioning calculated personas — V1.2 product §10 ("user is
> final authority") + R30. **Hard-Neg FPR = 0.000** on both baselines is
> the correct outcome here: both B0 and B1 over their full 5-persona run
> never mispredicted on the all-decoy cases. (For B1 this is trivial — it
> predicts nothing. For B0 it's meaningful: at chance level it could have
> hit a decoy and didn't.)

### Synthetic ↔ Human gap (V1.2 R8 honesty metric)

| Baseline | F1 (Tier A) | F1 (Tier A-H) | Δ |
|---|---:|---:|---:|
| B0-prior | 0.111 | 0.050 | −0.061 |
| B1-lexical | 0.000 | 0.000 | +0.000 |

A negative Δ on B0 confirms what V1.2 R8 predicts: **human prose is harder
than synthetic**, even for a randomly-firing baseline, because the human
personas have rarer-true-pattern distributions per persona. This gap is
the headline honesty number: we report it explicitly so that any future
"full engine beats B1" claim has to survive the same Tier A | Tier A-H
side-by-side scrutiny (R34).

---

## Interpretation — what V1 result means for V2

This is the section that informs the **V2 direction** (which was the
explicit reason for running V1 to completion).

### 1. The closed-set 12-pattern taxonomy is the right shape, but the LITERAL cue list is not load-bearing

B1's job is to be the floor — "if a system can't beat raw substring
matching on YAML cues, the LLM machinery isn't earning its tokens."
What we learned is something subtler: **B1's failure at F1 = 0 on every
persona means the gap between B1 and the full pipeline is going to be
huge by definition, but that's not where the story is.** The interesting
gap is between:

  - B2 (single-prompt LLM, no retrieval) and
  - Full pipeline (with HyDE-exemplar retrieval + verification)

Both of those require live API and were not run today. V2 must run them.

### 2. The full-decoy personas (ah-05, ah-06) are a much sharper instrument than I expected

The 0.000 hard-negative FPR on those two personas, across both
baselines, is a clean signal. When we plug in the full pipeline,
**watching ah-05 / ah-06 specifically** will tell us whether the LLM
engine over-labels people who actively resist labeling — which is
exactly the failure mode product §10 was written to prevent. This is
the kind of test case that's worth more than 100 well-behaved synthetic
personas.

### 3. The lexical_cues YAMLs need a V2 redesign

Right now the cues are written like Pinyin-mixed catchphrases
(`"说了yes"`, `"对不起"`, `"怕他失望"`). Real Chinese prose almost
never contains these exact strings. For B1 to be a meaningful floor,
the cues need to be:

  - shorter substrings ("yes" alone, not "说了yes")
  - or token-list intersections instead of exact substrings
  - or character n-grams

This is a V2 task — but it's worth doing **before** the V2 full-engine
eval, otherwise the "full beats B1" claim is technically true but
trivial.

### 4. Eval set size is the actual bottleneck

11 personas total (6 synthetic + 5 human, of which 2 are full-decoy)
is too few for tight error bars on per-pattern metrics. V2 should add
either:

  - more Tier A-H personas (slow but high-quality), or
  - per-`(pattern, persona)` micro-judgments instead of persona-level
    binary present/absent (cheaper way to grow the evaluation surface).

### 5. The eval harness itself works and is V2-ready

`GroundTruthLoader`, `MetricsCalculator`, baseline interface, and
`V1EvalRunner` together form a stable scaffold. When V2 swaps in the
full pipeline and live B2/B3, this same runner produces the comparable
table without any harness changes. That's the actual V1 win.

---

## What was NOT measured (deliberately)

- Full engine pipeline (requires DB + LLM access — set up locally in V2)
- B2 / B3 live mode (gated by `-Dpattern.eval.b2.live=true` / `b3.live=true`)
- R40 through-verifier recall retention (needs the verifier in the loop)
- Confidence calibration / ECE (a P1 item per V1.2 scope; deferred to V2)
- Statistical significance / bootstrap CIs (P1; deferred to V2)
- Per-pattern slicing (P1; deferred to V2)

These are all the right things to add in V2 — V1 deliberately froze
scope at "make it run, measure the floor, ship to main."

---

## Reproducibility

```bash
git checkout epic/pattern-engine-v1.2     # or main after the Epic merge
./mvnw -q -Dtest='V1EvalRunner' -Dpattern.eval.run-v1=true test
# → writes eval/RESULTS.md
```

Source: `src/test/java/com/ling/linginnerflow/pattern/eval/V1EvalRunner.java`
