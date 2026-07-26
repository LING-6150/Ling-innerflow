# InnerFlow Memory Reliability — M0 Results

> Protocol: v1.2 frozen. Counts are the scenario unit; no percentages or LLM judge are used.

## Run identity

- Model: `gemini-2.5-flash`
- Complete replicates: `5`
- Sealed raw artifact SHA-256: `973c2db48c8102449bdfa8cef8d54b6cc083da46d17307cfed9051c4c81f4ae4`
- Holdout disclosure: aggregate only; item-level holdout traces remain sealed.

## Per-replicate answer errors

| Policy | Split | R1 | R2 | R3 | R4 | R5 |
|---|---|---|---|---|---|---|
| B-summary | visible | 10/16 | 11/16 | 8/16 | 9/16 | 9/16 |
| B-summary | holdout | 5/8 | 6/8 | 5/8 | 4/8 | 5/8 |
| B-full | visible | 11/16 | 10/16 | 10/16 | 11/16 | 11/16 |
| B-full | holdout | 6/8 | 6/8 | 2/8 | 4/8 | 4/8 |
| B-none | visible | 13/16 | 13/16 | 13/16 | 12/16 | 12/16 |
| B-none | holdout | 7/8 | 7/8 | 7/8 | 8/8 | 8/8 |

## Visible scenario-majority results

| Scenario | Category | B-summary | B-full | B-none |
|---|---|---|---|---|
| corr_support_style | correction | wrong | correct | wrong |
| corr_coping_method | correction | wrong | wrong | wrong |
| corr_trigger_source | correction | wrong | wrong | wrong |
| corr_preferred_name | correction | wrong | wrong | wrong |
| super_stressor | supersession | correct | correct | correct |
| super_coping | supersession | wrong | wrong | wrong |
| super_goal | supersession | wrong | wrong | wrong |
| ctx_feedback_review | context-exception | correct | wrong | wrong |
| ctx_detail_crisis_plan | context-exception | wrong | wrong | wrong |
| ctx_bereavement | context-exception | correct | correct | wrong |
| ctx_humor_panic | context-exception | correct | wrong | wrong |
| none_crowd_calendar | no-memory | correct | wrong | wrong |
| none_running_grammar | no-memory | wrong | correct | correct |
| none_music_password | no-memory | wrong | wrong | correct |
| del_nickname | deletion | wrong | wrong | wrong |
| del_elevator_trigger | deletion | wrong | wrong | wrong |

## Sealed-holdout aggregates

| Policy | Majority errors | Non-unanimous |
|---|---:|---:|
| B-summary | 6/8 | 5/8 |
| B-full | 5/8 | 5/8 |
| B-none | 7/8 | 1/8 |

## G0

- Decision: **INCONCLUSIVE**
- Reason code: `INCONCLUSIVE_MODEL_VARIANCE`
- Selected path: `none`

| Predicate/count | Result |
|---|---:|
| B-summary visible error floor replicates | 5/5 |
| B-summary holdout error floor replicates | 5/5 |
| B-summary majority failures | 17/24 |
| Path A majority paired cases | 4/24 |
| Path A replicate threshold passes | 4/5 |
| Path B majority paired cases | 3/10 |
| Path B replicate threshold passes | 5/5 |
| B-full unstable scenarios | 8/24 |
| B-none unstable scenarios | 4/24 |
| B-summary unstable scenarios | 13/24 |

Failed predicates:

- more than 6/24 scenarios remain unstable on every evidence path

## Format-instability diagnostic (non-gating)

The official decision above is unchanged. Inspection after sealing found that
`216/360` responses graded `invalid_json`; every one was a valid single-choice
JSON object wrapped in a complete `json` Markdown fence. Fence use varied across
replicates, so the strict output contract dominated the variance guard:
B-summary was unstable on `13/24` scenarios and B-full on `8/24`.

As a post-hoc sensitivity check only, removing an exact outer fence before
applying the otherwise identical grader produced `STOP_COMMON_FLOOR`, not GO:

- B-summary visible error floor passed in `0/5` replicates;
- B-summary holdout error floor passed in `3/5` replicates;
- B-summary majority failures were `3/24`;
- Path A had `3/24` majority paired cases and passed its replicate threshold in
  `0/5` replicates;
- Path B had `0/10` majority paired cases and passed its replicate threshold in
  `0/5` replicates;
- instability fell to B-summary `3/24`, B-full `1/24`, and B-none `0/24`.

This diagnostic was not pre-registered and must not be substituted for the
official gate. It shows that the current forced-JSON compliance check is
confounded with the intended memory-correctness construct. M1 is not authorized
from this run. Any rerun with fence normalization requires a reviewed protocol
amendment and a new freeze; thresholds and fixtures must remain unchanged.

## Interpretation boundary

- M0 answers only whether the faithful baseline exhibits stable failures and whether pre-registered headroom exists.
- It does not estimate population prevalence, statistical significance, or treatment effectiveness.
- No aware lifecycle, external memory system, or external benchmark item was implemented in M0.
