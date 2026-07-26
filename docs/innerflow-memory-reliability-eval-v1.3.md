# InnerFlow Memory Reliability & Failure Localization

## M0 Construct-Repair Amendment v1.3

**Status:** protocol frozen; M0 run not yet authorized
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
fixture replacement is allowed. The only permitted replay and conformance
repair are the narrow cases in §9.1; neither permits a semantic change.

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

### 1.1 Second adversarial-review disposition

| Finding | Disposition | v1.3 action |
|---|---|---|
| F1 — action/slot still measures self-reported applicability | **accept and patch** | Remove `memory_action`; score one executable downstream `response_action`; replace `NONE` with `USE_UNPERSONALIZED_DEFAULT`; narrow the headline to downstream action selection. |
| F2 — static counterfactual does not exclude action priors | **accept and patch** | Execute both memory worlds for every policy and replicate; seed and freeze one gate world while retaining the other as a mandatory diagnostic. |
| F3 — semantic action names and pool composition can leak priors | **accept and patch** | Freeze action-pair, situation-family, template, and gold-action balance before candidate content; exclude normatively determined situations. |
| F4 — gold state and observed rendering are conflated | **accept and patch** | Separate gold applicability from observed rendering; bound automatic claims to the frozen lexicon and move unresolved semantics to qualitative audit. |
| F5 — future random selection does not prevent pool-level manipulation | **accept and patch** | Freeze pool strata first, prohibit near-duplicate slot occupancy, review the entire eligible pool before the seed, and make the first valid selection binding except for a pre-signed objective conformance violation. |
| F6 — holdout order and role separation are underspecified | **accept in part; declared limitation for technical isolation** | Review all candidates before split; forbid post-split replacement; define roles and process sealing. This solo project does not claim cryptographic separation from the owner or true annotator independence. |
| F7 — checkpoint/resume lacks an official cell boundary | **accept and patch** | Define a complete replicate, discard partial replicates, freeze request order, and prohibit cell-level selective retry. |
| F8 — final-repair and outage boundaries are not executable | **accept and patch** | Add a signed termination matrix: no-response provider failures always remain missing/API-inconclusive; external evidence controls replay eligibility only; complete invalid responses are model wrong output. |

The protected-CI/key-custody proposal in F6 is not adopted as a validity claim:
the owner of a solo repository ultimately controls code and credentials, so it
would not create genuine independence. The narrower process-sealed protocol in
§7.5 is auditable and states this limitation directly.

**Closure review:** `FREEZE` on 2026-07-26. F1–F8 are `CLOSED`. This freezes
the research question, constructs, corpus matrix, gates, termination rules, and
interpretation boundaries. It authorizes M0 conformance implementation and
candidate authoring, not responder-model execution.

---

## 2. Frozen research question

v1.3 asks the same question as v1.2:

> Does the faithful InnerFlow summary-plus-Wiki memory path create stable
> downstream action-selection failures during correction, supersession, context exception,
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
policy, output normalizer, action ontology, and deterministic action executor
across all three policies. Only memory context may differ.

No aware lifecycle, external memory system, or external benchmark execution is
implemented in M0.

---

## 4. Fixed action/slot output contract

### 4.1 Response object

Every v1.3 probe returns exactly:

```json
{
  "response_action": "ASK_PERMISSION"
}
```

Rules:

- the only object key is `response_action`;
- `response_action` is exactly one value from the frozen ontology in §4.2;
- no explanation, rationale, confidence, additional key, or multiple action is
  allowed;
- exact case-sensitive enum matching is used after the transport normalizer.

An invalid object is wrong. The grader does not infer intent from prose.
Applicability is derived by the evaluator from the fixture's gold state; the
model never reports whether it believes memory was applied.

### 4.2 Ontology freeze

The ontology is frozen before scenario content is authored:

| Family | Allowed response actions |
|---|---|
| support initiation | `ASK_PERMISSION`, `GIVE_DIRECT_STEPS` |
| detail | `USE_CONCISE_DETAIL`, `USE_EXPANDED_DETAIL` |
| tone/channel | `USE_GENTLE_PRIVATE`, `USE_DIRECT_TONE` |
| unpersonalized path | `USE_UNPERSONALIZED_DEFAULT` |

The corpus may use only this ontology. Adding a content-specific action after
seeing model output is prohibited. Candidate authors may reject a scenario if
no ontology action represents it; they may not extend the ontology to rescue
that scenario after execution begins.

Each action is a frozen downstream command consumed by a deterministic
executor. Before candidate authoring, the manifest binds every enum to one
concrete response behavior/template and records the executor hash. For example,
`USE_CONCISE_DETAIL` selects the concise response template, while
`USE_UNPERSONALIZED_DEFAULT` selects the domain-appropriate generic template
without persistent-memory personalization. The executor does not call a model,
inspect the policy, or alter the selected action. M0 measures selection of
these commands; it does not measure free-form response quality.

### 4.3 Action-prior and coverage contract

Before candidate content is authored, the following matrix is frozen. `E`
means a traceable item-level adaptation or external benchmark construction
pattern under §7.2; `P` means a disclosed InnerFlow product extension.

| Category | Action pair / band | Eligible | Selected | Selected provenance |
|---|---|---:|---:|---|
| correction | concise ↔ expanded detail | 3 | 2 | 2E |
| correction | gentle/private ↔ direct tone | 3 | 2 | 2E |
| correction | ask permission ↔ give direct steps | 3 | 2 | 1E + 1P |
| supersession | concise ↔ expanded detail | 3 | 2 | 2E |
| supersession | gentle/private ↔ direct tone | 3 | 2 | 1E + 1P |
| context-exception | concise ↔ expanded detail | 3 | 2 | 2E |
| context-exception | gentle/private ↔ direct tone | 3 | 2 | 2E |
| context-exception | ask permission ↔ give direct steps | 3 | 2 | 1E + 1P |
| no-memory | information/task default | 3 | 2 | 2E |
| no-memory | social/support default | 3 | 2 | 1E + 1P |
| deletion | detail pre-delete ↔ default post-delete | 3 | 2 | 2P |
| deletion | tone pre-delete ↔ default post-delete | 3 | 2 | 2P |

Each row's three candidates fill exactly these precommitted
situation/template slots, one candidate per slot:

| Matrix row | Frozen slots |
|---|---|
| correction — detail | project update; tutorial explanation; itinerary briefing |
| correction — tone | peer review; planning disagreement; routine accountability reminder |
| correction — support | routine setback; decision uncertainty; creative block |
| supersession — detail | meeting recap; technical handoff; options comparison |
| supersession — tone | performance reflection; boundary negotiation; schedule conflict |
| context-exception — detail | expert-vs-novice audience; work-vs-personal update; urgent-vs-routine briefing |
| context-exception — tone | public-vs-private feedback; work-vs-home disagreement; celebration-vs-debugging |
| context-exception — support | venting-vs-planning; ideation-vs-decision; setback-vs-next-step |
| no-memory — information/task | unrelated scheduling; neutral summarization; document organization |
| no-memory — social/support | casual greeting; third-party coordination; neutral check-in |
| deletion — detail | saved report format; saved reading format; saved planning format |
| deletion — tone | saved feedback style; saved reminder style; saved collaboration style |

This produces the fixed 36-candidate minimum and 24 selected scenarios in
§7.1, including 16 traceable external item/pattern scenarios and eight
declared product extensions. Within each row, all three eligible candidates
must have different underlying events, situation families, and probe-template
families. Selection therefore cannot place two near-duplicate templates in one
row.

For each required-memory row, the two selected scenarios form one action pair
and each action becomes gate-world gold exactly once. A situation is ineligible
if safety, crisis policy, factual correctness, grammar, politeness, or generic
social norms make either action uniquely preferable without memory.
`USE_UNPERSONALIZED_DEFAULT` is the gold command for no-memory and
post-deletion worlds; it means execute a real generic response path, not refuse
to answer.

### 4.4 Probe wording

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

### 4.5 Transport normalization

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
      "response_action": "USE_CONCISE_DETAIL"
    }
  },
  "world_b": {
    "effective_memory": "For project updates, include expanded detail.",
    "gold": {
      "response_action": "USE_EXPANDED_DETAIL"
    }
  }
}
```

Both worlds are executed for B-summary, B-full, and B-none in every complete
replicate. One is the preselected `gate_world`; its result supplies the
scenario's single G0 label and preserves the 24-scenario denominator. The
other is the mandatory `counter_world`: it is reported as a diagnostic but
never used to select/reject a fixture, change gold, tune a prompt, or alter a
gate. The pair tests whether behavior changes when only effective memory
changes instead of merely documenting that two gold answers are conceivable.

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

Checks 5–7 require a named reviewer's written symmetry disposition. The
candidate records a symmetry certificate containing the controlled claim,
the two action bindings, why both remain plausible and safe, and why no
non-memory norm uniquely selects either. These checks are not inferred from
B-none output, and B-none performance is never used to select fixtures.

### 5.3 Gate-world assignment and paired diagnostic

After candidate selection and split, but before the first model request, the
same future public seed from §7.4 assigns gate worlds using the
`"|gate-world|"` domain separator. Within each selected required-memory
`category × action_pair` stratum, sort ascending by
`SHA-256(seed_bytes || UTF8("|gate-world|") || candidate_hash_bytes)`; assign
the first half to the pair's lexicographically first action and the second half
to its other action. This makes each action gate-world gold equally often.
For no-memory variants, the lower-ranked variant is the gate world. Assignment
and its manifest hash are committed before any request.

For every required-memory pair, reports include:

- gate-world accuracy for each policy and replicate;
- counter-world accuracy, excluded from G0;
- paired action-switch behavior;
- whether B-none repeats one action across the opposite-gold worlds, exposing
  an action prior.

Counter-world diagnostics may explain a result but cannot retroactively alter
the corpus or authorize another run.

### 5.4 No-memory category

No-memory candidates retain an irrelevant but valid user memory and ask for an
action in a different domain. Gold for both variants is:

```json
{
  "response_action": "USE_UNPERSONALIZED_DEFAULT"
}
```

The candidate must include at least two irrelevant-memory variants while
keeping the probe and gold identical. This proves invariance to irrelevant
memory without using model output as a selection filter. Both variants are
executed for every policy and replicate. The future seed selects one variant
as the scenario's gate world; the other is a mandatory diagnostic and cannot
affect fixture eligibility.

### 5.5 Deletion category

Deletion candidates contain a pre-delete and post-delete state with the same
probe:

- pre-delete gold applies the stored action;
- post-delete gold is `USE_UNPERSONALIZED_DEFAULT`;
- at least one of the four selected deletion scenarios deletes a claim already
  absorbed into summary or Wiki text, preserving the v1.2 derived-deletion
  requirement.

Both states are executed for every policy and replicate. Because the category
construct is deletion compliance, the post-delete state is always the
scenario's G0 gate world; the pre-delete state is the mandatory counterfactual
diagnostic. This exception to seeded world assignment is frozen before
candidate content and prevents a pre-delete success from standing in for
deletion behavior.

---

## 6. Claim-level measurement contract

### 6.1 Gold applicability

Each fixture defines tracked claims:

```json
{
  "claim_id": "claim_...",
  "canonical_value": "ask permission before advice",
  "source_event_ids": ["event_..."],
  "gold_applicability": "CURRENT_EFFECTIVE",
  "surface_forms": [
    "ask permission before advice",
    "request permission before offering advice"
  ]
}
```

`gold_applicability` is authored from source events before memory rendering and
is exactly one of:

- `CURRENT_EFFECTIVE`;
- `HISTORICAL`;
- `OUT_OF_SCOPE`;
- `DELETED`;
- `IRRELEVANT`;
- `FORBIDDEN_INFERENCE`.

The first five describe fixture-authored source claims.
`FORBIDDEN_INFERENCE` is only a specifically pre-registered inference with
frozen deterministic surface forms. Gold applicability never contains an
observed result such as “recoverable,” “active,” or “negated.”

### 6.2 Observed rendering

For every tracked claim and rendered policy context, the evaluator records
exactly one observed label:

- `NOT_DETECTED_UNDER_FROZEN_LEXICON`;
- `ACTIVE_ASSERTION`;
- `HISTORICAL_OR_NEGATED`;
- `AMBIGUOUS`.

Automatic counts are limited to:

- container source-ID provenance;
- exact or frozen normalized surface-form matches;
- explicit frozen active/historical/negation state cues;
- the structured response-action schema.

Surface forms, normalization, and state cues are frozen before model calls.
A lexicon miss is reported only as
`NOT_DETECTED_UNDER_FROZEN_LEXICON`; it does not prove semantic absence.
Matches whose state cannot be resolved are `AMBIGUOUS`, never silently labeled
active or harmful.

### 6.3 Automatic and qualitative outputs

The confirmatory/gating report contains only deterministic automatic counts.
A separate non-gating qualitative audit may inspect uncovered paraphrase,
distortion, omission, active-versus-historical ambiguity, or unsupported
inference. It must identify the reviewer and evidence but may not change an
automatic label, gate, fixture, lexicon, or rerun decision.

### 6.4 Metric language

Reports must not equate:

- source-ID presence with claim-level faithfulness;
- historical/negated mention with active harmful memory;
- frozen-lexicon non-detection with semantic absence;
- mention/error co-occurrence with causation;
- mechanism exposure reduction with product improvement.

Allowed M0 language includes:

- “required claim detected under the frozen lexicon in x/N contexts”;
- “out-of-scope claim rendered as active in x/N contexts”;
- “deleted claim remained an active assertion in x/N contexts”;
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

Before candidate content is authored, commit the complete stratum specification
from §4.3, including required pool/selection counts by action pair, situation
family, template family, and provenance stratum. Candidate authoring may fill
only those slots.

Before model calls, the registry includes every included, rejected, and
replaced candidate with:

- canonical fixture hash;
- counterfactual dependency result;
- symmetry certificate and named reviewer disposition;
- action-ontology compatibility;
- claim-state review;
- rejection/replacement reason;
- whether the author saw v1.2 visible outputs.
- authoring agent/task identifier, authoring-prompt hash, and materials visible
  to that author;
- underlying-event, template-family, and semantic-overlap fingerprints.

No two candidates derived from the same underlying event or near-duplicate
template may occupy separate selection slots. A context-isolated authoring
agent is the default and is not given the v1.2 item-level failure ledger.
Any exception is recorded and reviewed before the pool freezes. The pool
reviewer audits the entire eligible pool—not only selected items—for
stratum balance, normative action priors, leakage, and near duplicates.

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
2. Use the first NIST Randomness Beacon 2.0 pulse whose timestamp is greater
   than or equal to `freeze_timestamp + 24 hours` as the selection seed.
   The manifest pins the Beacon 2.0 chain identifier, HTTPS endpoint, pulse
   timestamp, output value in canonical lowercase hexadecimal, signature
   verification result, and signed-pulse hash. `seed_bytes` is the decoded
   output-value byte sequence, not the UTF-8 hex characters. If the endpoint is
   unavailable, selection waits; no convenience seed is substituted.
3. Compute
   `SHA-256(seed_bytes || UTF8("|select|") || candidate_hash_bytes)`.
4. Sort ascending within each frozen
   `category × action_pair/band × provenance` stratum and select the committed
   §4.3 quota; situation/template uniqueness is an eligibility constraint.
5. Rank selected items again with
   `SHA-256(seed_bytes || UTF8("|split|") || candidate_hash_bytes)` and assign
   the fixed category holdout quotas first; the remainder is visible. Because
   several per-category holdout quotas are odd, visible and holdout subsets are
   not separately claimed to be action-balanced; only the complete selected
   corpus has exact required-memory pair balance.
6. Publish selected IDs and hashes for visible items; publish only category
   counts and aggregate hashes for holdout.

Canonical fixture serialization is UTF-8 RFC 8785 JSON Canonicalization Scheme;
hashes are SHA-256 over those bytes and rendered as lowercase hex. Selection,
split, and §5.3 gate-world assignment use the same seed with distinct domain
separators.

No B-summary, B-full, B-none, or responder output may exist before selection.
The future public seed prevents an author from choosing a convenient salt after
seeing candidate hashes. It does not establish corpus validity; the pre-content
strata and whole-pool review address pool-level manipulation.

### 7.5 Holdout independence

The eight v1.3 holdout items are new. They may not be edited versions of the
old holdout.

Every eligible candidate—not a predicted holdout subset—is reviewed before the
future seed. Split assignment never changes eligibility. If any selected item
is found invalid after the split, invalidation may occur only when it violates
a named eligibility or conformance predicate already frozen and signed before
the pool hash. The incident must identify that predicate, objective evidence,
and the old pool, seed, selection, and incident hashes. Content preference,
perceived difficulty, an inconvenient selection, or any criterion invented
after the split cannot trigger invalidation.

The first validly signed pool/seed/selection is binding. When a pre-registered
predicate is objectively violated, the corpus freeze is void: repair and
refreeze the complete pool, record the superseded artifacts without deleting
them, and wait for a new future pulse. The item may not be replaced in place.
Any second post-split invalidation attempt terminates this treatment hypothesis
without M1; it cannot initiate another selection. No responder-model output may
be generated before this conformance window closes.

The post-split predicates are exactly those enumerated in the signed
pre-selection conformance manifest: schema/hash/canonicalization validity,
§4.3 stratum and provenance quotas, §5.2 deterministic pair checks, old-item
exact/normalized-overlap rules, and presence/authenticity of required
provenance artifacts. Any reviewer judgment required by §5.2 is final when the
pool is signed; it cannot be reversed after selection unless its recorded
evidence is objectively missing or false.

The default solo-project roles are:

| Role | Responsibility |
|---|---|
| context-isolated authoring agent | fills precommitted candidate strata without the v1.2 item-level failure ledger |
| pool reviewer agent | reviews every candidate before seed; it is not called an independent human annotator |
| owner / seal custodian | freezes hashes, retains unredacted selected fixtures, and signs incidents |
| frozen evaluator | executes only the committed schema, metrics, gates, and aggregate export |
| process-sealed runner | withholds holdout item traces from the ordinary development view during execution |
| owner / aggregate release authority | releases visible item-level results, holdout aggregates, and raw-artifact hashes |

The selected holdout is process-sealed: the unredacted artifact stays outside
the public Git tree, public manifests expose only aggregate hashes/counts, and
ordinary run output contains no holdout IDs or item-level traces. The owner
ultimately controls the repository, credentials, and sealed artifact, so this
is not cryptographic or organizational independence. The project explicitly
claims no IAA, no independent annotation, no guarantee that the implementation
agent is technically unable to read fixtures, no construct-validity guarantee,
and no population or real-traffic representativeness.

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

- A transport/provider failure that returns no syntactically complete model
  response exhausts the frozen retry policy, then remains a missing cell and
  makes the run `INCONCLUSIVE_API_FAILURE`. It is never entered in a G0
  denominator or converted into a wrong answer.
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

A `complete replicate` contains all 24 selected scenarios × three policies ×
every mandatory gate/counterfactual world defined in §5. Every cell must reach
either a valid response or a terminal wrong-output result. A provider failure
after the frozen retry policy makes the current replicate incomplete and
invokes §9.1. Before the replicate's first request, the complete
scenario/policy/world request order is generated, committed, and hashed.

Official resume rules:

- resume begins only with the replicate after the last complete checkpoint;
- every cell from a partial replicate is void and may not be merged with a
  resumed replicate;
- cell-level selective retry is prohibited;
- the checkpoint is process-sealed and public output contains only its hash,
  completed-replicate count, and aggregate status;
- a resume manifest records interruption reason/time, provider and exact model
  version, request IDs, frozen-order hash, and checkpoint hash;
- any provider/model drift is handled by the §9.1 termination matrix, not by
  silently resuming under a different manifest.

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
  detected under the frozen lexicon in B-full context;
- the same paired/mechanism condition holds in the required replicate count.

Reason codes remain:

- `GO_PATH_A`;
- `GO_PATH_B`;
- `STOP_COMMON_FLOOR`;
- `STOP_NO_HEADROOM_PATH`;
- `INCONCLUSIVE_API_FAILURE`;
- `INCONCLUSIVE_MODEL_VARIANCE`.

No threshold may be relaxed after output. Gate calculations use only the
precommitted gate world for the same scenario, policy, and replicate.
Counter-world cells are mandatory diagnostics but never enlarge a G0
denominator. An official complete result other than `GO_PATH_A` or
`GO_PATH_B` terminates this treatment hypothesis under §0.2.

### 9.1 Termination and incident matrix

| Condition | Required disposition |
|---|---|
| `STOP_COMMON_FLOOR` or `STOP_NO_HEADROOM_PATH` | Terminate the treatment hypothesis immediately; no M1 and no rerun. |
| Five-run `INCONCLUSIVE_MODEL_VARIANCE` | Terminate; do not change model, temperature, prompt, ontology, normalizer, corpus, split, or gate. |
| `INCONCLUSIVE_API_FAILURE` with contemporaneous provider status or failed-request evidence independently establishing the outage | Replay once from the last complete checkpoint under the byte-identical manifest. A second API failure terminates without M1. |
| Transport/provider failure after frozen retries but without the required external outage evidence | Keep the cell missing and terminate without replay or M1; never score it as model wrong output. |
| A syntactically complete model response that violates the frozen wrapper/enum/schema contract | Score as model wrong output; it is not an API failure or infrastructure repair. |
| Frozen implementation demonstrably differs from the signed protocol | Void every output. Permit at most one conformance repair that changes no protocol, fixture, gold, model, prompt, ontology, normalizer semantics, executor semantics, split, or gate; then rerun from the beginning. |
| Any requested semantic or construct change after the first official request | Reject and terminate this hypothesis; it cannot be relabeled a conformance repair. |

The owner and adversarial reviewer sign the protocol freeze and any incident
classification. The owner signs the final GO/termination decision. After the
first official request, model, prompt, temperature, ontology, normalizer,
executor, candidate pool, selection, split, gate-world assignment, and gates
are immutable.

---

## 10. M0 deliverables

Before execution:

- adversarially reviewed and frozen v1.3 protocol;
- frozen action ontology, deterministic executor, and exact-fence normalizer;
- frozen action-pair/situation/template/provenance strata;
- complete candidate registry;
- static counterfactual dependency and symmetry report;
- new 24-item corpus and split manifest;
- sealed new holdout artifact;
- B-summary fidelity/conformance evidence;
- model/prompt/run manifest, gate-world assignment, and request-order hashes.

After execution:

- sealed raw artifact and public hash;
- visible item-level and holdout aggregate report;
- count-first S1–S4 tables separating automatic §6 labels from qualitative
  audit;
- mandatory paired-world/action-prior diagnostics excluded from G0;
- official signed G0 decision;
- explicit termination statement if G0 does not GO.

No M1 code exists before the signed G0 decision.

---

## 11. Required pre-run authorization tests

The protocol is frozen, but candidate-pool sealing and responder-model
execution are not authorized until deterministic tests demonstrate:

1. exact bare JSON is accepted;
2. one exact complete outer `json` fence is accepted;
3. nested fences, untyped fences, prose, extra keys, invalid enums, and multiple
   actions are rejected;
4. the schema contains only one frozen, executable `response_action`;
5. every required-memory candidate changes gold under its counterfactual memory
   change while keeping the probe byte-identical, and both worlds enter each
   complete replicate;
6. every no-memory candidate keeps
   `USE_UNPERSONALIZED_DEFAULT` invariant across two executed irrelevant-memory
   variants;
7. every deletion candidate changes from a personalized pre-delete action to
   `USE_UNPERSONALIZED_DEFAULT` post-delete, and both states are executed;
8. ontology values are frozen and no fixture introduces another value;
9. action-pair, gate-gold, situation, and template quotas satisfy §4.3;
10. category and split counts match §7.1;
11. deterministic selection, split, and gate-world assignment reproduce the
    committed manifest from pinned beacon bytes;
12. each required-memory pair has a complete symmetry certificate and named
    reviewer disposition;
13. automatic rendering labels use only the frozen lexicon and do not convert
    non-detection into semantic absence;
14. old v1.2 item hashes are absent from the new corpus;
15. a whole-pool content-overlap review finds no renamed/lightly paraphrased
    v1.2 item or duplicate underlying event occupying multiple slots;
16. reports contain no holdout IDs or item-level traces;
17. a complete replicate contains all policies and gate/counter worlds; partial
    replicates cannot be resumed or merged;
18. G0 synthetic tests still reject disjoint Path B errors and unstable
    comparators and ignore counter-world cells in gate denominators;
19. termination-matrix tests reject selective retry, semantic repair, model
    replacement, and M1 after any terminal result.
20. post-split invalidation fails for every reason not named in the signed
    conformance manifest, and a second invalidation attempt terminates;
21. no-response provider failures remain missing under both evidenced and
    unevidenced outage paths, while only complete schema-invalid responses are
    scored wrong.

---

## 12. Interpretation boundaries

v1.3 may support:

- a faithful baseline has stable downstream action-selection failures under a
  paired memory-dependent task;
- an applicability or compression opportunity passes a pre-registered path;
- a deleted claim remained detected under the frozen lexicon and changed a
  paired downstream action;
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
- structured action selection alone establishes natural-language quality,
  end-user benefit, or reduced real-world harmful-memory behavior;
- process sealing provides independent annotation, technical access isolation,
  IAA, construct validity, or real-traffic representativeness.

---

## 13. Freeze checklist

The owner and adversarial reviewer must answer all items before `FREEZE`:

1. Does the downstream action task require memory rather than self-reported
   applicability or normative option selection?
2. Does every required-memory item pass a byte-identical-probe counterfactual
   dependency check, execute both worlds, and have a symmetry certificate?
3. Can B-none performance influence fixture selection? It must not.
4. Is the exact-fence normalizer the only transport repair?
5. Are gold applicability, frozen-lexicon detection, active assertion,
   historical mention, ambiguity, and qualitative audit reported separately?
6. Are all old v1.2 items excluded?
7. Are action-pair/template/situation strata and gate-world gold exactly
   balanced before output?
8. Are category/split counts, 24 gate-world denominators, and G0 thresholds
   unchanged?
9. Was the whole candidate pool reviewed and frozen before the future seed and
   before model output?
10. Is the new holdout genuinely new, process-sealed, and free from post-split
    replacement?
11. Are absent human IAA, true independence, and technical isolation disclosed?
12. Are complete-replicate, resume, and termination rules executable without
    selective cell retry?
13. Is the first valid pool/seed/selection binding except for one objectively
    evidenced violation of a pre-signed conformance predicate?
14. Do no-response provider failures always remain missing, with evidence
    affecting replay permission rather than scoring?
15. Is v1.3 explicitly the final construct repair?
16. Does every terminal non-GO or five-run variance result stop the hypothesis?
17. Is M1 still absent?

**Current freeze decision:** `FREEZE`

**Closure:** F1–F8 `CLOSED` on 2026-07-26.

**Next action:** implement only the frozen deterministic conformance layer and
candidate registry required by §§4–8 and §11. Do not call the responder model,
seal the candidate pool, or begin the official run until every §11 test passes.
