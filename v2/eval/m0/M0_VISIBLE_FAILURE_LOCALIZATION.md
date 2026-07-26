# M0 Visible Failure Localization

Status: post-hoc forensic audit; **non-gating**  
Input: sealed Run 2 raw artifact
`973c2db48c8102449bdfa8cef8d54b6cc083da46d17307cfed9051c4c81f4ae4`  
Scope: 16 visible scenarios only; no new model calls; no holdout item-level
inspection or disclosure.

## Adversarial-review correction

The independent review accepted the need for a new measurement protocol but
required narrower evidence language:

- `55/55` is final-context recoverability, not proof of S1 formation fidelity;
- `78/80` is a definition-dependent mention count, not active harmful state;
- correction/supersession history is not automatically an S2 failure;
- the eight answer errors are associated with mentions, not causally attributed
  to them;
- only elevator deletion supplies a stable visible S2→S4 chain.

The findings below use those corrected boundaries. The official v1.2 decision
remains unchanged.

## Decision

The current benchmark does not justify M1.

The required fact remained recoverable in every required-memory final context,
while almost every stale, context-invalid, irrelevant, or deleted fact was also
mentioned. Final-context recovery cannot prove that S1 formation was complete,
undistorted, or source-faithful. In the forced-choice probes, the response model
usually selected the expected answer despite those mentions. The clearest
mechanism defects are **S2 deletion** and the absence of probe-conditioned S3
selection; the intended product-level consequence was not established under
this probe design.

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
5. Report two evidence levels separately:
   - `context_source_ids`, which deterministically establish only conservative
     container-level provenance;
   - manual literal/semantic inspection, which can establish recoverability or
     mention but is definition-dependent without claim-level labels.

The content labels below were manually checked against all five visible
contexts. They are a localization aid, not a new benchmark metric.

## Stage findings

### S1 — formation

Across the 11 visible scenarios requiring a memory fact, the required source ID
was represented by container-level provenance in `55/55` replicate cells.
Manual inspection also found the required semantic fact recoverable in all
`55/55` final contexts.

This rules out complete final-context omission as the main visible bottleneck.
It does **not** establish that S1 formation was correct: the fact could have
been distorted in summary, Wiki, progress, or reflection before remaining
recoverable downstream. The Wiki/reflection text also added clinical
interpretations not covered by the frozen gold, such as describing deletion
requests as “denial,” “avoidance,” or an unresolved therapeutic target.
Because M0 has no claim-level support labels, those are qualitative
observations, not a measured S1 failure rate.

### S2 — lifecycle

- Corrections and supersessions: the new value was recoverable in `35/35`
  cells, while the old value was still mentioned in `33/35`. Because the old
  value was often historical or negated, this does not establish `33/35`
  effective-state lifecycle failures.
- Context exceptions: the scoped rule was retained in `20/20`; the general rule
  was also present in `20/20`. Coexistence is consistent with `keep_scoped`;
  final context assembly remained unconditioned on the probe.
- Deletion: deleted content remained plainly recoverable in `10/10`. The
  nickname `Roo` and elevator trauma continued to appear in Wiki, progress, or
  reflection text after an explicit forget request. This is an unambiguous S2
  lifecycle failure.

### S3 — selection/applicability

- Required fact recoverable: `55/55`.
- Frozen harmful fact mentioned: `78/80`.
- Frozen harmful source ID present in conservative container provenance:
  `55/80`.

Breakdown:

| Category | Required fact recoverable | Harmful fact mentioned |
|---|---:|---:|
| correction | 20/20 | 18/20 |
| supersession | 15/15 | 15/15 |
| context-exception | 20/20 | 20/20 |
| no-memory | n/a | 15/15 |
| deletion | n/a | 10/10 |

The `78/80` count is definition-dependent: it deliberately includes historical
and negated mentions as well as out-of-scope assertions and deleted content. It
does not mean `78/80` active harmful memories or `78/80` harmful applications.
The narrower implementation-level finding is that `buildContextPrompt` is an
unconditional profile renderer with no probe-conditioned applicability
selection.

### S4 — use

After the exact-fence diagnostic normalization:

- B-summary: `72/80` correct;
- B-full: `76/80` correct;
- B-none: `51/80` correct.

B-summary visible errors per replicate were `3/16`, `2/16`, `1/16`, `1/16`,
and `1/16`; the pre-registered `5/16` floor passed in `0/5` replicates.
B-summary had only `1/16` visible scenario-majority failure
(`del_elevator_trigger`).

All eight B-summary response errors occurred in cells where the frozen harmful
fact was mentioned:

| Scenario | Correct / 5 | Observed wrong choice |
|---|---:|---|
| `corr_coping_method` | 4/5 | recommended both stale and corrected coping |
| `none_crowd_calendar` | 4/5 | injected the unrelated crowd preference |
| `none_running_grammar` | 3/5 | injected the unrelated running preference |
| `del_nickname` | 4/5 | reused the deleted nickname |
| `del_elevator_trigger` | 2/5 | treated the deleted elevator trigger as active |

Because the mention base rate was `78/80`, this co-occurrence is not causal
evidence. Four of the five affected scenarios did not produce a
scenario-majority failure. The only stable visible S2→S4 chain was
`del_elevator_trigger`: deleted content remained recoverable in `5/5`,
B-summary was correct in `2/5`, and B-full was correct in `5/5`.

The remaining 11 visible scenarios were `5/5` correct under B-summary after
format normalization despite broad old/irrelevant-memory mention.

## Scenario ledger

“Harmful mentioned” means the frozen harmful fact was visible in the rendered
context, even if marked historical or accompanied by the correct fact. It is
not an active-state or causal label.

| Scenario | Required recoverable | Harmful mentioned | Fence-normalized B-summary answer | Localization |
|---|---:|---:|---:|---|
| `corr_support_style` | 5/5 | 3/5 | 5/5 | correction retained; no S4 error |
| `corr_coping_method` | 5/5 | 5/5 | 4/5 | one mixed answer; causal attribution not established |
| `corr_trigger_source` | 5/5 | 5/5 | 5/5 | old trigger mentioned historically |
| `corr_preferred_name` | 5/5 | 5/5 | 5/5 | old name remained in progress notes |
| `super_stressor` | 5/5 | 5/5 | 5/5 | old stressor remained as history |
| `super_coping` | 5/5 | 5/5 | 5/5 | old coping remained as history |
| `super_goal` | 5/5 | 5/5 | 5/5 | old goal remained as history |
| `ctx_feedback_review` | 5/5 | 5/5 | 5/5 | general and scoped styles both injected |
| `ctx_detail_crisis_plan` | 5/5 | 5/5 | 5/5 | general and scoped styles both injected |
| `ctx_bereavement` | 5/5 | 5/5 | 5/5 | model applied the scoped exception |
| `ctx_humor_panic` | 5/5 | 5/5 | 5/5 | model applied the scoped exception |
| `none_crowd_calendar` | n/a | 5/5 | 4/5 | one error co-occurred with irrelevant memory |
| `none_running_grammar` | n/a | 5/5 | 3/5 | two errors co-occurred with irrelevant memory |
| `none_music_password` | n/a | 5/5 | 5/5 | irrelevant memory ignored |
| `del_nickname` | n/a | 5/5 | 4/5 | deletion failed; one direct reuse, not stable |
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

The proposed design-only repair is
`docs/innerflow-memory-reliability-eval-v1.3.md`. It is an adversarial-review
candidate, not a frozen authorization to author fixtures or implement M1.
