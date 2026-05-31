# Pattern Engine V2.1 Contrastive Retrieval R4

Generated: 2026-05-31T13:33:08.655277Z

## Purpose

This eval-only R4 run tests contrastive retrieval / differential evidence scoring. `ah-06` is a dev-set diagnostic, not final held-out proof.

Hard criteria: Tier A F1 >= 0.300; Tier A killed true positives = 0; total LABEL count >= 12. Soft: ah-05 + ah-06 <= 2.

## Summary Metrics

### Tier A
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.222 | 0.167 | 0.178 | 1.000 | 0.000 | 28630 | $0.0057 | 172.560s |
| V1-no-verify | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 12513 | $0.0003 | 33.761s |
| V2-contrastive-r4 | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 70767 | $0.0014 | 3614.189s |

### Tier A-H
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.000 | 0.000 | 0.000 | 2.600 | 0.000 | 71763 | $0.0122 | 242.355s |
| V1-no-verify | 0.000 | 0.000 | 0.000 | 3.000 | 0.000 | 28434 | $0.0006 | 28.654s |
| V2-contrastive-r4 | 0.000 | 0.000 | 0.000 | 3.000 | 0.000 | 183069 | $0.0037 | 4053.779s |

## Full-Decoy Safety

| runner | ah-05 surfaced | ah-06 surfaced | total | target met? |
|---|---:|---:|---:|---|
| V1-full | 0 | 11 | 11 | no |
| V1-no-verify | 1 | 12 | 13 | no |
| V2-contrastive-r4 | 1 | 12 | 13 | no |

## Per-Persona Detail

| runner | persona | true | before gate | surfaced | filtered | before true hits | after true hits | killed true hits | precision | recall | F1 | tokens | cost | wall |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | a-01 | 2 | 3 | 3 | 0 | 1 | 1 | 0 | 0.333 | 0.500 | 0.400 | 7290 | $0.0018 | 50.411s |
| V1-no-verify | a-01 | 2 | 4 | 4 | 0 | 1 | 1 | 0 | 0.250 | 0.500 | 0.333 | 2174 | $0.0000 | 7.195s |
| V2-contrastive-r4 | a-01 | 2 | 4 | 4 | 0 | 1 | 1 | 0 | 0.250 | 0.500 | 0.333 | 20282 | $0.0004 | 1059.745s |
| V1-full | a-02 | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 4882 | $0.0008 | 19.711s |
| V1-no-verify | a-02 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 2497 | $0.0000 | 4.381s |
| V2-contrastive-r4 | a-02 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 11416 | $0.0002 | 530.398s |
| V1-full | a-03 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3157 | $0.0004 | 13.158s |
| V1-no-verify | a-03 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 2054 | $0.0000 | 3.718s |
| V2-contrastive-r4 | a-03 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 6382 | $0.0001 | 330.447s |
| V1-full | a-04 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4372 | $0.0008 | 27.900s |
| V1-no-verify | a-04 | 1 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 1.000 | 0.667 | 1972 | $0.0000 | 5.888s |
| V2-contrastive-r4 | a-04 | 1 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 1.000 | 0.667 | 11062 | $0.0002 | 517.515s |
| V1-full | a-05 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4474 | $0.0010 | 32.578s |
| V1-no-verify | a-05 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 1832 | $0.0000 | 8.127s |
| V2-contrastive-r4 | a-05 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 10671 | $0.0002 | 598.501s |
| V1-full | a-06 | 3 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4455 | $0.0009 | 28.799s |
| V1-no-verify | a-06 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1984 | $0.0000 | 4.450s |
| V2-contrastive-r4 | a-06 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 10954 | $0.0002 | 577.581s |
| V1-full | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3480 | $0.0005 | 16.899s |
| V1-no-verify | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 3.136s |
| V2-contrastive-r4 | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 1.656s |
| V1-full | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 12113 | $0.0020 | 46.127s |
| V1-no-verify | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 5.014s |
| V2-contrastive-r4 | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 5.687s |
| V1-full | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11471 | $0.0013 | 28.627s |
| V1-no-verify | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7029 | $0.0001 | 3.755s |
| V2-contrastive-r4 | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 26986 | $0.0005 | 552.830s |
| V1-full | ah-05 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8411 | $0.0006 | 16.951s |
| V1-no-verify | ah-05 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 6485 | $0.0001 | 3.783s |
| V2-contrastive-r4 | ah-05 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 15496 | $0.0003 | 271.684s |
| V1-full | ah-06 | 0 | 11 | 11 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 36288 | $0.0078 | 133.749s |
| V1-no-verify | ah-06 | 0 | 12 | 12 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7252 | $0.0001 | 12.963s |
| V2-contrastive-r4 | ah-06 | 0 | 12 | 12 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 132919 | $0.0027 | 3221.920s |

## Surfaced Candidates

| persona | candidate | true positive? | full-decoy FP? | supportive | max contrastive | margin | strongest confusable |
|---|---|---|---|---:|---:|---:|---|
| a-01 | boundary_difficulty / family | no | no | 0.900 | 0.412 | 0.488 | conflict_aversion |
| a-01 | perfectionism / work | no | no | 0.900 | 0.422 | 0.478 | worth_through_achievement |
| a-01 | rumination / self | no | no | 0.900 | 0.440 | 0.460 | self_criticism |
| a-01 | self_criticism / self | yes | no | 0.900 | 0.413 | 0.487 | worth_through_achievement |
| a-02 | comparison_loop / self | no | no | 0.930 | 0.422 | 0.508 | worth_through_achievement |
| a-02 | conflict_aversion / intimate | yes | no | 0.930 | 0.484 | 0.446 | people_pleasing |
| a-03 | comparison_loop / self | no | no | 0.920 | 0.383 | 0.537 | worth_through_achievement |
| a-04 | comparison_loop / self | no | no | 0.900 | 0.455 | 0.445 | rumination |
| a-04 | rumination / self | yes | no | 0.900 | 0.374 | 0.526 | emotional_suppression |
| a-05 | comparison_loop / self | no | no | 0.900 | 0.535 | 0.365 | perfectionism |
| a-05 | perfectionism / work | yes | no | 0.900 | 0.396 | 0.504 | rumination |
| a-06 | comparison_loop / self | no | no | 0.900 | 0.432 | 0.468 | worth_through_achievement |
| a-06 | rumination / self | no | no | 0.900 | 0.425 | 0.475 | self_criticism |
| ah-04 | avoidance / self | no | no | 1.000 | 0.434 | 0.566 | rumination |
| ah-04 | rumination / self | no | no | 1.000 | 0.467 | 0.533 | self_criticism |
| ah-05 | self_criticism / self | no | yes | 1.000 | 0.385 | 0.615 | rumination |
| ah-06 | avoidance / self | no | yes | 1.000 | 0.429 | 0.571 | conflict_aversion |
| ah-06 | boundary_difficulty / family | no | yes | 1.000 | 0.429 | 0.571 | conflict_aversion |
| ah-06 | comparison_loop / self | no | yes | 1.000 | 0.480 | 0.520 | self_criticism |
| ah-06 | conflict_aversion / intimate | no | yes | 1.000 | 0.406 | 0.594 | emotional_suppression |
| ah-06 | emotional_suppression / self | no | yes | 1.000 | 0.480 | 0.520 | self_criticism |
| ah-06 | family_pressure / family | no | yes | 1.000 | 0.408 | 0.592 | over_responsibility |
| ah-06 | over_responsibility / family | no | yes | 1.000 | 0.480 | 0.520 | self_criticism |
| ah-06 | people_pleasing / social | no | yes | 1.000 | 0.429 | 0.571 | conflict_aversion |
| ah-06 | perfectionism / work | no | yes | 1.000 | 0.480 | 0.520 | self_criticism |
| ah-06 | rumination / self | no | yes | 1.000 | 0.480 | 0.520 | self_criticism |
| ah-06 | self_criticism / self | no | yes | 1.000 | 0.414 | 0.586 | rumination |
| ah-06 | worth_through_achievement / work | no | yes | 1.000 | 0.480 | 0.520 | self_criticism |

## Filtered Candidates

| persona | candidate | true positive? | full-decoy FP? | supportive | max contrastive | margin | strongest confusable | reason |
|---|---|---|---|---:|---:|---:|---|---|

## Conclusion

- Tier A F1: 0.333
- LABEL count: 28
- Tier A killed true positives: 0
- Full-decoy surfaced count: 13
- `ah-06` is a dev-set metric in this report, not evidence that V2.1 has solved NPD-style over-labeling. Fresh held-out hard negatives are required for any final safety claim.
