# Offline Rule Simulation

Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md`

This report simulates pre-declared numeric gates over the R1.5 surfaced candidates. It does not call an LLM and must not be treated as held-out validation.

## Candidate Summary

- surfaced candidates: 18
- Tier A true positives before rule: 4
- full-decoy false positives before rule: 5

## Pareto Candidates

Filtered to rules that keep all Tier A true positives.

| rule | Tier A TP kept | Tier A killed TP | Tier A labels kept | full-decoy FP kept | total kept |
|---|---:|---:|---:|---:|---:|
| fit * specificity >= 0.10 | 4/4 | 0 | 12 | 2 | 14 |
| fit + specificity >= 0.60 | 4/4 | 0 | 12 | 2 | 14 |
| fit + specificity >= 0.70 | 4/4 | 0 | 12 | 2 | 14 |
| fit >= 0.40 | 4/4 | 0 | 12 | 2 | 14 |
| specificity >= 0.30 | 4/4 | 0 | 12 | 2 | 14 |
| fit * specificity >= 0.15 | 4/4 | 0 | 11 | 2 | 13 |
| fit + specificity >= 0.80 | 4/4 | 0 | 11 | 2 | 13 |
| fit >= 0.50 | 4/4 | 0 | 11 | 2 | 13 |
| fit * specificity >= 0.05 | 4/4 | 0 | 13 | 4 | 17 |
| fit + specificity >= 0.40 | 4/4 | 0 | 13 | 4 | 17 |
| fit + specificity >= 0.50 | 4/4 | 0 | 13 | 4 | 17 |
| specificity >= 0.10 | 4/4 | 0 | 13 | 4 | 17 |
| specificity >= 0.20 | 4/4 | 0 | 13 | 4 | 17 |
| fit * specificity >= 0.00 | 4/4 | 0 | 13 | 5 | 18 |
| fit + specificity >= 0.00 | 4/4 | 0 | 13 | 5 | 18 |
| fit + specificity >= 0.10 | 4/4 | 0 | 13 | 5 | 18 |
| fit + specificity >= 0.20 | 4/4 | 0 | 13 | 5 | 18 |
| fit + specificity >= 0.30 | 4/4 | 0 | 13 | 5 | 18 |
| fit >= 0.00 | 4/4 | 0 | 13 | 5 | 18 |
| fit >= 0.10 | 4/4 | 0 | 13 | 5 | 18 |
| fit >= 0.20 | 4/4 | 0 | 13 | 5 | 18 |
| fit >= 0.30 | 4/4 | 0 | 13 | 5 | 18 |
| specificity >= 0.00 | 4/4 | 0 | 13 | 5 | 18 |
