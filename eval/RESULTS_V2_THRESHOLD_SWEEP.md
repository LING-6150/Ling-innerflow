# Pattern Engine V2 Threshold Sweep

Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` surfaced candidates.

This is an offline calibration diagnostic. It does not call an LLM and must not be treated as held-out proof.

## Decision Summary

- Candidate source: R1.5 label-biased gate surfaced table.
- Candidates swept: 18
- Recovery target: Tier A F1 comparable to B2 (`0.386`) while full-decoy false positives stay `<= 2`.
- Tier A sample size: 6 personas, 12 true labels, 4 true positives present in the R1.5 surfaced set.
- Recall ceiling: thresholding can only remove R1.5 candidates, so Tier A recall cannot exceed the no-threshold value `0.333`; this caps post-hoc threshold F1 before the sweep starts.
- Result: no swept rule meets the full recovery target on this candidate table.
- Best safety-constrained rule: `fit >= 0.450` keeps full-decoy FP at 2 with Tier A F1 0.348.
- Best Tier A F1 rule: `fit >= 0.450` reaches Tier A F1 0.348 with full-decoy FP 2.
- Threshold stage cost/latency: `$0.0000` and `0s`; candidate-generation cost/latency comes from the R1.5 source report.

Interpretation: simple post-hoc numeric thresholds over R1.5 `fit` and `specificity` scores can reduce decoy false positives, but they cannot recover candidates that the generator never surfaced. The best point slightly improves over the same-candidate no-threshold baseline (Tier A F1 `0.320`) while cutting full-decoy FP from 5 to 2, but it remains below the cross-pipeline B2 bar (`0.386`). Differences below roughly `0.05` F1 should not be over-read on this small candidate table. The next step should improve the candidate generator or abstain score, not add Pattern Structure modules.

## Key Operating Points

| point | rule | threshold | Tier A precision | Tier A recall | Tier A F1 | overall abstain rate | full-decoy FP | killed Tier A TP |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| best safety-constrained F1 | fit | 0.450 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 |
| best Tier A F1 | fit | 0.450 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 |
| no threshold baseline | fit | 0.000 | 0.308 | 0.333 | 0.320 | 0.000 | 5 | 0 |

## Pareto Slice

| rule | threshold | Tier A precision | Tier A recall | Tier A F1 | overall abstain rate | full-decoy FP | killed Tier A TP | kept / candidates | recovery target met |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| fit | 0.450 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 | 13/18 | no |
| fit | 0.500 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 | 13/18 | no |
| fit * specificity | 0.150 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 | 13/18 | no |
| fit + specificity | 0.750 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 | 13/18 | no |
| fit + specificity | 0.800 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 | 13/18 | no |
| fit | 0.350 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit | 0.400 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit * specificity | 0.100 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit + specificity | 0.550 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit + specificity | 0.600 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit + specificity | 0.650 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit + specificity | 0.700 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| min(fit, specificity) | 0.250 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| min(fit, specificity) | 0.300 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| specificity | 0.250 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| specificity | 0.300 | 0.333 | 0.333 | 0.333 | 0.222 | 2 | 0 | 14/18 | no |
| fit * specificity | 0.200 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| fit * specificity | 0.250 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| fit + specificity | 0.850 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| fit + specificity | 0.900 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| fit + specificity | 0.950 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| fit + specificity | 1.000 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| min(fit, specificity) | 0.350 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |
| min(fit, specificity) | 0.400 | 1.000 | 0.167 | 0.286 | 0.889 | 0 | 2 | 2/18 | no |

## Full Sweep

Tier A-H F1 is structurally `0.000` in this offline sweep because the R1.5 surfaced set contains no true-positive candidates for the non-decoy human personas; the column is retained only as an audit signal.

| rule | threshold | Tier A precision | Tier A recall | Tier A F1 | Tier A-H F1 | overall abstain rate | full-decoy FP | killed Tier A TP | kept |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fit * specificity | 0.200 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| fit * specificity | 0.250 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| fit + specificity | 0.850 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| fit + specificity | 0.900 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| fit + specificity | 0.950 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| fit + specificity | 1.000 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| min(fit, specificity) | 0.350 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| min(fit, specificity) | 0.400 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| min(fit, specificity) | 0.450 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| min(fit, specificity) | 0.500 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| specificity | 0.350 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| specificity | 0.400 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| specificity | 0.450 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| specificity | 0.500 | 1.000 | 0.167 | 0.286 | 0.000 | 0.889 | 0 | 2 | 2 |
| fit | 0.550 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.600 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.650 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.700 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.750 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.800 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.850 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.900 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.950 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 1.000 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit * specificity | 0.300 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit * specificity | 0.350 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit * specificity | 0.400 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit * specificity | 0.450 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit * specificity | 0.500 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.550 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.600 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.650 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.700 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.750 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.800 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.850 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.900 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 0.950 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| min(fit, specificity) | 1.000 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.550 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.600 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.650 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.700 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.750 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.800 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.850 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.900 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 0.950 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| specificity | 1.000 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 0 | 4 | 0 |
| fit | 0.450 | 0.364 | 0.333 | 0.348 | 0.000 | 0.278 | 2 | 0 | 13 |
| fit | 0.500 | 0.364 | 0.333 | 0.348 | 0.000 | 0.278 | 2 | 0 | 13 |
| fit * specificity | 0.150 | 0.364 | 0.333 | 0.348 | 0.000 | 0.278 | 2 | 0 | 13 |
| fit + specificity | 0.750 | 0.364 | 0.333 | 0.348 | 0.000 | 0.278 | 2 | 0 | 13 |
| fit + specificity | 0.800 | 0.364 | 0.333 | 0.348 | 0.000 | 0.278 | 2 | 0 | 13 |
| fit | 0.350 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit | 0.400 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit * specificity | 0.100 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit + specificity | 0.550 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit + specificity | 0.600 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit + specificity | 0.650 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit + specificity | 0.700 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| min(fit, specificity) | 0.250 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| min(fit, specificity) | 0.300 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| specificity | 0.250 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| specificity | 0.300 | 0.333 | 0.333 | 0.333 | 0.000 | 0.222 | 2 | 0 | 14 |
| fit * specificity | 0.050 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| fit + specificity | 0.350 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| fit + specificity | 0.400 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| fit + specificity | 0.450 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| fit + specificity | 0.500 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| min(fit, specificity) | 0.050 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| min(fit, specificity) | 0.100 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| min(fit, specificity) | 0.150 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| min(fit, specificity) | 0.200 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| specificity | 0.050 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| specificity | 0.100 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| specificity | 0.150 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| specificity | 0.200 | 0.308 | 0.333 | 0.320 | 0.000 | 0.056 | 4 | 0 | 17 |
| fit | 0.000 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit | 0.050 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit | 0.100 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit | 0.150 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit | 0.200 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit | 0.250 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit | 0.300 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit * specificity | 0.000 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.000 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.050 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.100 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.150 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.200 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.250 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| fit + specificity | 0.300 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| min(fit, specificity) | 0.000 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
| specificity | 0.000 | 0.308 | 0.333 | 0.320 | 0.000 | 0.000 | 5 | 0 | 18 |
