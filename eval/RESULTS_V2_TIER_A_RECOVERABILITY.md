# Pattern Engine V2 Tier A Recoverability Audit

Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` prevented + surfaced candidates, Tier A only.

This is an offline diagnostic over the dev/calibration slice. It does not inspect Tier A-H sealed labels and must not be treated as held-out proof.

## Decision Summary

- Missing Tier A true labels: `8`.
- Recoverable by pattern-key relabel only: `1`.
- Requires new candidate generation: `7`.
- Domain-agnostic matching is diagnostic only. The headline metric remains strict `(pattern_key, domain)` recall.

Interpretation: the cheap relabel/domain-assignment fix can recover at most one missing Tier A label on this candidate table. The remaining seven misses require the generator to propose additional evidence-grounded candidates before the abstain gate or threshold sweep can help.

## Recoverability Split

| persona | missing true label | generated labels for persona | status | diagnostic note |
|---|---|---|---|---|
| a-01 | worth_through_achievement / work | boundary_difficulty / family<br>perfectionism / work<br>rumination / self<br>self_criticism / self | needs_new_generation | pattern key absent from generated candidates |
| a-02 | people_pleasing / family | comparison_loop / self<br>conflict_aversion / intimate | needs_new_generation | pattern key absent from generated candidates |
| a-03 | emotional_suppression / self | comparison_loop / self | needs_new_generation | pattern key absent from generated candidates |
| a-03 | family_pressure / family | comparison_loop / self | needs_new_generation | pattern key absent from generated candidates |
| a-05 | avoidance / self | comparison_loop / self<br>perfectionism / work | needs_new_generation | pattern key absent from generated candidates |
| a-06 | boundary_difficulty / intimate | comparison_loop / self<br>rumination / self | needs_new_generation | pattern key absent from generated candidates |
| a-06 | comparison_loop / social | comparison_loop / self<br>rumination / self | recoverable_by_relabel | same pattern key generated under a different domain |
| a-06 | over_responsibility / family | comparison_loop / self<br>rumination / self | needs_new_generation | pattern key absent from generated candidates |

## Design Implication

- Treat `recoverable_by_relabel` as a bounded domain-assignment experiment, not a metric change.
- Treat `needs_new_generation` as the main candidate-generator redesign budget.
- Re-measure full-decoy generated and surfaced false positives after any broader generation change; PR #50 showed `13` generated and `5` surfaced full-decoy FPs, above the `<=2` recovery target.
