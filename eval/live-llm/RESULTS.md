# Live-LLM validation of the result-driven loop (#66 / #67)

The deterministic A/B harness ([`LoopAbEvalTest`](../../src/test/java/com/ling/linginnerflow/agent/LoopAbEvalTest.java),
PR #67) compares the **assume-success** (baseline) vs **result-driven** (treatment)
loop using two *synthetic* model profiles (NAIVE / ROBUST). The one open caveat
was: *a synthetic model is not a real LLM.*

This script closes that caveat for the comprehension-dependent claim. It feeds a
**real** model the exact feedback string each loop arm produces for a tool
outcome, and asks for the next action (`USE_RESULT` / `HEDGE` / `RECOVER`).

**Scope (honest):** this validates the *decision-given-feedback* step on the real
tool strings — not a full multi-turn loop (that needs live tools + many turns).
The **uncaught-exception** and **transient-retry** wins are *model-independent*
and already proven deterministically in `LoopAbEvalTest` (retry turns failure
prob `p` into ~`p²`), so they are intentionally out of scope here.

## Result (N=8/cell, temperature=0.7)

Synthetic #67 reference: baseline **NAIVE 20% / ROBUST 60%** → treatment **100%**.

### `gpt-4o-mini` (capable)

| scenario | baseline | treatment |
|---|---|---|
| cbt_success | 8/8 USE_RESULT | 8/8 USE_RESULT |
| cbt_empty (partial) | 8/8 HEDGE | 8/8 HEDGE |
| emotion_empty (partial) | 8/8 HEDGE | 8/8 HEDGE |
| cbt_failure | 8/8 RECOVER | 8/8 RECOVER |
| empty_observation | 8/8 HEDGE | 8/8 HEDGE |
| **aggregate** | **100% (40/40)** | **100% (40/40)** |

### `gpt-3.5-turbo` (weaker)

| scenario | baseline | treatment |
|---|---|---|
| cbt_success | 6/8 (2 spurious HEDGE) | **8/8** |
| cbt_empty (partial) | **2/8** (6 over-react RECOVER) | **8/8** HEDGE |
| emotion_empty (partial) | 8/8 HEDGE | 8/8 HEDGE |
| cbt_failure | 8/8 RECOVER | 8/8 RECOVER |
| empty_observation | 8/8 HEDGE | 8/8 HEDGE |
| **aggregate** | **80% (32/40)** | **100% (40/40)** |

## Reading

- A **capable** model behaves like the synthetic **ROBUST** profile: it handles
  the explicit tool strings on *both* arms (100%/100%). Typed framing causes **no
  regression** — it never makes a good model worse.
- A **weaker** model degrades on the baseline (100%→**80%**) and gets **noisy**
  (treats an empty result as a hard failure, or hedges on a clear success), while
  the treatment stays at **100% with zero variance**. This is the synthetic
  **NAIVE→ROBUST** gap reproduced on a real model.
- **Conclusion:** the value of typed results is *robustness across model quality*
  (and the model-independent crash/retry guarantees), not making a strong model
  smarter on explicit text. The weaker / cheaper the model you deploy, the more it
  pays off.

## Reproduce

Local only (needs an OpenAI key; not run in CI). The key is read from the
environment and never stored in the repo:

```bash
MY_OPENAI_KEY="$(cat ~/.innerflow_openai_key)" \
OPENAI_MODELS="gpt-4o-mini,gpt-3.5-turbo" \
python3 eval/live-llm/live_llm_loop_validation.py
```

Knobs: `OPENAI_MODELS` (comma-separated), `N_REPEATS` (default 8),
`TEMPERATURE` (default 0.7).
