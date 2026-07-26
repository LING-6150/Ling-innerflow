# InnerFlow Memory Reliability & Failure Localization

## M0 Construct-Repair Amendment v1.3

**Status:** adversarial-review candidate; not frozen  
**Date:** 2026-07-26  
**Parent protocol:** v1.2 remains frozen and immutable  
**Scope:** replace M0 measurement constructs only; M1 remains blocked  
**Positioning:** InnerFlow — Memory Reliability and Failure Localization for
Stateful AI Agents

---

## 0. Why this amendment exists

The v1.2 M0 implementation and model run completed, but the official gate was
`INCONCLUSIVE_MODEL_VARIANCE`. Two measurement defects dominated:

1. `216/360` valid single-choice JSON answers were wrapped in one complete
   Markdown JSON fence. The strict grader treated the wrapper as an answer
   error, making formatting variance look like memory variance.
2. The forced-choice probes frequently revealed the normatively or
   linguistically preferable answer. After a non-gating exact-fence diagnostic,
   B-none still answered `51/80` visible cells correctly and B-summary reached
   only `3`, `2`, `1`, `1`, and `1` errors out of 16. The original probe did
   not reliably require memory.

The visible forensic audit found one stable deletion chain and widespread
old/irrelevant-memory mention, but did not establish broad product-level
headroom. This amendment repairs the measurement construct before any
treatment exists. It does not reinterpret the v1.2 result as GO.

### 0.1 Version relationship

- v1.2, its fixtures, manifests, raw hashes, and results remain unchanged.
- v1.3 creates a new M0 corpus, new holdout, new output contract, and new run.
- No v1.2 output is pooled into a v1.3 denominator.
- v1.2 visible items are development evidence about a failed probe template;
  they are ineligible for the v1.3 confirmatory corpus.
- M1 is authorized only if the official v1.3 G0 passes the unchanged gate.

### 0.2 Final construct-repair rule

v1.3 is the last permitted construct-level repair for this hypothesis. If a
complete v1.3 M0 does not return `GO_PATH_A` or `GO_PATH_B` under the unchanged
thresholds, work on the conflict/applicability-aware treatment stops. No later
probe redesign, category rebalance, threshold relaxation, or result-driven
fixture replacement is allowed.

---

## 1. Review disposition

| Finding | Disposition | v1.3 action |
|---|---|---|
| F1 — probes do not isolate memory dependence | **accept and patch** | Replace forced choices with a fixed action/slot contract and a pre-model counterfactual dependency gate. |
| F2 — Markdown packaging dominates variance | **accept and patch** | Normalize at most one exact complete outer `json` fence; retain strict JSON/schema grading afterward. |
| F3 — final-context recovery does not prove S1 correctness | **accept and patch** | Report recoverability separately from formation; add tracked claim/state labels and preserve unsupported inference as qualitative unless exhaustively labeled. |
| F4 — mention exposure conflates active and historical claims | **accept and patch** | Split tracked mentions into current-effective, historical/negated, out-of-scope, deleted-recoverable, and unsupported. |
| F5 — error/exposure co-occurrence is not causal | **accept and patch** | Report paired same-cell associations only; reserve bottleneck claims for pre-registered oracle interventions and stable paired repairs. |

No finding changes the v1.2 official result.

---

## 2. Frozen research question

v1.3 asks the same question as v1.2:

> Does the faithful InnerFlow summary-plus-Wiki memory path create stable
> answer-level failures during correction, supersession, context exception,
> irrelevant-memory, and deletion events, with enough pre-registered headroom
> to justify an applicability-aware treatment?

The amendment changes how the final action is elicited and graded. It does not
change B-summary, add a treatment, enlarge deletion, or lower the gate.

---

## 3. Systems and fidelity

The evaluated systems remain:

- `B-summary`: the faithful settled/quiescent summary, Wiki merge, reflection,
  and `buildContextPrompt` replica defined by v1.2 §4;
- `B-full`: all source history in timestamp order;
- `B-none`: no persistent memory.

All v1.2 conformance requirements remain prerequisites. The v1.3 run must use
the same response model, response prompt, temperature, token limit, retry
policy, and output normalizer across all three policies. Only memory context
may differ.

No aware lifecycle, external memory system, or external benchmark execution is
implemented in M0.

---

## 4. Fixed action/slot output contract

### 4.1 Response object

Every v1.3 probe returns exactly:

```json
{
  "memory_action": "APPLY",
  "response_action": "ASK_PERMISSION"
}
```

Rules:

- object keys are exactly `memory_action` and `response_action`;
- `memory_action` is `APPLY` or `ABSTAIN`;
- `response_action` is one value from the frozen ontology in §4.2, or `NONE`;
- `ABSTAIN` requires `response_action == "NONE"`;
- `APPLY` requires `response_action != "NONE"`;
- no explanation, rationale, confidence, additional key, or multiple action is
  allowed;
- exact case-sensitive enum matching is used after the transport normalizer.

An invalid object is wrong. The grader does not infer intent from prose.

### 4.2 Ontology freeze

The ontology is frozen before scenario content is authored:

| Family | Allowed response actions |
|---|---|
| support initiation | `ASK_PERMISSION`, `GIVE_DIRECT_STEPS`, `LISTEN_FIRST` |
| detail | `USE_CONCISE_DETAIL`, `USE_EXPANDED_DETAIL` |
| tone/channel | `USE_GENTLE_PRIVATE`, `USE_DIRECT_TONE`, `USE_CALM_LITERAL`, `USE_LIGHT_HUMOR` |
| coping | `SUGGEST_BREATHING`, `SUGGEST_QUIET_WALK`, `SUGGEST_SOCIAL_SUPPORT`, `SUGGEST_JOURNALING` |
| no memory action | `NONE` |

The corpus may use only this ontology. Adding a content-specific action after
seeing model output is prohibited. Candidate authors may reject a scenario if
no ontology action represents it; they may not extend the ontology to rescue
that scenario after execution begins.

### 4.3 Probe wording

The probe asks for the action object for a named current situation. It must not:

- display answer options;
- quote or paraphrase the applicable stored preference;
- use evaluative words that make one action normatively superior;
- contain a grammatical, safety, or factual clue that uniquely selects the
  gold action without memory;
- request a rationale.

The system prompt states that stored memory may be stale, scoped, deleted, or
irrelevant and must be used only when applicable. This instruction is identical
for every policy and scenario.

### 4.4 Transport normalization

The v1.3 grader performs exactly this normalization:

1. Remove leading and trailing Unicode whitespace from the complete response.
2. If and only if the remaining response matches one complete outer fence:

   ```regex
   \A```json[ \t]*\r?\n(?P<body>[\s\S]*?)\r?\n```[ \t]*\z
   ```

   replace the response with `body`.
3. Do not remove a second fence.
4. Do not accept an untyped fence, prose before/after the fence, multiple code
   blocks, comments, trailing text, or JSON embedded in prose.
5. Parse one JSON object and apply §4.1 exactly.

This is transport normalization, not semantic grading. The normalizer and its
positive/negative tests are frozen before any model call.

---

## 5. Counterfactual memory-dependency contract

### 5.1 Required-memory categories

Every correction, supersession, and context-exception candidate includes a
counterfactual pair:

- identical probe text;
- identical non-memory task state;
- identical response schema and ontology;
- one controlled change to the effective setup memory;
- a different gold `response_action`.

Example:

```json
{
  "probe_template_id": "detail_policy_v1",
  "probe": "Return the detail-level action for the requested project update.",
  "world_a": {
    "effective_memory": "For project updates, keep the explanation concise.",
    "gold": {
      "memory_action": "APPLY",
      "response_action": "USE_CONCISE_DETAIL"
    }
  },
  "world_b": {
    "effective_memory": "For project updates, include expanded detail.",
    "gold": {
      "memory_action": "APPLY",
      "response_action": "USE_EXPANDED_DETAIL"
    }
  }
}
```

The counterfactual world is a fixture-validation object, not an extra scored
scenario. Its purpose is to prove that the same probe can have two different
valid answers depending only on memory.

### 5.2 Static dependency gate

A required-memory candidate is eligible only when all checks pass before model
execution:

1. `world_a.probe == world_b.probe` byte-for-byte.
2. The non-memory state and action ontology are identical.
3. Exactly one effective memory claim changes.
4. The two gold response actions differ.
5. Both actions are plausible and safe in the same probe situation.
6. Removing setup memory leaves no uniquely derivable gold action.
7. Neither action is preferred by grammar, safety policy, or generic social
   norms independently of memory.

Checks 5–7 require a written reviewer disposition. They are not inferred from
B-none model output, and B-none performance is never used to select fixtures.

### 5.3 No-memory category

No-memory candidates retain an irrelevant but valid user memory and ask for an
action in a different domain. Gold is:

```json
{
  "memory_action": "ABSTAIN",
  "response_action": "NONE"
}
```

The candidate must include at least two irrelevant-memory variants while
keeping the probe and gold identical. This proves invariance to irrelevant
memory without using model output as a selection filter.

### 5.4 Deletion category

Deletion candidates contain a pre-delete and post-delete state with the same
probe:

- pre-delete gold applies the stored action;
- post-delete gold is `ABSTAIN` / `NONE`;
- at least one of the four selected deletion scenarios deletes a claim already
  absorbed into summary or Wiki text, preserving the v1.2 derived-deletion
  requirement.

Only the post-delete state is scored in the main corpus. The pre-delete object
is a static dependency/conformance check.

---

## 6. Claim-level state contract

### 6.1 Gold tracked claims

Each fixture defines tracked claims:

```json
{
  "claim_id": "claim_...",
  "canonical_value": "ask permission before advice",
  "source_event_ids": ["event_..."],
  "probe_state": "CURRENT_EFFECTIVE",
  "surface_forms": [
    "ask permission before advice",
    "request permission before offering advice"
  ]
}
```

`probe_state` is exactly one of:

- `CURRENT_EFFECTIVE`;
- `HISTORICAL_NEGATED`;
- `OUT_OF_SCOPE`;
- `DELETED_RECOVERABLE`;
- `IRRELEVANT`;
- `UNSUPPORTED`.

The first five describe fixture-authored source claims. `UNSUPPORTED` is used
only for a specifically pre-registered forbidden inference with deterministic
surface forms; M0 does not claim exhaustive unsupported-claim detection over
free-form summary text.

### 6.2 Rendered-context labels

For every tracked claim, the evaluator reports separately:

- source ID represented by container provenance;
- semantic surface recoverable;
- rendered as active/current;
- rendered as historical or negated;
- rendered without enough state information to disambiguate.

Surface forms and state cues are frozen before model calls. Ambiguous matches
are `UNRESOLVED`, not silently labeled harmful.

### 6.3 Metric language

Reports must not equate:

- source-ID presence with claim-level faithfulness;
- historical/negated mention with active harmful memory;
- mention/error co-occurrence with causation;
- mechanism exposure reduction with product improvement.

Allowed M0 language includes:

- “required claim recoverable in x/N contexts”;
- “out-of-scope claim rendered as active in x/N contexts”;
- “deleted claim remained recoverable in x/N contexts”;
- “the same cell was baseline-wrong and comparator-correct in x/N cases.”

---

## 7. Corpus construction and anti-cherry-pick rules

### 7.1 Distribution

The selected corpus remains exactly 24 scenarios:

| Category | Total | Visible | Sealed holdout |
|---|---:|---:|---:|
| correction | 6 | 4 | 2 |
| supersession | 4 | 3 | 1 |
| context-exception | 6 | 4 | 2 |
| no-memory | 4 | 3 | 1 |
| deletion | 4 | 2 | 2 |
| **Total** | **24** | **16** | **8** |

The old 24 items, including all old visible and holdout items, are ineligible.
Their taxonomy patterns may inform generic templates, but names, events,
probes, gold values, and distinctive content may not be copied.

### 7.2 Taxonomy anchors and provenance

Scenario categories remain anchored to the external benchmark taxonomies
declared in v1.2. Each candidate records:

- source taxonomy and reference;
- whether it is an item-level adaptation, construction-pattern item, or product
  extension;
- transformation log;
- author and reviewer;
- why the gold is unambiguous;
- leakage and prior-exposure notes.

No item is represented as an external benchmark adaptation without traceable
item-level provenance.

### 7.3 Candidate registry

Before model calls, the registry includes every included, rejected, and
replaced candidate with:

- canonical fixture hash;
- counterfactual dependency result;
- action-ontology compatibility;
- claim-state review;
- rejection/replacement reason;
- whether the author saw v1.2 visible outputs.

Minimum eligible pool before deterministic selection:

| Category | Minimum eligible | Selected |
|---|---:|---:|
| correction | 9 | 6 |
| supersession | 6 | 4 |
| context-exception | 9 | 6 |
| no-memory | 6 | 4 |
| deletion | 6 | 4 |

### 7.4 Deterministic selection

After eligibility is frozen:

1. Canonicalize each candidate excluding `split`, commit the complete candidate
   pool hash, and publish the freeze timestamp.
2. Use the first NIST Randomness Beacon 2.0 pulse occurring at least 24 hours
   after that timestamp as the selection seed. Record the pulse timestamp,
   output value, and signed-pulse hash in the manifest.
3. Compute
   `SHA-256(seed + "|select|" + canonical_candidate_sha256)`.
4. Sort ascending within category and select the required category count.
5. Rank selected items again with
   `SHA-256(seed + "|split|" + canonical_candidate_sha256)` and assign the
   fixed category holdout quotas first; the remainder is visible.
6. Publish selected IDs and hashes for visible items; publish only category
   counts and aggregate hashes for holdout.

No B-summary, B-full, B-none, or responder output may exist before selection.
The future public seed prevents an author from choosing a convenient salt after
seeing candidate hashes.

### 7.5 Holdout independence

The eight v1.3 holdout items are new. They may not be edited versions of the
old holdout.

Preferred process:

- a reviewer who did not implement the harness authors or independently
  reviews the holdout candidates;
- the reviewer retains the unredacted holdout candidate registry; the public
  repository receives a redacted registry plus its aggregate hash;
- the implementation agent receives only an encrypted/sealed fixture artifact
  and public hashes until execution;
- item-level traces remain outside Git; only aggregate results are published.

If no independent human annotator is available:

- use the term `sealed holdout`, not `independent holdout`;
- declare no IAA and no true annotator independence;
- record any agent-assisted authorship;
- do not claim that sealing establishes construct validity.

---

## 8. Sampling, grading, and run freeze

### 8.1 Sampling

The v1.2 sampling discipline remains:

- realistic non-zero temperature;
- three independent complete replicates;
- fixed seed when supported;
- identical stage parameters across policies;
- randomized policy order per replicate;
- five-run escalation under the unchanged variance rule.

Exact model ID, temperatures, token limits, retry policy, request IDs, prompt
hashes, dependency lock hash, corpus hash, ontology hash, normalizer hash, and
run date are frozen before output.

### 8.2 Failure semantics

- Provider failures exhaust the frozen retry policy, then make the run
  `INCONCLUSIVE_API_FAILURE`.
- A syntactically complete response that fails the v1.3 normalizer/schema is
  wrong, not retried.
- Missing cells never reduce a denominator.
- Scenario majority, replicate-level conditions, paired cases, and five-run
  instability use v1.2 §6.1 unchanged.

### 8.3 Full new run

v1.3 requires a full new run of every selected scenario, policy, and replicate.
It may not:

- regrade v1.2 output as official v1.3 evidence;
- reuse v1.2 formed contexts;
- rerun only failed cells;
- merge old and new denominators;
- tune prompts after visible output is observed.

The runner must checkpoint after every complete replicate into a sealed local
artifact so infrastructure interruption does not discard hours of completed
work. Checkpointing changes persistence only; it must not expose item-level
holdout data or permit selective resume.

---

## 9. G0 remains unchanged

All v1.2 G0 thresholds and identities are inherited verbatim:

### Common floor

1. B-summary errors at least `5/16` visible scenarios in at least the required
   replicate count.
2. B-summary errors at least `2/8` sealed-holdout scenarios in at least the
   required replicate count.
3. Scenario-majority failures span at least two categories and are not
   deletion-only.
4. Selected-path comparator instability is at most `6/24`, otherwise invoke
   the frozen five-run procedure.

### Path A

- at least `4/24` same-scenario majority cases where B-summary is wrong and
  B-full is correct;
- the same paired contrast reaches `4/24` in the required replicate count.

### Path B

- only the fixed 10 context-exception/no-memory scenarios;
- at least `3/10` same-scenario majority cases where B-summary and B-full are
  both wrong;
- at least one supporting case from each category;
- a no-memory supporter requires B-none correct;
- a context-exception supporter requires the narrower applicable gold claim
  recoverable in B-full context;
- the same paired/mechanism condition holds in the required replicate count.

Reason codes remain:

- `GO_PATH_A`;
- `GO_PATH_B`;
- `STOP_COMMON_FLOOR`;
- `STOP_NO_HEADROOM_PATH`;
- `INCONCLUSIVE_API_FAILURE`;
- `INCONCLUSIVE_MODEL_VARIANCE`.

No threshold may be relaxed after output. An official complete result other
than `GO_PATH_A` or `GO_PATH_B` terminates this treatment hypothesis under
§0.2. A pure external outage may be replayed once under the identical frozen
manifest; it does not authorize a construct change.

---

## 10. M0 deliverables

Before execution:

- adversarially reviewed and frozen v1.3 protocol;
- frozen action ontology and exact-fence normalizer;
- complete candidate registry;
- static counterfactual dependency report;
- new 24-item corpus and split manifest;
- sealed new holdout artifact;
- B-summary fidelity/conformance evidence;
- model/prompt/run manifest.

After execution:

- sealed raw artifact and public hash;
- visible item-level and holdout aggregate report;
- count-first S1–S4 tables using §6 evidence labels;
- official signed G0 decision;
- explicit termination statement if G0 does not GO.

No M1 code exists before the signed G0 decision.

---

## 11. Required pre-freeze tests

The v1.3 design cannot freeze until deterministic tests demonstrate:

1. exact bare JSON is accepted;
2. one exact complete outer `json` fence is accepted;
3. nested fences, untyped fences, prose, extra keys, invalid enums, and multiple
   actions are rejected;
4. `ABSTAIN` requires `NONE`, and `APPLY` forbids `NONE`;
5. every required-memory candidate changes gold under its counterfactual memory
   change while keeping the probe byte-identical;
6. every no-memory candidate keeps gold invariant across irrelevant memories;
7. every deletion candidate changes from apply before deletion to abstain after
   deletion;
8. ontology values are frozen and no fixture introduces another value;
9. category and split counts match §7.1;
10. deterministic selection reproduces the committed manifest;
11. old v1.2 item hashes are absent from the new corpus;
12. an independent content-overlap review finds no renamed or lightly
    paraphrased v1.2 item;
13. reports contain no holdout IDs or item-level traces;
14. G0 synthetic tests still reject disjoint Path B errors and unstable
    comparators;
15. a stopped/inconclusive run cannot invoke M1.

---

## 12. Interpretation boundaries

v1.3 may support:

- a faithful baseline has stable product-level failures under a
  memory-dependent action task;
- an applicability or compression opportunity passes a pre-registered path;
- a deleted claim remained recoverable and changed a paired action;
- a particular oracle intervention repaired the most scenarios in the frozen
  pipeline.

v1.3 may not claim:

- the project invented memory benchmarking;
- mention equals active harmful application;
- final-context recovery proves formation fidelity;
- error/exposure co-occurrence proves causation;
- mechanism exposure reduction implies product improvement;
- a small fixture count estimates population prevalence;
- a known exact-match bug proves summary mechanisms inherently cannot manage
  lifecycle;
- a post-hoc diagnostic is a confirmatory result.

---

## 13. Freeze checklist

The owner and adversarial reviewer must answer all items before `FREEZE`:

1. Does the action/slot task require memory rather than normative option
   selection?
2. Does every required-memory item pass a byte-identical-probe counterfactual
   dependency check?
3. Can B-none performance influence fixture selection? It must not.
4. Is the exact-fence normalizer the only transport repair?
5. Are source presence, semantic recoverability, active assertion, historical
   mention, and deletion recovery reported separately?
6. Are all old v1.2 items excluded?
7. Are category/split counts and G0 thresholds unchanged?
8. Was the candidate registry frozen before model output?
9. Is the new holdout genuinely new and still sealed?
10. Are absent human IAA/independence disclosed?
11. Is v1.3 explicitly the final construct repair?
12. Does any non-GO official result terminate the hypothesis?
13. Is M1 still absent?

**Current freeze decision:** `PATCH BEFORE FREEZE`  
**Next action:** independent adversarial review of this v1.3 candidate. No
fixture authoring or implementation begins before review disposition.
