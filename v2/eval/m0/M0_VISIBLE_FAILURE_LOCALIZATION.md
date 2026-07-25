# M0 Visible Failure Localization

Status: post-hoc forensic audit; **non-gating**  
Input: sealed Run 2 raw artifact
`973c2db48c8102449bdfa8cef8d54b6cc083da46d17307cfed9051c4c81f4ae4`  
Scope: 16 visible scenarios only; no new model calls; no holdout item-level
inspection or disclosure.

## Decision

The current benchmark does not justify M1.

B-summary reliably formed and surfaced the required fact, but it also surfaced
almost every stale, context-invalid, irrelevant, or deleted fact. In the
forced-choice probes, the response model usually ignored that harmful context.
The bottleneck is therefore **S2 deletion plus S3 applicability/selection**, but
the intended product-level consequence is too weak under this probe design to
pass the pre-registered headroom gate.

This is not evidence that harmful-memory exposure is safe in general. It is
evidence that these particular forced-choice probes make the correct answer too
easy to recover despite bad memory context.

## Method

1. Verify the sealed raw artifact against its committed SHA-256.
2. Select records only by the 16 fixture entries whose frozen split is
   `visible`.
3. For the format diagnostic, remove only an exact complete outer
   ```` ```json ... ``` ```` fence, then apply the unchanged strict
   single-choice grader. This remains non-gating because it was not
   pre-registered.
4. Inspect each B-summary rendered context for:
   - the required current/scoped fact;
   - the frozen harmful fact: stale, currently inapplicable, irrelevant, or
     deleted.
5. Count literal/semantic fact presence, not `context_source_ids` alone.
   Generated Wiki fields have conservative container-level provenance, and
   progress-note provenance is not claim-level.

The content labels below were manually checked against all five visible
contexts. They are a localization aid, not a new benchmark metric.

## Stage findings

### S1 — formation

Across the 11 visible scenarios requiring a memory fact, the required fact was
present in the final B-summary context in `55/55` replicate cells.

This rules out required-fact omission as the main visible bottleneck. It does
not establish full formation faithfulness: the Wiki/reflection text frequently
added clinical interpretations not covered by the frozen gold, such as
describing deletion requests as “denial,” “avoidance,” or an unresolved
therapeutic target. Because M0 has no claim-level support labels for those
inferences, they are a declared qualitative observation, not a count.

### S2 — lifecycle

- Corrections and supersessions: the new value was retained in `35/35` cells,
  but the old value was still mentioned in `33/35`. It was often framed as
  historical or negated, so mention alone is not always an active lifecycle
  error.
- Context exceptions: the scoped rule was retained in `20/20`; the general rule
  was also present in `20/20`. Formation captured the exception, but context
  assembly did not select by the probe’s scope.
- Deletion: deleted content remained plainly recoverable in `10/10`. The
  nickname `Roo` and elevator trauma continued to appear in Wiki, progress, or
  reflection text after an explicit forget request. This is an unambiguous S2
  lifecycle failure.

### S3 — selection/applicability

- Required fact present: `55/55`.
- Harmful fact exposed: `78/80`.

Breakdown:

| Category | Required fact present | Harmful fact exposed |
|---|---:|---:|
| correction | 20/20 | 18/20 |
| supersession | 15/15 | 15/15 |
| context-exception | 20/20 | 20/20 |
| no-memory | n/a | 15/15 |
| deletion | n/a | 10/10 |

This is the strongest mechanism-level finding: `buildContextPrompt` behaves as
an unconditional profile dump. It has high required recall, but almost no
applicability filtering.

### S4 — use

After the exact-fence diagnostic normalization:

- B-summary: `72/80` correct;
- B-full: `76/80` correct;
- B-none: `51/80` correct.

B-summary visible errors per replicate were `3/16`, `2/16`, `1/16`, `1/16`,
and `1/16`; the pre-registered `5/16` floor passed in `0/5` replicates.
B-summary had only `1/16` visible scenario-majority failure
(`del_elevator_trigger`).

All eight B-summary response errors aligned with an exposed harmful memory:

| Scenario | Correct / 5 | Observed wrong choice |
|---|---:|---|
| `corr_coping_method` | 4/5 | recommended both stale and corrected coping |
| `none_crowd_calendar` | 4/5 | injected the unrelated crowd preference |
| `none_running_grammar` | 3/5 | injected the unrelated running preference |
| `del_nickname` | 4/5 | reused the deleted nickname |
| `del_elevator_trigger` | 2/5 | treated the deleted elevator trigger as active |

The remaining 11 visible scenarios were `5/5` correct under B-summary after
format normalization, despite broad harmful-memory exposure.

## Scenario ledger

“Harmful exposed” means the frozen harmful fact was present in the rendered
context, even if marked historical or accompanied by the correct fact.

| Scenario | Required present | Harmful exposed | Fence-normalized B-summary answer | Localization |
|---|---:|---:|---:|---|
| `corr_support_style` | 5/5 | 3/5 | 5/5 | correction retained; no S4 error |
| `corr_coping_method` | 5/5 | 5/5 | 4/5 | stale reflection caused one mixed answer |
| `corr_trigger_source` | 5/5 | 5/5 | 5/5 | old trigger mentioned historically |
| `corr_preferred_name` | 5/5 | 5/5 | 5/5 | old name remained in progress notes |
| `super_stressor` | 5/5 | 5/5 | 5/5 | old stressor remained as history |
| `super_coping` | 5/5 | 5/5 | 5/5 | old coping remained as history |
| `super_goal` | 5/5 | 5/5 | 5/5 | old goal remained as history |
| `ctx_feedback_review` | 5/5 | 5/5 | 5/5 | general and scoped styles both injected |
| `ctx_detail_crisis_plan` | 5/5 | 5/5 | 5/5 | general and scoped styles both injected |
| `ctx_bereavement` | 5/5 | 5/5 | 5/5 | model applied the scoped exception |
| `ctx_humor_panic` | 5/5 | 5/5 | 5/5 | model applied the scoped exception |
| `none_crowd_calendar` | n/a | 5/5 | 4/5 | irrelevant memory changed one answer |
| `none_running_grammar` | n/a | 5/5 | 3/5 | irrelevant memory changed two answers |
| `none_music_password` | n/a | 5/5 | 5/5 | irrelevant memory ignored |
| `del_nickname` | n/a | 5/5 | 4/5 | deletion failed; one direct reuse |
| `del_elevator_trigger` | n/a | 5/5 | 2/5 | deletion failed; majority product error |

## Why the current probe has little headroom

The options often state the desired policy explicitly:

- “gentle, private feedback” versus “very-direct style”;
- “calm and literal, without humor” versus humor during panic;
- a grammatically correct sentence versus an obviously malformed sentence;
- a verified password reset link versus unsafe distractors.

A capable response model can select the normatively correct option from the
probe and option wording even when memory context is polluted. This produces a
real S3 defect but a flat S4 curve. B-none being correct on `51/80` cells
confirms that many probes do not require memory to be solvable.

## Recommendation

1. **Do not enter M1** under protocol v1.2.
2. Preserve the official `INCONCLUSIVE_MODEL_VARIANCE` result and this
   visible-only localization as separate artifacts.
3. Do not spend another full run merely adding fence normalization. The
   already-sealed sensitivity analysis predicts `STOP_COMMON_FLOOR`.
4. If the research question is retained, it requires a reviewed **new M0
   protocol**, not a treatment:
   - normalize only an exact outer JSON fence or use provider-enforced
     structured output;
   - replace self-revealing forced choices with deterministic action/slot tasks
     whose correct output genuinely depends on the memory;
   - preserve the same category balance, provenance ledger, sealed holdout, and
     kill discipline;
   - add claim-level support labels if unsupported Wiki/reflection inference is
     to become a measured reliability failure.

Until that redesign is independently reviewed and frozen, the honest project
decision is: **mechanism defect confirmed; product-level headroom not
established; treatment work stopped.**
