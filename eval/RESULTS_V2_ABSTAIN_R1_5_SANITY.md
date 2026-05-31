# Pattern Engine V2 Abstain Validation R1.5 Sanity

Generated: 2026-05-31T08:37:24.542158Z

## Purpose

This eval-only R1.5 sanity run intentionally biases the gate toward LABEL to prove the gate can enter the LABEL branch and to expose the upper bound of false positives.

Primary safety target: `ah-05 + ah-06 surfaced false positives <= 2` (stretch: `0`). Anti-cheat target: recover non-zero Tier A recall.

## Summary Metrics

### Tier A
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.389 | 0.250 | 0.289 | 1.000 | 0.000 | 28589 | $0.0057 | 186.728s |
| V1-no-verify | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 12513 | $0.0003 | 30.252s |
| V2-abstain-r1.5-sanity | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 31617 | $0.0036 | 53.837s |

### Tier A-H
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.000 | 0.000 | 0.000 | 2.800 | 0.000 | 71740 | $0.0122 | 566.226s |
| V1-no-verify | 0.000 | 0.000 | 0.000 | 3.000 | 0.000 | 28344 | $0.0006 | 367.190s |
| V2-abstain-r1.5-sanity | 0.000 | 0.000 | 0.000 | 1.000 | 0.517 | 65712 | $0.0067 | 58.328s |

## Full-Decoy Safety

| runner | ah-05 surfaced | ah-06 surfaced | total | target met? |
|---|---:|---:|---:|---|
| V1-full | 0 | 11 | 11 | no |
| V1-no-verify | 1 | 12 | 13 | no |
| V2-abstain-r1.5-sanity | 0 | 5 | 5 | no |

## Abstain Reason Codes

| reason | count |
|---|---:|
| LABEL | 18 |
| INSUFFICIENT_POSITIVE_FIT | 0 |
| LOW_SPECIFICITY | 1 |
| DECOY_MATCH | 7 |
| MODEL_UNCERTAIN | 2 |
| CHAIN_TOO_WEAK | 0 |
| SYSTEM_ERROR | 0 |

## Per-Persona Detail

| runner | persona | true | before gate | surfaced | abstained | before true hits | after true hits | killed true hits | precision | recall | F1 | tokens | cost | wall |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | a-01 | 2 | 3 | 3 | 0 | 1 | 1 | 0 | 0.333 | 0.500 | 0.400 | 7293 | $0.0018 | 49.682s |
| V1-no-verify | a-01 | 2 | 4 | 4 | 0 | 1 | 1 | 0 | 0.250 | 0.500 | 0.333 | 2174 | $0.0000 | 6.418s |
| V2-abstain-r1.5-sanity | a-01 | 2 | 4 | 4 | 0 | 1 | 1 | 0 | 0.250 | 0.500 | 0.333 | 8126 | $0.0011 | 15.176s |
| V1-full | a-02 | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 4921 | $0.0009 | 21.070s |
| V1-no-verify | a-02 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 2497 | $0.0000 | 4.366s |
| V2-abstain-r1.5-sanity | a-02 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 5396 | $0.0006 | 6.250s |
| V1-full | a-03 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3157 | $0.0004 | 11.398s |
| V1-no-verify | a-03 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 2054 | $0.0000 | 7.448s |
| V2-abstain-r1.5-sanity | a-03 | 2 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3479 | $0.0003 | 6.350s |
| V1-full | a-04 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4376 | $0.0008 | 25.844s |
| V1-no-verify | a-04 | 1 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 1.000 | 0.667 | 1972 | $0.0000 | 4.090s |
| V2-abstain-r1.5-sanity | a-04 | 1 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 1.000 | 0.667 | 4945 | $0.0006 | 8.797s |
| V1-full | a-05 | 2 | 1 | 1 | 0 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 4427 | $0.0009 | 53.392s |
| V1-no-verify | a-05 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 1832 | $0.0000 | 4.123s |
| V2-abstain-r1.5-sanity | a-05 | 2 | 2 | 2 | 0 | 1 | 1 | 0 | 0.500 | 0.500 | 0.500 | 4766 | $0.0005 | 9.181s |
| V1-full | a-06 | 3 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4415 | $0.0008 | 25.340s |
| V1-no-verify | a-06 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1984 | $0.0000 | 3.805s |
| V2-abstain-r1.5-sanity | a-06 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4905 | $0.0006 | 8.081s |
| V1-full | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3500 | $0.0005 | 16.114s |
| V1-no-verify | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 2.429s |
| V2-abstain-r1.5-sanity | ah-02 | 6 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 1.658s |
| V1-full | ah-03 | 5 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 12055 | $0.0020 | 38.310s |
| V1-no-verify | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 7.228s |
| V2-abstain-r1.5-sanity | ah-03 | 5 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 3.826s |
| V1-full | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11495 | $0.0014 | 25.921s |
| V1-no-verify | ah-04 | 3 | 2 | 2 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7029 | $0.0001 | 3.180s |
| V2-abstain-r1.5-sanity | ah-04 | 3 | 2 | 0 | 2 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 11822 | $0.0009 | 7.716s |
| V1-full | ah-05 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8411 | $0.0006 | 22.336s |
| V1-no-verify | ah-05 | 0 | 1 | 1 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 6485 | $0.0001 | 2.362s |
| V2-abstain-r1.5-sanity | ah-05 | 0 | 1 | 0 | 1 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8778 | $0.0005 | 4.194s |
| V1-full | ah-06 | 0 | 11 | 11 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 36279 | $0.0078 | 463.542s |
| V1-no-verify | ah-06 | 0 | 12 | 12 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 7162 | $0.0001 | 351.990s |
| V2-abstain-r1.5-sanity | ah-06 | 0 | 12 | 5 | 7 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 37444 | $0.0051 | 40.933s |

## Prevented Candidates

| persona | candidate | reason | fit | specificity | rationale |
|---|---|---|---:|---:|---|
| ah-04 | avoidance / self | DECOY_MATCH | 0.000 | 0.000 | The evidence excerpts reflect feelings of inadequacy and comparison, which align more closely with the comparison_loop pattern rather than avoidance. The known hard-negative guidance indicates that the avoidance is topic-specific and bounded, which does not fit the broader avoidance coping style. |
| ah-04 | rumination / self | DECOY_MATCH | 0.000 | 0.000 | The evidence excerpts reflect structural analysis of class mobility rather than closed-loop rumination, aligning with the known hard-negative guidance that distinguishes these patterns. |
| ah-05 | self_criticism / self | DECOY_MATCH | 0.000 | 0.000 | The candidate exhibits a clear rejection of self-blame and emphasizes boundaries, which aligns with the hard-negative guidance indicating that they do not engage in self-criticism. |
| ah-06 | avoidance / self | DECOY_MATCH | 0.000 | 0.000 | The evidence suggests a pattern of control and manipulation rather than avoidance, aligning with known hard-negative guidance. |
| ah-06 | conflict_aversion / intimate | DECOY_MATCH | 0.000 | 0.000 | The evidence suggests strategic non-confrontation rather than conflict aversion, as the candidate actively engineers conflicts for others while avoiding direct confrontation. |
| ah-06 | emotional_suppression / self | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence suggests a complex persona that may not fit the emotional suppression pattern, as it indicates a lack of access to genuine emotions rather than suppression of them. |
| ah-06 | over_responsibility / family | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided does not clearly demonstrate the habit of over-responsibility for others' feelings or outcomes, as it focuses more on self-perception and avoidance of vulnerability. |
| ah-06 | people_pleasing / social | DECOY_MATCH | 0.000 | 0.000 | The candidate exhibits behaviors that align with an instrumental persona rather than genuine people-pleasing, as indicated by the known hard-negative guidance. |
| ah-06 | rumination / self | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence excerpts reflect a complex interplay of self-perception and avoidance of vulnerability, which may not align strictly with the rumination pattern. The known hard-negative guidance suggests that the candidate's self-reflection serves more as self-congratulation rather than distress, leading to uncertainty in labeling. |
| ah-06 | self_criticism / self | DECOY_MATCH | 0.000 | 0.000 | The evidence excerpts demonstrate a performance of self-criticism followed by self-exoneration, aligning with the known hard-negative guidance that indicates these are not genuine instances of self-criticism. |

## Surfaced Candidates

| persona | candidate | true positive? | full-decoy FP? | fit | specificity | rationale |
|---|---|---|---|---:|---:|---|
| a-01 | boundary_difficulty / family | no | no | 0.500 | 0.300 | The evidence suggests feelings of guilt and anxiety about performance and expectations, but does not clearly indicate recurring difficulty saying no or protecting personal time, which are central to the boundary_difficulty pattern. |
| a-01 | perfectionism / work | no | no | 0.500 | 0.300 | The evidence suggests feelings of inadequacy and anxiety related to performance, but does not consistently demonstrate the strong distress or avoidance behaviors required for a clear fit with perfectionism. |
| a-01 | rumination / self | no | no | 0.500 | 0.300 | The candidate exhibits some evidence of rumination, particularly in the reflections on colleagues' achievements and self-worth, but the evidence is not consistently strong enough to fully align with the pattern's definition. |
| a-01 | self_criticism / self | yes | no | 0.500 | 0.300 | The evidence shows elements of self-criticism, such as feeling inadequate and questioning one's worth, but does not consistently demonstrate the harsh self-verdicts or comparisons to others required for a strong fit. |
| a-02 | comparison_loop / self | no | no | 0.500 | 0.300 | The evidence suggests some emotional suppression and avoidance in interactions, but does not strongly indicate a consistent pattern of comparison or rumination as defined. |
| a-02 | conflict_aversion / intimate | yes | no | 0.500 | 0.500 | The evidence suggests a tendency to avoid confrontation and express true feelings indirectly, aligning with conflict aversion. However, the evidence is not consistently strong across all excerpts, leading to an insufficient positive fit. |
| a-03 | comparison_loop / self | no | no | 0.300 | 0.200 | The evidence excerpts reflect a sense of self-awareness and processing of emotions, but do not strongly indicate a pattern of comparison or persistent unease related to self-worth. The evidence is more about situational responses and adjustments rather than a cycle of comparison. |
| a-04 | comparison_loop / self | no | no | 0.400 | 0.300 | The evidence shows some elements of comparison and rumination, but lacks a consistent pattern of repeatedly measuring self-worth against others, which is central to the candidate pattern. |
| a-04 | rumination / self | yes | no | 0.500 | 0.300 | The candidate evidence includes instances of repetitive thinking about past interactions and decisions, which aligns with the rumination pattern. However, the overall context suggests a mix of reflection and processing rather than a clear pattern of distressing rumination. |
| a-05 | comparison_loop / self | no | no | 0.500 | 0.300 | The evidence excerpts suggest a tendency towards avoidance and rumination, particularly in the context of writing and feedback, but do not strongly align with the specific pattern of comparison_loop as defined. |
| a-05 | perfectionism / work | yes | no | 0.500 | 0.500 | The evidence excerpts indicate a tendency towards perfectionism, such as excessive revision and distress over perceived inadequacies, but do not fully meet the criteria for a strong positive fit. |
| a-06 | comparison_loop / self | no | no | 0.500 | 0.300 | The evidence suggests some elements of comparison and self-evaluation, particularly in excerpts 4 and 5, but lacks sufficient consistent patterns of behavior that align strongly with the defined pattern of comparison_loop. |
| a-06 | rumination / self | no | no | 0.500 | 0.300 | The evidence excerpts suggest some elements of comparison and emotional suppression, but they do not strongly align with the defined patterns of rumination. The candidate reflects on others' achievements and their own feelings, but lacks clear repetitive distressing thought patterns characteristic of rumination. |
| ah-06 | boundary_difficulty / family | no | yes | 0.300 | 0.200 | The evidence suggests a tendency to avoid vulnerability and responsibility for others' feelings, but does not clearly align with the specific evidence shapes for boundary_difficulty. |
| ah-06 | comparison_loop / self | no | yes | 0.500 | 0.300 | The evidence suggests a tendency to avoid vulnerability and maintain a facade, which aligns with the comparison loop pattern. However, the evidence does not strongly indicate a consistent cycle of measuring worth against others, as described in the pattern. |
| ah-06 | family_pressure / family | no | yes | 0.300 | 0.200 | The evidence suggests a tendency to avoid vulnerability and manage perceptions, but does not clearly indicate family pressure as defined. The excerpts reflect more on personal identity and relational dynamics rather than the specific conflict with family expectations. |
| ah-06 | perfectionism / work | no | yes | 0.300 | 0.000 | The evidence suggests a tendency to avoid vulnerability and maintain a facade of being a 'good person,' but does not clearly indicate perfectionism as defined by the evidence shapes. |
| ah-06 | worth_through_achievement / work | no | yes | 0.500 | 0.300 | The evidence excerpts reflect a tendency to tie self-worth to external perceptions and outcomes, particularly in work contexts, but do not consistently demonstrate the specific evidence shapes required for the 'worth_through_achievement' pattern. |

## Killed True Positives

| persona | candidate | reason | rationale |
|---|---|---|---|
