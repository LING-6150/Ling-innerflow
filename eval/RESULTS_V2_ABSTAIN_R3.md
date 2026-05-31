# Pattern Engine V2 Abstain Validation R3 Quote Gate

Generated: 2026-05-31T09:11:03.253249Z

## Purpose

This eval-only R3 run keeps the R1.5 label-biased posture but requires every LABEL to pass quote-level evidence verification. `ah-06` is now treated as dev-set diagnostic, not final held-out proof.

Primary safety target: `ah-05 + ah-06 surfaced false positives <= 2` (stretch: `0`). Anti-cheat target: recover non-zero Tier A recall.

## Summary Metrics

### Tier A
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.167 | 0.083 | 0.111 | 1.167 | 0.000 | 28671 | $0.0058 | 173.064s |
| V1-no-verify | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 12513 | $0.0003 | 25.504s |
| V2-abstain-r3-quote | 0.167 | 0.083 | 0.111 | 0.167 | 0.917 | 32645 | $0.0038 | 60.997s |

### Tier A-H
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.000 | 0.000 | 0.000 | 2.600 | 0.000 | 71468 | $0.0121 | 245.999s |
| V1-no-verify | 0.000 | 0.000 | 0.000 | 3.000 | 0.000 | 28434 | $0.0006 | 31.598s |
| V2-abstain-r3-quote | 0.000 | 0.000 | 0.000 | 0.000 | 0.600 | 66831 | $0.0069 | 68.443s |

## Full-Decoy Safety

| runner | ah-05 surfaced | ah-06 surfaced | total | target met? |
|---|---:|---:|---:|---|
| V1-full | 0 | 11 | 11 | no |
| V1-no-verify | 1 | 12 | 13 | no |
| V2-abstain-r3-quote | 0 | 0 | 0 | yes |

## Abstain Reason Codes

| reason | count |
|---|---:|
| LABEL | 1 |
| INSUFFICIENT_POSITIVE_FIT | 17 |
| LOW_SPECIFICITY | 3 |
| DECOY_MATCH | 5 |
| MODEL_UNCERTAIN | 2 |
| CHAIN_TOO_WEAK | 0 |
| SYSTEM_ERROR | 0 |

## Per-Persona Detail

| runner | persona | true | before gate | surfaced | abstained | before true hits | after true hits | killed true hits | precision | recall | F1 | tokens | cost | wall |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | a-01 | 2 | 3 | 3 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7289 | $0.0018 | 56.190s |
| V1-no-verify | a-01 | 2 | 4 | 4 | 0 | 1 | 1 | 0 | 0.250 | 0.500 | 0.333 | 2174 | $0.0000 | 5.647s |
| V2-abstain-r3-quote | a-01 | 2 | 4 | 0 | 4 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 8432 | $0.0011 | 17.499s |
| V1-full | a-02 | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 5023 | $0.0009 | 23.578s |
| V1-no-verify | a-02 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 2497 | $0.0000 | 3.916s |
| V2-abstain-r3-quote | a-02 | 2 | 2 | 0 | 2 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 5564 | $0.0006 | 9.806s |
| V1-full | a-03 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3157 | $0.0004 | 11.585s |
| V1-no-verify | a-03 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 2054 | $0.0000 | 2.568s |
| V2-abstain-r3-quote | a-03 | 2 | 1 | 0 | 1 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3551 | $0.0003 | 5.171s |
| V1-full | a-04 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4359 | $0.0008 | 26.034s |
| V1-no-verify | a-04 | 1 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 1.000 | 0.667 | 1972 | $0.0000 | 2.989s |
| V2-abstain-r3-quote | a-04 | 1 | 2 | 0 | 2 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 5092 | $0.0006 | 8.508s |
| V1-full | a-05 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4414 | $0.0009 | 27.477s |
| V1-no-verify | a-05 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 1832 | $0.0000 | 4.150s |
| V2-abstain-r3-quote | a-05 | 2 | 2 | 1 | 1 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 4959 | $0.0006 | 10.369s |
| V1-full | a-06 | 3 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4429 | $0.0009 | 28.199s |
| V1-no-verify | a-06 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1984 | $0.0000 | 6.231s |
| V2-abstain-r3-quote | a-06 | 3 | 2 | 0 | 2 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5047 | $0.0006 | 9.641s |
| V1-full | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3476 | $0.0005 | 14.673s |
| V1-no-verify | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 3.357s |
| V2-abstain-r3-quote | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 3.472s |
| V1-full | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 12084 | $0.0020 | 43.909s |
| V1-no-verify | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 6.917s |
| V2-abstain-r3-quote | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 4.364s |
| V1-full | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11363 | $0.0013 | 23.135s |
| V1-no-verify | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7029 | $0.0001 | 5.380s |
| V2-abstain-r3-quote | ah-04 | 3 | 2 | 0 | 2 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11962 | $0.0010 | 9.679s |
| V1-full | ah-05 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8411 | $0.0006 | 13.020s |
| V1-no-verify | ah-05 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 6485 | $0.0001 | 2.556s |
| V2-abstain-r3-quote | ah-05 | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8856 | $0.0005 | 3.708s |
| V1-full | ah-06 | 0 | 11 | 11 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 36134 | $0.0077 | 151.259s |
| V1-no-verify | ah-06 | 0 | 12 | 12 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7252 | $0.0001 | 13.386s |
| V2-abstain-r3-quote | ah-06 | 0 | 12 | 0 | 12 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 38345 | $0.0052 | 47.219s |

## Prevented Candidates

| persona | candidate | reason | fit | specificity | rationale |
|---|---|---|---:|---:|---|
| a-01 | boundary_difficulty / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly match the defined evidence shapes for any of the candidate patterns. |
| a-01 | perfectionism / work | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided does not clearly match the specific evidence shapes required for labeling perfectionism, and there is insufficient positive fit. |
| a-01 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence excerpts reflect feelings of self-doubt and comparison but do not clearly fit the defined evidence shapes for rumination, and there is insufficient specificity to label it as such. |
| a-01 | self_criticism / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not sufficiently match the defined evidence shapes for self_criticism, as there are not enough clear instances of harsh self-verdicts or comparisons to others' mistakes. |
| a-02 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly align with the defined evidence shapes for the candidate pattern of comparison_loop, as they do not demonstrate a consistent cycle of measuring worth against others. |
| a-02 | conflict_aversion / intimate | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | While there are indications of conflict aversion, the evidence does not consistently align with the required evidence shapes to support a definitive label. |
| a-03 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly align with the defined evidence shapes for the candidate pattern of comparison_loop, as they do not demonstrate a consistent cycle of comparing oneself to others. |
| a-04 | comparison_loop / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence excerpts do not consistently demonstrate a pattern of repeated self-comparison, which is necessary for a clear label. |
| a-04 | rumination / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not sufficiently match the defined evidence shapes for rumination, as there are not enough clear instances of repetitive distressing thoughts without new conclusions. |
| a-05 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly match the defined evidence shapes for the candidate pattern of 'comparison_loop', as they focus more on writing process and self-critique rather than comparing oneself to others. |
| a-06 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly align with the defined evidence shapes for the candidate pattern of comparison_loop, as they do not demonstrate a consistent cycle of measuring worth against others. |
| a-06 | rumination / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly match the defined evidence shapes for rumination, avoidance, comparison loop, or emotional suppression. |
| ah-04 | avoidance / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not provide sufficient positive fit to the avoidance pattern, as they do not clearly demonstrate consistent delaying or sidestepping of uncomfortable situations. |
| ah-04 | rumination / self | DECOY_MATCH | 0.000 | 0.000 | The evidence excerpts reflect structural analysis of class mobility rather than closed-loop rumination, aligning with known hard-negative guidance. |
| ah-05 | self_criticism / self | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence does not clearly demonstrate self-criticism as defined, and the known hard-negative guidance suggests a defensive negation rather than self-criticism. |
| ah-06 | avoidance / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not provide sufficient positive fit to the avoidance pattern, as they do not clearly demonstrate consistent delaying or sidestepping uncomfortable situations. |
| ah-06 | boundary_difficulty / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly match the required evidence shapes for the candidate pattern, indicating insufficient positive fit. |
| ah-06 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not provide sufficient positive fit to the candidate pattern of 'comparison_loop', as they focus more on avoidance and emotional suppression rather than the specific cycle of comparison described. |
| ah-06 | conflict_aversion / intimate | DECOY_MATCH | 0.000 | 0.000 | The candidate's behavior aligns with strategic non-confrontation rather than conflict aversion, as indicated by the known hard-negative guidance. |
| ah-06 | emotional_suppression / self | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence provided does not clearly fit the emotional suppression pattern due to the presence of a maintained persona rather than suppressed emotions. |
| ah-06 | family_pressure / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly match the required evidence shapes for the candidate pattern of family pressure. |
| ah-06 | over_responsibility / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly demonstrate the specific patterns of over-responsibility as defined, lacking sufficient positive fit. |
| ah-06 | people_pleasing / social | DECOY_MATCH | 0.000 | 0.000 | The persona exhibits behaviors that superficially resemble people-pleasing but are actually strategic and instrumental, as indicated by the known hard-negative guidance. |
| ah-06 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not provide sufficient positive fit to any specific evidence shape defined for the candidate pattern. |
| ah-06 | rumination / self | DECOY_MATCH | 0.000 | 0.000 | The candidate's self-reflection appears to align more with self-congratulation rather than the distress characteristic of rumination, as indicated by the known hard-negative guidance. |
| ah-06 | self_criticism / self | DECOY_MATCH | 0.000 | 0.000 | The evidence excerpts reflect a performance of self-criticism that concludes with self-exoneration, aligning with known hard-negative guidance. |
| ah-06 | worth_through_achievement / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not clearly describe setbacks in terms of personal worth or link external performance outcomes to feelings of being 'okay,' which are necessary for a positive fit with the candidate pattern. |

## Surfaced Candidates

| persona | candidate | true positive? | full-decoy FP? | fit | specificity | evidence shape | quote | rationale |
|---|---|---|---|---:|---:|---|---|---|
| a-05 | perfectionism / work | yes | no | 0.000 | 0.000 | Avoids submitting or sharing work because it does not feel ready, even past deadlines. | 我说"再看看"，然后又改了两处才关掉文件。 | The candidate shows a tendency to delay submission due to feeling unprepared, as indicated by the supporting quote. |

## Killed True Positives

| persona | candidate | reason | rationale |
|---|---|---|---|
| a-01 | self_criticism / self | INSUFFICIENT_POSITIVE_FIT | The evidence provided does not sufficiently match the defined evidence shapes for self_criticism, as there are not enough clear instances of harsh self-verdicts or comparisons to others' mistakes. |
| a-02 | conflict_aversion / intimate | INSUFFICIENT_POSITIVE_FIT | While there are indications of conflict aversion, the evidence does not consistently align with the required evidence shapes to support a definitive label. |
| a-04 | rumination / self | INSUFFICIENT_POSITIVE_FIT | The evidence provided does not sufficiently match the defined evidence shapes for rumination, as there are not enough clear instances of repetitive distressing thoughts without new conclusions. |
