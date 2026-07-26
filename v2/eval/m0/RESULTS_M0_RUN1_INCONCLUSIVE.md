# InnerFlow Memory Reliability — M0 Results

> Protocol: v1.2 frozen. Counts are the scenario unit; no percentages or LLM judge are used.

## Run identity

- Model: `gemini-2.5-flash`
- Complete replicates: `3`
- Sealed raw artifact SHA-256: `d1ab39b4c9d75485fb566f93d3884dfc3f82c886bd3f85d116c6777535478a32`
- Holdout disclosure: aggregate only; item-level holdout traces remain sealed.

## Run validity

This run is retained as an infrastructure failure, not as evidence about memory
reliability. The harness added small generation caps that the production Java
client does not impose. All `144/144` B-full/B-none probe responses and the
`28/28` completed B-summary probe responses were truncated to invalid JSON;
`44/72` B-summary cells also failed while parsing truncated Wiki JSON. The
sealed raw artifact remains immutable. No failed cell was selectively rerun.
The cap correction is frozen before a new complete run.

## Per-replicate answer errors

| Policy | Split | R1 | R2 | R3 |
|---|---|---|---|---|
| B-summary | visible | 8/16 (+8 unavailable) | 6/16 (+10 unavailable) | 5/16 (+11 unavailable) |
| B-summary | holdout | 3/8 (+5 unavailable) | 3/8 (+5 unavailable) | 3/8 (+5 unavailable) |
| B-full | visible | 16/16 | 16/16 | 16/16 |
| B-full | holdout | 8/8 | 8/8 | 8/8 |
| B-none | visible | 16/16 | 16/16 | 16/16 |
| B-none | holdout | 8/8 | 8/8 | 8/8 |

## Visible scenario-majority results

| Scenario | Category | B-summary | B-full | B-none |
|---|---|---|---|---|
| corr_support_style | correction | unavailable | wrong | wrong |
| corr_coping_method | correction | unavailable | wrong | wrong |
| corr_trigger_source | correction | unavailable | wrong | wrong |
| corr_preferred_name | correction | wrong | wrong | wrong |
| super_stressor | supersession | unavailable | wrong | wrong |
| super_coping | supersession | unavailable | wrong | wrong |
| super_goal | supersession | unavailable | wrong | wrong |
| ctx_feedback_review | context-exception | unavailable | wrong | wrong |
| ctx_detail_crisis_plan | context-exception | unavailable | wrong | wrong |
| ctx_bereavement | context-exception | unavailable | wrong | wrong |
| ctx_humor_panic | context-exception | unavailable | wrong | wrong |
| none_crowd_calendar | no-memory | wrong | wrong | wrong |
| none_running_grammar | no-memory | wrong | wrong | wrong |
| none_music_password | no-memory | wrong | wrong | wrong |
| del_nickname | deletion | unavailable | wrong | wrong |
| del_elevator_trigger | deletion | unavailable | wrong | wrong |

## Sealed-holdout aggregates

| Policy | Majority errors | Non-unanimous |
|---|---:|---:|
| B-summary | 1/8 | 5/8 |
| B-full | 8/8 | 0/8 |
| B-none | 8/8 | 0/8 |

## G0

- Decision: **INCONCLUSIVE**
- Reason code: `INCONCLUSIVE_API_FAILURE`
- Selected path: `none`

| Predicate/count | Result |
|---|---:|

Failed predicates:

- one or more policy/scenario/replicate cells are unavailable

## Interpretation boundary

- M0 answers only whether the faithful baseline exhibits stable failures and whether pre-registered headroom exists.
- It does not estimate population prevalence, statistical significance, or treatment effectiveness.
- No aware lifecycle, external memory system, or external benchmark item was implemented in M0.
