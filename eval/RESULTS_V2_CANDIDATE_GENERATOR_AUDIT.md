# Pattern Engine V2 Candidate Generator Audit

Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` prevented + surfaced candidates.

This is an offline audit of the checked-in R1.5 candidate table. It does not call an LLM and must not be treated as held-out proof.

## Decision Summary

- Candidate source: R1.5 label-biased candidate table, before and after the abstain gate.
- Tier A generator recall ceiling: `4/12 = 0.333`; 8 true labels are absent before thresholding starts.
- Tier A personas with zero generated true-positive candidates: `a-03`, `a-06`.
- Tier A-H non-decoy human generator recall ceiling: `0/14 = 0.000`; no genuine human true positives are present in this candidate table.
- Full-decoy false positives: generator produced `13`; the R1.5 gate prevented 8 and still surfaced 5.
- B2 comparison caveat: checked-in reports provide aggregate B2 metrics, not per-label B2 predictions, so this audit does not claim which missing labels B2 recovered.

Interpretation: the next improvement should target candidate generation or an earlier abstain signal. The current gate can reduce decoy exposure, but it cannot recover the 8 Tier A true labels that never entered the candidate table.

## Slice Summary

| slice | personas | true labels | generated candidates | surfaced candidates | generated TP | surfaced TP | missing true labels | recall ceiling | generated FP | surfaced FP |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Tier A | 6 | 12 | 13 | 13 | 4 | 4 | 8 | 0.333 | 9 | 9 |
| Tier A-H non-decoy humans | 3 | 14 | 2 | 0 | 0 | 0 | 14 | 0.000 | 2 | 0 |
| Full decoys | 2 | 0 | 13 | 5 | 0 | 0 | 0 | 0.000 | 13 | 5 |

## Per-Persona Ceiling

| persona | true labels | generated | surfaced | generated TP | surfaced TP | missing true labels | recall ceiling | generated FP | surfaced FP |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| a-01 | 2 | 4 | 4 | 1 | 1 | 1 | 0.500 | 3 | 3 |
| a-02 | 2 | 2 | 2 | 1 | 1 | 1 | 0.500 | 1 | 1 |
| a-03 | 2 | 1 | 1 | 0 | 0 | 2 | 0.000 | 1 | 1 |
| a-04 | 1 | 2 | 2 | 1 | 1 | 0 | 1.000 | 1 | 1 |
| a-05 | 2 | 2 | 2 | 1 | 1 | 1 | 0.500 | 1 | 1 |
| a-06 | 3 | 2 | 2 | 0 | 0 | 3 | 0.000 | 2 | 2 |
| ah-02 | 6 | 0 | 0 | 0 | 0 | 6 | 0.000 | 0 | 0 |
| ah-03 | 5 | 0 | 0 | 0 | 0 | 5 | 0.000 | 0 | 0 |
| ah-04 | 3 | 2 | 0 | 0 | 0 | 3 | 0.000 | 2 | 0 |
| ah-05 | 0 | 1 | 0 | 0 | 0 | 0 | 0.000 | 1 | 0 |
| ah-06 | 0 | 12 | 5 | 0 | 0 | 0 | 0.000 | 12 | 5 |

## Surfaced True Positives

| persona | candidate |
|---|---|
| a-01 | self_criticism / self |
| a-02 | conflict_aversion / intimate |
| a-04 | rumination / self |
| a-05 | perfectionism / work |

## Missing True Labels

| persona | missing label |
|---|---|
| a-01 | worth_through_achievement / work |
| a-02 | people_pleasing / family |
| a-03 | emotional_suppression / self |
| a-03 | family_pressure / family |
| a-05 | avoidance / self |
| a-06 | boundary_difficulty / intimate |
| a-06 | comparison_loop / social |
| a-06 | over_responsibility / family |
| ah-02 | avoidance / intimate |
| ah-02 | comparison_loop / self |
| ah-02 | family_pressure / family |
| ah-02 | perfectionism / work |
| ah-02 | self_criticism / self |
| ah-02 | worth_through_achievement / work |
| ah-03 | conflict_aversion / intimate |
| ah-03 | family_pressure / family |
| ah-03 | over_responsibility / family |
| ah-03 | people_pleasing / social |
| ah-03 | self_criticism / self |
| ah-04 | comparison_loop / self |
| ah-04 | self_criticism / self |
| ah-04 | worth_through_achievement / work |

## Full-Decoy False Positives

| disposition | persona | candidate |
|---|---|---|
| prevented | ah-05 | self_criticism / self |
| prevented | ah-06 | avoidance / self |
| surfaced | ah-06 | boundary_difficulty / family |
| surfaced | ah-06 | comparison_loop / self |
| prevented | ah-06 | conflict_aversion / intimate |
| prevented | ah-06 | emotional_suppression / self |
| surfaced | ah-06 | family_pressure / family |
| prevented | ah-06 | over_responsibility / family |
| prevented | ah-06 | people_pleasing / social |
| surfaced | ah-06 | perfectionism / work |
| prevented | ah-06 | rumination / self |
| prevented | ah-06 | self_criticism / self |
| surfaced | ah-06 | worth_through_achievement / work |

## Tier A False-Positive Pressure

| candidate | generated count | surfaced count |
|---|---:|---:|
| comparison_loop / self | 5 | 5 |
| rumination / self | 2 | 2 |
| boundary_difficulty / family | 1 | 1 |
| perfectionism / work | 1 | 1 |
