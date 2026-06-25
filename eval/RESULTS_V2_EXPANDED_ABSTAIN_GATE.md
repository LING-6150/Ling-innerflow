# Pattern Engine V2 Expanded Candidate Abstain Sweep

Input: PR #53 evidence-gated expansion candidates plus R1.5 surfaced candidates.

This is an offline gate diagnostic. It does not call an LLM and must not be treated as held-out proof or production abstain behavior.

## Decision Summary

- Baseline gate: keep existing R1.5 surfaced candidates with `fit >= 0.45`, matching the PR #49 safety-constrained operating point.
- Best safety-constrained point: `evidence>=2 terms>=2` has Tier A generated TP `12/12` and full-decoy FP `2`.
- Best recall point: `evidence>=2 terms>=2` has Tier A generated TP `12/12` but full-decoy FP `2`.
- Result: this offline evidence-count proxy can preserve the full PR #53 Tier A recall gain while keeping full-decoy FP at `2`, but Tier A false positives remain high (`11`).
- Cost/latency: `$0.0000` and `0s`; this sweep is deterministic and offline.

Interpretation: evidence count is a useful offline proxy for filtering the probe-expanded set, but it is not a production abstain gate. The `12/12` result is in-sample, and the remaining Tier A FP pressure means the next gate must judge quote-level specificity or use a calibrated learned/LLM score before claiming safety.

## Sweep Points

| rule | Tier A generated | Tier A TP | Tier A recall | Tier A FP | full-decoy FP | added Tier A TP | added Tier A FP | added full-decoy FP |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| baseline only | 13 | 4 | 0.333 | 7 | 2 | 0 | 0 | 0 |
| evidence>=2 terms>=1 | 27 | 12 | 1.000 | 13 | 5 | 8 | 6 | 3 |
| evidence>=2 terms>=2 | 25 | 12 | 1.000 | 11 | 2 | 8 | 4 | 0 |
| evidence>=3 terms>=2 | 23 | 11 | 0.917 | 10 | 2 | 7 | 3 | 0 |
| evidence>=3 terms>=3 | 18 | 9 | 0.750 | 7 | 2 | 5 | 0 | 0 |

## Caveats

- This is still a pre-product eval diagnostic, not a production gate.
- The expanded candidates are probe-authored from Tier A forensics, so Tier A recall gains are in-sample.
- Full-decoy personas are used only as FP confirmation; do not tune future probe terms against their excerpts.
- The recovery target remains full-decoy surfaced FP `<=2` without recall collapse.
