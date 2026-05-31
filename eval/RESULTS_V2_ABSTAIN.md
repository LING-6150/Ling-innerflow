# Pattern Engine V2 Abstain Validation

Generated: 2026-05-31T06:31:51.087113Z

## Purpose

This eval-only run tests whether an OOD / abstain gate can reduce V1's full-decoy false positives before any production code is changed.

Primary safety target from the V2 spec: `ah-05 + ah-06 surfaced false positives <= 2` (stretch: `0`).

## Summary Metrics

### Tier A
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.222 | 0.167 | 0.178 | 1.000 | 0.000 | 28534 | $0.0057 | 143.196s |
| V1-no-verify | 0.292 | 0.417 | 0.333 | 2.167 | 0.000 | 12513 | $0.0003 | 24.283s |
| V2-abstain | 0.000 | 0.000 | 0.000 | 0.000 | 1.000 | 30736 | $0.0035 | 43.181s |

### Tier A-H
| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | 0.000 | 0.000 | 0.000 | 2.800 | 0.000 | 71730 | $0.0122 | 192.668s |
| V1-no-verify | 0.000 | 0.000 | 0.000 | 3.000 | 0.000 | 28434 | $0.0006 | 29.052s |
| V2-abstain | 0.000 | 0.000 | 0.000 | 0.000 | 0.600 | 64688 | $0.0066 | 63.882s |

## Full-Decoy Safety

| runner | ah-05 surfaced | ah-06 surfaced | total | target met? |
|---|---:|---:|---:|---|
| V1-full | 0 | 11 | 11 | no |
| V1-no-verify | 1 | 12 | 13 | no |
| V2-abstain | 0 | 0 | 0 | yes |

## Abstain Reason Codes

| reason | count |
|---|---:|
| LABEL | 0 |
| INSUFFICIENT_POSITIVE_FIT | 5 |
| LOW_SPECIFICITY | 21 |
| DECOY_MATCH | 0 |
| MODEL_UNCERTAIN | 2 |
| CHAIN_TOO_WEAK | 0 |
| SYSTEM_ERROR | 0 |

## Per-Persona Detail

| runner | persona | true | before gate | surfaced | abstained | precision | recall | F1 | tokens | cost | wall |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| V1-full | a-01 | 2 | 3 | 3 | 0 | 0.333 | 0.500 | 0.400 | 7293 | $0.0018 | 45.219s |
| V1-no-verify | a-01 | 2 | 4 | 4 | 0 | 0.250 | 0.500 | 0.333 | 2174 | $0.0000 | 5.921s |
| V2-abstain | a-01 | 2 | 4 | 0 | 4 | 0.000 | 0.000 | 0.000 | 7875 | $0.0011 | 11.911s |
| V1-full | a-02 | 2 | 1 | 1 | 0 | 1.000 | 0.500 | 0.667 | 4883 | $0.0008 | 25.269s |
| V1-no-verify | a-02 | 2 | 2 | 2 | 0 | 0.500 | 0.500 | 0.500 | 2497 | $0.0000 | 4.493s |
| V2-abstain | a-02 | 2 | 2 | 0 | 2 | 0.000 | 0.000 | 0.000 | 5278 | $0.0006 | 7.360s |
| V1-full | a-03 | 2 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3157 | $0.0004 | 11.189s |
| V1-no-verify | a-03 | 2 | 1 | 1 | 0 | 0.000 | 0.000 | 0.000 | 2054 | $0.0000 | 2.640s |
| V2-abstain | a-03 | 2 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 3411 | $0.0003 | 3.680s |
| V1-full | a-04 | 1 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 4374 | $0.0008 | 19.796s |
| V1-no-verify | a-04 | 1 | 2 | 2 | 0 | 0.500 | 1.000 | 0.667 | 1972 | $0.0000 | 3.326s |
| V2-abstain | a-04 | 1 | 2 | 0 | 2 | 0.000 | 0.000 | 0.000 | 4800 | $0.0005 | 7.618s |
| V1-full | a-05 | 2 | 1 | 1 | 0 | 0.000 | 0.000 | 0.000 | 4438 | $0.0009 | 22.055s |
| V1-no-verify | a-05 | 2 | 2 | 2 | 0 | 0.500 | 0.500 | 0.500 | 1832 | $0.0000 | 3.823s |
| V2-abstain | a-05 | 2 | 2 | 0 | 2 | 0.000 | 0.000 | 0.000 | 4625 | $0.0005 | 6.352s |
| V1-full | a-06 | 3 | 1 | 1 | 0 | 0.000 | 0.000 | 0.000 | 4389 | $0.0008 | 19.665s |
| V1-no-verify | a-06 | 3 | 2 | 2 | 0 | 0.000 | 0.000 | 0.000 | 1984 | $0.0000 | 4.077s |
| V2-abstain | a-06 | 3 | 2 | 0 | 2 | 0.000 | 0.000 | 0.000 | 4747 | $0.0005 | 6.258s |
| V1-full | ah-02 | 6 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 3498 | $0.0005 | 11.826s |
| V1-no-verify | ah-02 | 6 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 2.943s |
| V2-abstain | ah-02 | 6 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 1985 | $0.0000 | 2.528s |
| V1-full | ah-03 | 5 | 1 | 1 | 0 | 0.000 | 0.000 | 0.000 | 12048 | $0.0020 | 33.986s |
| V1-no-verify | ah-03 | 5 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 7.277s |
| V2-abstain | ah-03 | 5 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 5683 | $0.0001 | 5.225s |
| V1-full | ah-04 | 3 | 2 | 2 | 0 | 0.000 | 0.000 | 0.000 | 11445 | $0.0013 | 21.235s |
| V1-no-verify | ah-04 | 3 | 2 | 2 | 0 | 0.000 | 0.000 | 0.000 | 7029 | $0.0001 | 4.060s |
| V2-abstain | ah-04 | 3 | 2 | 0 | 2 | 0.000 | 0.000 | 0.000 | 11677 | $0.0009 | 7.625s |
| V1-full | ah-05 | 0 | 0 | 0 | 0 | 0.000 | 0.000 | 0.000 | 8411 | $0.0006 | 9.510s |
| V1-no-verify | ah-05 | 0 | 1 | 1 | 0 | 0.000 | 0.000 | 0.000 | 6485 | $0.0001 | 2.402s |
| V2-abstain | ah-05 | 0 | 1 | 0 | 1 | 0.000 | 0.000 | 0.000 | 8709 | $0.0005 | 4.345s |
| V1-full | ah-06 | 0 | 11 | 11 | 0 | 0.000 | 0.000 | 0.000 | 36328 | $0.0078 | 116.109s |
| V1-no-verify | ah-06 | 0 | 12 | 12 | 0 | 0.000 | 0.000 | 0.000 | 7252 | $0.0001 | 12.368s |
| V2-abstain | ah-06 | 0 | 12 | 0 | 12 | 0.000 | 0.000 | 0.000 | 36634 | $0.0050 | 44.157s |

## Prevented Candidates

| persona | candidate | reason | fit | specificity | rationale |
|---|---|---|---:|---:|---|
| a-01 | boundary_difficulty / family | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects feelings of anxiety, guilt, and uncertainty about personal and professional decisions, but does not specifically address the recurring difficulty in saying no or setting boundaries with family members as defined in the candidate pattern. |
| a-01 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided reflects feelings of inadequacy and anxiety related to performance, but does not consistently demonstrate the specific pattern of perfectionism as defined. The references to feeling 'not good enough' and the pressure to achieve do not clearly indicate a pattern of distress or avoidance in response to unmet standards, as required for a strong fit. |
| a-01 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects feelings of self-doubt and comparison to others, but does not clearly demonstrate the repetitive, distressing thought patterns characteristic of rumination as defined in the candidate pattern. |
| a-01 | self_criticism / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence reflects feelings of inadequacy and anxiety about performance, but it does not consistently demonstrate the specific pattern of self-criticism as defined. The statements are more about general feelings of pressure and comparison rather than explicit self-blame or harsh self-judgment. |
| a-02 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a pattern of repeatedly measuring self-worth against others, nor does it indicate a cycle of relief and unease based on comparisons. Instead, it reflects personal reflections and interactions that do not align with the specific criteria of the candidate pattern. |
| a-02 | conflict_aversion / intimate | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided includes instances of avoiding confrontation and expressing discomfort indirectly, but it lacks clear, consistent patterns of conflict aversion as defined. Some excerpts could also relate to boundary difficulty or avoidance, making the fit ambiguous. |
| a-03 | comparison_loop / self | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a pattern of repeatedly measuring self-worth against others, nor does it indicate a cycle of relief and unease based on comparisons. The excerpts focus more on personal experiences and interactions without explicit references to comparison or self-evaluation against peers. |
| a-04 | comparison_loop / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a repeated cycle of measuring self-worth against others, as described in the candidate pattern. Instead, it reflects various personal reflections and evaluations without a consistent theme of comparison leading to unease. |
| a-04 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided includes instances of reflecting on past conversations and decisions, but it does not clearly indicate a repetitive cycle of distressing thoughts without reaching new conclusions, which is essential for labeling as rumination. |
| a-05 | comparison_loop / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects a general struggle with writing and productivity, but does not specifically indicate a pattern of repeatedly measuring self-worth against others or experiencing relief and unease based on comparisons. |
| a-05 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence suggests behaviors related to perfectionism, such as excessive revision and avoidance of submission, but does not clearly indicate significant distress or interference with completion, which are critical components of the perfectionism pattern definition. |
| a-06 | comparison_loop / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a pattern of repeatedly measuring self-worth against others, as it contains more general reflections on personal situations and interactions without a consistent focus on comparison or the resulting emotional cycles. |
| a-06 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects a range of thoughts and behaviors that do not specifically indicate rumination. While there are elements of comparison and self-reflection, the evidence lacks the repetitive, distressing thought patterns characteristic of rumination. |
| ah-04 | avoidance / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects feelings of inadequacy and comparison to others, but does not clearly demonstrate the specific avoidance behaviors outlined in the candidate pattern. The excerpts focus more on personal reflections and emotional responses rather than consistent avoidance of uncomfortable situations. |
| ah-04 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects a complex internal dialogue about self-worth and societal comparisons rather than a clear instance of rumination as defined. The thoughts expressed are more analytical and contextual rather than repetitively distressing without new conclusions. |
| ah-05 | self_criticism / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided does not clearly demonstrate a consistent pattern of self-criticism as defined. Instead, it reflects a strong sense of self-awareness and boundary-setting, which contradicts the notion of self-criticism. |
| ah-06 | avoidance / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects complex interpersonal dynamics and self-perception but does not clearly demonstrate consistent avoidance behavior as defined in the candidate pattern. The excerpts suggest a mix of self-protection and manipulation rather than straightforward avoidance. |
| ah-06 | boundary_difficulty / family | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects a complex interplay of self-perception, avoidance of vulnerability, and manipulation in relationships, but does not specifically demonstrate the recurring difficulty in setting boundaries or saying no as defined in the candidate pattern. |
| ah-06 | comparison_loop / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects a complex interplay of self-perception, avoidance of vulnerability, and a desire to maintain a positive self-image, but it does not clearly demonstrate the specific pattern of repeatedly measuring worth against others as defined in the candidate pattern. |
| ah-06 | conflict_aversion / intimate | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence suggests a strategic approach to conflict rather than an aversion to it. The candidate pattern of conflict aversion implies a fear of confrontation, while the persona exhibits behaviors that manipulate situations to avoid direct conflict, indicating a more complex relationship with confrontation. |
| ah-06 | emotional_suppression / self | MODEL_UNCERTAIN | 0.000 | 0.000 | The evidence suggests a complex relationship with emotions and self-perception, but it does not clearly demonstrate the habitual suppression of emotions as defined in the candidate pattern. The persona appears to maintain a facade rather than suppressing underlying emotions, making it difficult to confidently label this as emotional suppression. |
| ah-06 | family_pressure / family | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects themes of self-perception, avoidance of vulnerability, and manipulation in relationships, but does not specifically address family expectations or pressures as defined in the candidate pattern. |
| ah-06 | over_responsibility / family | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects the candidate's self-perception and avoidance of vulnerability rather than a clear pattern of taking on responsibility for others' feelings or outcomes. |
| ah-06 | people_pleasing / social | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects a complex interplay of self-perception and interpersonal dynamics, but does not clearly demonstrate the specific behaviors or internal costs associated with people-pleasing as defined in the candidate pattern. |
| ah-06 | perfectionism / work | INSUFFICIENT_POSITIVE_FIT | 0.000 | 0.000 | The evidence provided does not clearly demonstrate the specific behaviors associated with perfectionism, such as significant distress over minor errors or excessive revision. Instead, it reflects a complex interplay of self-perception and avoidance of vulnerability, which may align more closely with boundary difficulty or worth through achievement. |
| ah-06 | rumination / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence provided reflects a complex interplay of self-perception and avoidance of vulnerability rather than a clear instance of rumination as defined. The focus is more on self-justification and maintaining a facade than on repetitively returning to distressing thoughts without resolution. |
| ah-06 | self_criticism / self | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence primarily reflects a complex self-perception and avoidance of vulnerability rather than a clear pattern of self-criticism. The individual often rationalizes their actions and maintains a positive self-image, which does not align with the definition of self-criticism. |
| ah-06 | worth_through_achievement / work | LOW_SPECIFICITY | 0.000 | 0.000 | The evidence discusses feelings of self-perception and interpersonal dynamics but does not clearly tie the individual's sense of worth to external performance outcomes or setbacks in a way that directly supports the 'worth_through_achievement' pattern. |
