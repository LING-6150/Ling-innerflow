# Pattern Engine V2 Abstain Validation R2

Generated: 2026-05-31T07:01:22.870971Z

## Purpose

This eval-only R2 run tests whether a less conservative OOD / abstain gate can preserve true positives while still reducing full-decoy false positives.

Primary safety target: `ah-05 + ah-06 surfaced false positives <= 2` (stretch: `0`). Anti-cheat target: recover non-zero Tier A recall.

## Summary Metrics

### Tier A
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.167 | 0.083 | 0.111 | 1.000 | 0.000 | 28564 | $0.0057 | 138.555s |
| V1-no-verify | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 12513 | $0.0003 | 26.339s |
| V2-abstain-r2 | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 31819 | $0.0036 | 67.890s |

### Tier A-H
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.000 | 0.000 | 0.000 | 2.800 | 0.000 | 71394 | $0.0120 | 187.915s |
| V1-no-verify | 0.000 | 0.000 | 0.000 | 3.000 | 0.000 | 28434 | $0.0006 | 24.131s |
| V2-abstain-r2 | 0.000 | 0.000 | 0.000 | 0.000 | 0.600 | 65885 | $0.0067 | 69.472s |

## Full-Decoy Safety

| runner | ah-05 surfaced | ah-06 surfaced | total | target met? |
|---|---:|---:|---:|---|
| V1-full | 0 | 11 | 11 | no |
| V1-no-verify | 1 | 12 | 13 | no |
| V2-abstain-r2 | 0 | 0 | 0 | yes |

## Abstain Reason Codes

| reason | count |
|---|---:|
| LABEL | 0 |
| INSUFFICIENT_POSITIVE_FIT | 23 |
| LOW_SPECIFICITY | 4 |
| DECOY_MATCH | 0 |
| MODEL_UNCERTAIN | 1 |
| CHAIN_TOO_WEAK | 0 |
| SYSTEM_ERROR | 0 |

## Per-Persona Detail

| runner | persona | true | before gate | surfaced | abstained | before true hits | after true hits | killed true hits | precision | recall | F1 | tokens | cost | wall |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | a-01 | 2 | 3 | 3 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7312 | $0.0018 | 46.567s |
| V1-no-verify | a-01 | 2 | 4 | 4 | 0 | 1 | 1 | 0 | 0.250 | 0.500 | 0.333 | 2174 | $0.0000 | 6.637s |
| V2-abstain-r2 | a-01 | 2 | 4 | 0 | 4 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 8175 | $0.0011 | 24.163s |
| V1-full | a-02 | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 4907 | $0.0009 | 17.697s |
| V1-no-verify | a-02 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 2497 | $0.0000 | 4.345s |
| V2-abstain-r2 | a-02 | 2 | 2 | 0 | 2 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 5447 | $0.0006 | 8.075s |
| V1-full | a-03 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3157 | $0.0004 | 9.206s |
| V1-no-verify | a-03 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 2054 | $0.0000 | 3.577s |
| V2-abstain-r2 | a-03 | 2 | 1 | 0 | 1 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3496 | $0.0003 | 4.898s |
| V1-full | a-04 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4340 | $0.0008 | 21.399s |
| V1-no-verify | a-04 | 1 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 1.000 | 0.667 | 1972 | $0.0000 | 4.359s |
| V2-abstain-r2 | a-04 | 1 | 2 | 0 | 2 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 4970 | $0.0006 | 12.407s |
| V1-full | a-05 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4421 | $0.0009 | 19.738s |
| V1-no-verify | a-05 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 1832 | $0.0000 | 3.793s |
| V2-abstain-r2 | a-05 | 2 | 2 | 0 | 2 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 4802 | $0.0006 | 7.737s |
| V1-full | a-06 | 3 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4427 | $0.0009 | 23.945s |
| V1-no-verify | a-06 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1984 | $0.0000 | 3.625s |
| V2-abstain-r2 | a-06 | 3 | 2 | 0 | 2 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4929 | $0.0006 | 10.608s |
| V1-full | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3462 | $0.0005 | 12.499s |
| V1-no-verify | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 2.253s |
| V2-abstain-r2 | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 1.888s |
| V1-full | ah-03 | 5 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11978 | $0.0019 | 33.781s |
| V1-no-verify | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 4.779s |
| V2-abstain-r2 | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 3.631s |
| V1-full | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11408 | $0.0013 | 20.479s |
| V1-no-verify | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7029 | $0.0001 | 3.058s |
| V2-abstain-r2 | ah-04 | 3 | 2 | 0 | 2 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11838 | $0.0009 | 7.884s |
| V1-full | ah-05 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8411 | $0.0006 | 9.984s |
| V1-no-verify | ah-05 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 6485 | $0.0001 | 2.406s |
| V2-abstain-r2 | ah-05 | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8800 | $0.0005 | 4.811s |
| V1-full | ah-06 | 0 | 11 | 11 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 36135 | $0.0077 | 111.170s |
| V1-no-verify | ah-06 | 0 | 12 | 12 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7252 | $0.0001 | 11.633s |
| V2-abstain-r2 | ah-06 | 0 | 12 | 0 | 12 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 37579 | $0.0051 | 51.256s |

## Prevented Candidates

| persona | candidate | reason | fit | specificity | rationale |
|---|---|---|---:|---:|---|
| a-01 | boundary_difficulty / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects feelings of anxiety and self-doubt without clear indications of boundary difficulties or family pressure, lacking the necessary fit to the candidate pattern. |
| a-01 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects feelings of inadequacy and anxiety without demonstrating a consistent pattern of perfectionism as defined by the evidence shapes. |
| a-01 | rumination / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects feelings of inadequacy and anxiety rather than clear patterns of rumination, lacking the necessary recurrence and specificity to support the label. |
| a-01 | self_criticism / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects feelings of inadequacy and anxiety without clear patterns of self-criticism as defined in the evidence shapes. |
| a-02 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a pattern of comparison loop behavior, as it primarily reflects emotional responses and interpersonal dynamics without sufficient recurrence of self-evaluation against others. |
| a-02 | conflict_aversion / intimate | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not consistently demonstrate the specific behaviors outlined in the candidate evidence_shapes for conflict aversion, as it includes elements of personal reflection and situational context without clear patterns of avoidance or conflict. |
| a-03 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not demonstrate a consistent pattern of comparison or emotional evaluation against others, which is necessary for the candidate pattern. Instead, it reflects a range of neutral observations and interactions without clear evidence of the described behavior. |
| a-04 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts do not consistently demonstrate a pattern of comparison leading to persistent unease; they reflect isolated instances of reflection and feedback without a recurring cycle of distress. |
| a-04 | rumination / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts primarily reflect reflections on feedback and personal experiences without demonstrating a clear pattern of rumination as defined by the evidence shapes. |
| a-05 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence excerpts primarily reflect feelings of inefficiency and self-doubt in writing progress, lacking clear patterns of comparison or emotional suppression that would fit the candidate pattern. |
| a-05 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided reflects feelings of inadequacy and procrastination but lacks clear instances of distress or avoidance that directly match the defined evidence shapes for perfectionism. |
| a-06 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a consistent pattern of comparison or emotional evaluation as described in the candidate pattern. Instead, it reflects situational responses and personal reflections without a strong link to the defined evidence shapes. |
| a-06 | rumination / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a pattern of rumination, as it primarily reflects situational thoughts and reactions without repetitive distressing loops. |
| ah-04 | avoidance / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects feelings of inadequacy and personal reflection rather than consistent avoidance behavior as defined in the candidate pattern. |
| ah-04 | rumination / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects personal reflections and feelings rather than the repetitive, distressing thought patterns characteristic of rumination. There is insufficient direct alignment with the evidence shapes. |
| ah-05 | self_criticism / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects self-awareness and boundary-setting rather than self-criticism, lacking the harsh self-verdicts or comparisons to others that characterize the self_criticism pattern. |
| ah-06 | avoidance / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided reflects complex emotional dynamics and self-perception but lacks clear instances of avoidance behavior as defined by the evidence shapes. |
| ah-06 | boundary_difficulty / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects personal reflections and emotional states without clear instances of boundary difficulty, guilt, or obligation related to family expectations. |
| ah-06 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects introspection and emotional complexity without clear patterns of comparison or evaluation against others, which are necessary for the candidate label. |
| ah-06 | conflict_aversion / intimate | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence provided reflects complex interpersonal dynamics and self-perception but does not clearly align with the specific evidence shapes for conflict aversion. The candidate's behavior appears more strategic than avoidant, suggesting uncertainty in labeling. |
| ah-06 | emotional_suppression / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects a persona and self-perception rather than clear emotional suppression as defined by the evidence shapes. The candidate's reflections indicate a lack of access to genuine emotions rather than merely suppressing them. |
| ah-06 | family_pressure / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects personal reflections and emotional states without clear alignment to the defined evidence shapes for family pressure. |
| ah-06 | over_responsibility / family | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects personal reflections and emotional states rather than clear patterns of over-responsibility as defined in the candidate evidence shapes. |
| ah-06 | people_pleasing / social | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects a complex self-perception and interpersonal dynamics without clear indicators of people-pleasing behavior as defined by the evidence shapes. |
| ah-06 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects feelings of self-perception and avoidance of vulnerability rather than clear indicators of perfectionism, as defined by the evidence shapes. |
| ah-06 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects self-reflection and interpersonal dynamics without clear indicators of rumination as defined by the evidence shapes. |
| ah-06 | self_criticism / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects complex interpersonal dynamics and self-perception without clear instances of self-criticism as defined by the evidence shapes. |
| ah-06 | worth_through_achievement / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence primarily reflects personal reflections and feelings rather than clear descriptions of setbacks tied to self-worth based on external performance outcomes. |

## Killed True Positives

| persona | candidate | reason | rationale |
|---|---|---|---|
| a-01 | self_criticism / self | INSUFFICIENT_POSITIVE_FIT | The evidence primarily reflects feelings of inadequacy and anxiety without clear patterns of self-criticism as defined in the evidence shapes. |
| a-02 | conflict_aversion / intimate | INSUFFICIENT_POSITIVE_FIT | The evidence provided does not consistently demonstrate the specific behaviors outlined in the candidate evidence_shapes for conflict aversion, as it includes elements of personal reflection and situational context without clear patterns of avoidance or conflict. |
| a-04 | rumination / self | INSUFFICIENT_POSITIVE_FIT | The evidence excerpts primarily reflect reflections on feedback and personal experiences without demonstrating a clear pattern of rumination as defined by the evidence shapes. |
| a-05 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | The evidence provided reflects feelings of inadequacy and procrastination but lacks clear instances of distress or avoidance that directly match the defined evidence shapes for perfectionism. |
