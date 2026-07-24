# InnerFlow Memory Reliability & Failure Localization

## Execution-Ready Evaluation Design v1.2

**Status:** frozen

**Freeze decision:** `FREEZE` — independent closure review marked F1–F9 `CLOSED`; owner confirmed

**Date:** 2026-07-24  
**Code reference:** `origin/main` at `8a8aa69380fa873a9b803802751271d7e539a8ce` (PR #84)  
**Frozen positioning:** **InnerFlow — Memory Reliability and Failure Localization for Stateful AI Agents**

This document defines M0 through M4. It is a frozen design contract, not an implementation plan that may be freely expanded. The adversarial review and closure review are complete. M0 may begin after this document is merged; changes to frozen fixtures, gates, metric definitions, or fairness rules require a new protocol version.

### v1.2 changelog

- **Independent review F1/F2 — accept and patch:** require same-scenario Path B evidence and comparator stability; prevent G1 wins from memory suppression, no-memory-only repairs, forced-choice guessing, or the known exact-match bug.
- **Independent review F3/F4 — accept and patch:** use per-scenario context caps and specify compression persistence, reflection inputs, Wiki allowlists, role rendering, scoring, and quiescent-execution limits.
- **Independent review F5/F6 — accept and patch:** limit localization to a joint S1+S2 block, define S4 only under oracle-clean context, freeze a candidate registry, and make derived-deletion grading deterministic.
- **Independent review F7/F9 — accept and patch:** freeze reproducible external-slice selection and formal gate/replicate/failure semantics.
- **Independent review F8 — declared limitation:** retain the split-specific G0 floors but require an exact STOP reason that does not claim the problem was absent.

### v1.1 changelog

- **F1 — accept and patch:** G0 now has parallel compression-headroom and applicability-headroom GO paths.
- **F2 — accept and patch:** G1 now requires a repair outside deletion/context-exception and separates non-trivial stage repairs from missing-mechanism wins.
- **F3 — accept and patch:** deletion fixtures must include information already absorbed into a summary or Wiki field.
- **F4 — accept and patch:** reports identify the faithful baseline’s known exact-match bug; M1 may run one isolated bug-fixed secondary control.
- **Reviewer default — accept and patch:** no independent human reviewer is currently assigned, so v1.1 uses the term `sealed holdout` and claims neither IAA nor annotator independence.

---

## 1. Product requirements: the three résumé bullets

The following three bullets are the product requirements. A milestone or feature that cannot be traced to one of them is out of scope.

**RB1 — lifecycle correctness**

> Built a conflict-aware memory lifecycle for a longitudinal AI companion, preserving context-specific exceptions while superseding stale facts with source-level provenance.

**RB2 — state consistency under concurrency**

> Reproduced and fixed a Redis lost-update race in asynchronous memory compression using atomic Lua prefix replacement, with deterministic concurrency and real-Redis integration tests.

**RB3 — evaluation and failure localization**

> Designed an eval harness comparing full-context, faithful summary-compression, external memory, and applicability-aware policies on required recall, harmful-memory exposure, and answer-level failures across frozen and external benchmark slices.

RB2 is already supported by the merged concurrency fix and tests. M0–M4 must make RB1 and RB3 defensible while keeping RB2 load-bearing as part of the overall “memory reliability” story.

### 1.1 Milestone-to-requirement map

| Milestone | Required outcome | Résumé requirement served |
|---|---|---|
| M0 | Establish, with a faithful baseline, whether summary-based memory creates measurable answer failures and enough headroom to justify treatment work | RB3 |
| M1 | Build and evaluate a conflict/applicability-aware lifecycle; localize which memory stage is the bottleneck | RB1, RB3 |
| M2 | Test generalization on a larger frozen set, one external benchmark slice, and one real external memory system | RB1, RB3 |
| M3 | Integrate only validated invariants into the real Java/Redis system and preserve concurrency guarantees | RB1, RB2 |
| M4 | Produce a reproducible evidence pack and minimal stage-trace inspector for review/demo | RB1, RB2, RB3 |

### 1.2 Explicit non-goals

No voice interface, therapist imitation, FHIR, general agent orchestration, generic RAG platform, token-cost optimization, MMR/knapsack experimentation, broad observability rewrite, or polished consumer UI. M4 may expose existing evaluation traces, but it is not a dashboard product.

---

## 2. Claim boundary and differentiation

InnerFlow does **not** claim to invent long-term-memory benchmarking, preference-evolution taxonomies, or end-to-end memory QA. LongMemEval, PersonaMem, BenchPreS, and RPEval already cover important parts of memory update, abstention, evolving personalization, and selective preference use.

The intended contribution is narrower:

> For the same stateful-agent scenario, capture and score the intermediate artifacts at memory formation, lifecycle resolution, context selection, and final use; then use controlled oracle repairs to identify which layer would help most.

This is **bottleneck localization**, not causal attribution. Oracle interventions are conditional and order-dependent. The project must never report “layer X caused Y% of errors” or allocate an “error share” across layers. It may report:

- the actual error count at each stage;
- the number of failed scenarios repaired by each pre-registered intervention;
- which intervention produced the largest paired improvement under the declared intervention order.

This distinction is important because replacing an upstream state can change every downstream input. Such a result identifies the best repair target in this pipeline; it does not estimate a clean causal effect.

---

## 3. System boundary and stage model

Each scenario is processed through four observable stages:

| Stage | Name | Input | Required trace artifact |
|---|---|---|---|
| S1 | Memory formation | timestamped conversation events | candidate memories with stable IDs and source-event IDs |
| S2 | Lifecycle resolution | candidates plus prior memory state | effective, superseded, scoped, tombstoned, and historical memories with provenance |
| S3 | Applicability selection | resolved memory state plus current probe | ordered injected memory IDs, rejected IDs, and reason codes |
| S4 | Memory use | current probe plus selected context | final answer and exact rendered memory context |

The faithful B-summary baseline may not expose all structured fields internally. Its adapter must still emit an auditable trace: source events entering each summary, summary text, retained raw events, Wiki fields, and final rendered context. Missing structure is a property of the baseline, not a reason to fabricate it.

### 3.1 Gold objects

Gold data is held by the evaluator and is never passed into a policy:

- `effective_memory_ids`: memories true/applicable as of the probe;
- `historical_memory_ids`: superseded memories that remain valid only as history;
- `required_context_ids`: evidence that must enter the probe context;
- `harmful_context_ids`: stale, deleted, irrelevant, or out-of-scope evidence that must not be applied;
- `expected_resolution`: `supersede`, `keep_scoped`, `tombstone`, or `no_conflict`;
- deterministic answer rubric.

An ID names a source-grounded memory, not free-form text. Every derived claim must retain at least one source-event ID.

---

## 4. Faithful baseline contract: B-summary

M0 must not use a deliberately weak LWW toy. `B-summary` means a behaviorally faithful replica of the current InnerFlow summary-plus-Wiki path at the pinned reference commit.

### 4.1 Short-memory behavior that must be preserved

The reference behavior is defined by `MemoryService.addMessage` and `MemoryCompressionService`:

1. Add every user and assistant message to the short-memory sequence.
2. The configured threshold is **10 rounds**, implemented as **20 messages**.
3. At 20 or more messages, take a stable history snapshot and trigger compression.
4. Keep **4 recent rounds = 8 raw messages**.
5. Send every older message to the current summary prompt. The prompt:
   - frames the input as a therapeutic conversation log;
   - requests a summary under 150 words;
   - asks for emotional arc, key disclosures, what helped, and open threads;
   - requests past tense, third person, specific language, and summary text only.
6. Write one `system` message whose content begins `[Conversation summary] `, followed by the generated summary.
7. Retain the current history suffix beginning immediately after the summarized prefix. The production Lua prefix check and splice must be represented semantically: a compatible window-period append is retained; an incompatible prefix rewrite skips the compression write.
8. A later compression may summarize an earlier system summary again. It must not silently reset to the original raw dialogue.
9. Compression is considered settled before a semantic-evaluation probe. M0 does not inject scheduling races; those are already covered by RB2’s deterministic concurrency and real-Redis tests.
10. After a successful compression write, persist the generated text into `UserMemory.conversationSummary` and increment `compressionCount` by one. A skipped or failed write performs neither side effect.

Conformance cases must cover 19 messages/no compression, 20 messages/compression, the 8-message retained suffix, recursive compression, a compatible appended suffix, and an incompatible prefix rewrite. B-summary cannot be run on evaluation fixtures until all conformance cases pass.

### 4.2 Session-end Wiki behavior that must be preserved

The current application also compiles long memory when a session ends, then clears short memory. Omitting this would weaken the baseline. B-summary therefore includes:

1. Use the production first-extract predicate exactly: first extract is selected only when `emotionPattern`, `coreStruggles`, and `triggers` are all null. Other populated fields do not change that predicate.
2. On first extract, process the settled short history and populate the same conceptual Wiki fields: emotion pattern, core struggles, effective coping, language style, trigger updates, progress note, and change log.
3. On later sessions, provide the new settled short history plus only the production merge-visible Wiki subset: emotion pattern, core struggles, effective coping, language style, all triggers, and all progress-note text. Reflection, conflicts, change log, conversation summary, archived triggers, corrections, persona, and counters are not merge-prompt inputs.
4. Preserve the current trigger semantics:
   - `new` performs semantic deduplication when available, otherwise appends;
   - `increment` and `remove` use exact case-insensitive observation text matching;
   - score is `min(1, count/5) × exp(-days/90)`, rounded to two decimals; `confidence == "confirmed"` returns exactly `1.0`.
5. Preserve the current text-field update contract: meaningful new values replace their field; `null` means no change.
6. Store detected conflicts, save the Wiki, and immediately regenerate reflection.
7. Reflection receives only emotion pattern, core struggles, effective coping, and the persisted `conversationSummary`; it does not receive triggers, progress notes, language style, conflicts, or the other stored fields. Its production prompt requests a clinical-observation insight under 200 words focused on trends, triggers, progress, and what helps, with insight text only. The generated reflection is saved back to long memory.
8. Do not retroactively add stronger scope or tombstone semantics that the reference implementation lacks.
9. Clear short memory after the Wiki update and reflection call.

The exact matching in `increment`/`remove` is a known faithful-baseline defect: a paraphrased trigger can silently miss the intended update or removal. Baseline traces and reports must tag cases exposed to this defect. It must not be repaired inside B-summary.

### 4.3 Probe-context behavior that must be preserved

At a probe, B-summary renders:

- core struggles and emotion pattern;
- active triggers with score at least `0.3`, sorted by score;
- effective coping;
- the latest three progress notes;
- language style and reflection;
- the last ten short-memory messages.

It does not inject conflicts, change log, conversation summary, compression count, archived triggers, user corrections, or persona. The current method does not condition long-memory selection on the current user message. Every role other than exact `user` is rendered with the label `AI`; a system summary in the short-memory tail therefore renders exactly as `AI: [Conversation summary] ...`.

M0 deliberately evaluates **settled/quiescent executions**: compression finishes before session-end compilation or a semantic probe. Production does not await the asynchronous compressor, so immediate disconnect can let Wiki compilation read uncompressed history and then make the later Lua write skip after short-memory deletion. The fidelity claim is limited to settled executions; unsynchronized session-close behavior remains an operational limitation rather than a semantic-eval variable.

### 4.4 Fidelity evidence

M0’s report must include a “baseline fidelity table” linking each contract item above to:

- the pinned Java source location;
- one conformance fixture;
- the observed trace field proving the behavior.

If the Python replica and pinned Java behavior disagree, Java at the pinned commit is authoritative. The replica is corrected before any headline run; fixture/gold data is not changed to accommodate it.

---

## 5. Scenario corpus: frozen pilot

### 5.1 Size and category distribution

M0/M1 use exactly **24 scenarios**. Their distribution is frozen before implementation:

| Category | Visible development | Sealed holdout | Total |
|---|---:|---:|---:|
| correction | 4 | 2 | 6 |
| supersession | 3 | 1 | 4 |
| context-exception | 4 | 2 | 6 |
| no-memory | 3 | 1 | 4 |
| deletion | 2 | 2 | 4 |
| **Total** | **16** | **8** | **24** |

Definitions:

- **correction:** the user explicitly says an earlier stored statement was wrong.
- **supersession:** a once-valid fact or preference changes over time without framing the old value as an original error.
- **context-exception:** a general memory remains valid, but a narrower situation has a different rule.
- **no-memory:** stored memories exist but are not applicable to the current probe.
- **deletion:** the user explicitly asks that a memory no longer be used; successful behavior requires a non-retrievable tombstone or equivalent. At least `1/4` pilot deletion scenarios must target content already absorbed into an earlier summary or Wiki field, so success requires redaction or re-summarization of derived memory rather than removal of only a recent raw event. At M2, the same minimum ratio is `3/12`.

No category may be added, removed, or rebalanced after the first baseline output is observed. If a fixture is invalid, its replacement must have the same category and provenance tier, and the replacement event must be recorded before rerunning.

### 5.2 External-taxonomy anchors

The categories are not presented as new taxonomy. Every scenario must cite at least one nearest external construct:

| InnerFlow category | External anchor |
|---|---|
| correction | LongMemEval `Knowledge Updates`; PersonaMem evolving profiles/preferences |
| supersession | LongMemEval `Knowledge Updates` and temporal reasoning; PersonaMem preference evolution |
| context-exception | BenchPreS appropriate application/suppression; RPEval rational preference utilization |
| no-memory | LongMemEval `Abstention`; RPEval irrelevant-memory interference/irrational personalization |
| deletion | nearest anchors are knowledge update plus abstention, but no one-to-one construct is claimed |

The deletion qualification is deliberate. Pretending all five local categories map exactly onto the four external benchmarks would make the design less honest. Deletion remains because it is a product-level memory invariant; it must be labeled as a coverage extension, not externally validated taxonomy.

### 5.3 Provenance and anti-cherry-pick requirements

Scenario realism is the project’s largest validity risk. Every fixture must contain:

- `origin_type`: `adapted_external`, `pattern_authored`, or `consented_anonymized`;
- source benchmark/paper, public item ID when available, and retrieval date;
- a transformation log explaining every adaptation;
- author identifier and reviewer identifier (`none` until a human reviewer is explicitly assigned);
- the reason the probe has one unambiguous gold outcome;
- a taxonomy-anchor field;
- a content hash covering source events, probe, and gold;
- a leakage note stating whether the scenario was used in prompt/policy development.

At least **12/24** scenarios must be traceable adaptations of public benchmark items or public benchmark construction patterns. “Inspired by common conversations” is insufficient provenance. Real conversations may be used only with explicit consent and irreversible de-identification; they are not required.

Before any baseline run, freeze a **candidate registry** containing every considered scenario ID, including rejected and replaced candidates. For each candidate, record inclusion status, rejection/replacement reason, category, origin type, provenance tier, and content hash. A rejected candidate is never silently deleted from the registry. Items derived only from a benchmark construction pattern are reported separately from item-level external adaptations and cannot be described as adapted benchmark items.

The scenario author cannot inspect treatment output before gold and rubrics are hashed. Ambiguous scenarios are rejected, not repaired after seeing model behavior.

Gold is produced by one primary annotator using a written decision table. No independent human reviewer is assigned in v1.2. Therefore, no independent second-pass annotation or disagreement statistic is claimed.

### 5.4 Sealed holdout subset

The selected v1.2 default is **sealed holdout**, because no independent human reviewer has been named. The 8 holdout scenarios are a process control, not a marketing label:

1. The primary annotator authors or selects the source packs and gold before treatment implementation, then seals them with content hashes.
2. During treatment work, the implementer receives only schema validation and aggregate gate results for these cases; no per-item gold, grader failures, or answer traces are reopened through G1.
3. Source, gold, and rubric hashes are committed before B-summary is run.
4. The evaluator releases only G0/G1 aggregate counts until G1 is irrevocably recorded.
5. After G1, the seal may be opened for error analysis, but the same items can never again be called unseen holdout data.

All reports must state explicitly: **no inter-annotator agreement was measured and there is no true annotator independence**. If a named independent human reviewer is assigned before freeze, that fact and the exact review responsibility require a documented pre-freeze patch; until then, `blind` is prohibited terminology.

Sealing controls post-seal leakage only. It does not establish construct validity, annotator independence, or representativeness of real user traffic; all three remain declared limitations.

### 5.5 Fixture contract

Each scenario contains:

- stable scenario/category/split IDs;
- timestamped sessions and ordered source events with stable event IDs;
- one probe;
- gold lifecycle state and source provenance;
- required and harmful context IDs;
- a deterministic answer rubric;
- expected stage resolutions;
- provenance and sealing metadata.

For the deletion category, fixtures must identify every raw and derived location containing the target content. At least `1/4` pilot deletion fixtures—and `3/12` at M2—must place it in a prior summary or Wiki field. Each such fixture freezes a location-level target, normalization rules, and forbidden slot/value variants that cover allowed paraphrases. Gold success requires the raw memory to be tombstoned and all active derived representations to be redacted or re-summarized so the content is no longer retrievable or injectable; deleting only the latest raw event fails. If a free-text derived claim cannot be mapped to the frozen target deterministically, the fixture is invalid and must be replaced—under the candidate-registry replacement rules—before any baseline output is observed.

M0/M1 probes must support deterministic grading: forced choice, bounded slot filling, or explicit required/forbidden assertions. A free-form answer that needs subjective interpretation does not enter the 24-scenario pilot.

---

## 6. Model and sampling protocol

Temperature is not set to zero by default. The pilot uses controlled but realistic stochastic generation:

- summary/Wiki formation calls: `temperature = 0.2`;
- final conversational response: `temperature = 0.4`;
- **3 independent replicates** for every scenario-policy pair;
- identical model ID, prompt template, context budget, and generation limits for the same stage across policies;
- fixed seed when the provider supports it, while still treating the API as nondeterministic.

These values are frozen because formation should be conservative while the response remains conversational. They are not tuned against fixture outcomes. If a chosen model does not expose temperature, the manifest records “unsupported”; three repeats remain mandatory.

The run manifest records provider, exact model ID/version, run date, parameters, prompt hashes, dependency lock hash, and request IDs. All raw inputs and outputs are retained. Policy order is randomized per replicate to reduce time/provider drift.

The scenario, not an individual sample, is the unit of analysis. A scenario-level result is the majority label across three repeats. Reports also show the three per-replicate counts and their min–max range. They do not pool 72 generations as 72 independent scenarios.

### 6.1 Formal replicate and gate semantics

The evaluator uses these definitions everywhere:

1. A policy/scenario/replicate cell is labeled `correct` or `wrong` only after deterministic grading.
2. The manifest’s fixed retry policy applies to provider failures. If any cell is still unavailable after those retries, the run is `INCONCLUSIVE_API_FAILURE`; the cell is never removed from a denominator and the gate is not evaluated.
3. With three complete replicates, scenario majority is the label appearing in at least `2/3`; a scenario is non-unanimous when all three labels are not identical.
4. A replicate-level aggregate condition means the stated count is independently recomputed within each complete replicate column. “At least `2/3` replicates” means that aggregate condition passes in at least two columns.
5. A paired repair in one replicate is the same scenario being wrong under baseline and correct under treatment in that replicate. A scenario-majority repair requires baseline majority wrong and treatment majority correct for that same scenario.
6. A paired Path A or Path B case is likewise an intersection on the same scenario, never the sum of disjoint policy error sets.
7. If a selected policy exceeds the `6/24` three-run non-unanimous ceiling, run two additional complete replicates for every policy/scenario in that gate. Recompute scenario majority as at least `3/5`, replace `2/3` replicate conditions with `3/5`, and call a scenario unstable when neither label reaches `4/5`. If more than `6/24` remain unstable, the result is `INCONCLUSIVE_MODEL_VARIANCE`.
8. Gate output uses one reason code: `GO_PATH_A`, `GO_PATH_B`, `STOP_COMMON_FLOOR`, `STOP_NO_HEADROOM_PATH`, `STOP_G1_EFFECT`, `INCONCLUSIVE_API_FAILURE`, or `INCONCLUSIVE_MODEL_VARIANCE`. The report prints every failed predicate, not only the first.

---

## 7. Metrics and grading

Small-sample results are always written as counts such as `5/16`, never as `31.25%`, `0.3125`, or an unsupported “significant improvement.”

### 7.1 Stage metrics

| Stage | Primary count | Failure definition |
|---|---|---|
| S1 formation | source-supported memories / gold memories | required fact missing, invented claim, or provenance missing |
| S2 lifecycle | correct resolutions / gold conflicts | stale fact remains effective, scoped exception flattened, or deleted memory remains active |
| S3 selection | required-context-complete scenarios / N; harmful-exposure scenarios / N | any required ID absent; any harmful ID injected |
| S4 use | correct answers / N; harmful personalization errors / N | deterministic answer rubric fails or harmful memory changes the answer |

Additional guards:

- unnecessary-personalization count on no-memory scenarios;
- deletion-leak count;
- new-regression count: B-summary correct and aware wrong;
- sample-instability count: the three repeats do not share one unanimous label.
- known-exact-match-bug exposure count: scenarios in which B-summary’s paraphrased `increment`/`remove` misses the intended trigger.

Raw item-level denominators are retained, but the headline pilot results remain scenario counts.

Every S2 result table must identify whether a failure was exposed to the known exact-match defect. Such failures remain valid faithful-baseline failures, but they are reported separately from failures observed when exact matching is not involved.

### 7.2 Deterministic answer grading

Each rubric declares:

- the answer field or choice to inspect;
- required values;
- forbidden values;
- normalization rules fixed before execution;
- invalid-output behavior.

An invalid or non-conforming output is wrong. A response cannot pass by mentioning both mutually exclusive options. No LLM judge is used in M0 or M1.

---

## 8. Gates

### 8.1 G0 — does the problem exist, and is there headroom?

**Timing:** end of M0. The aware treatment does not exist yet.

Systems in G0:

- `B-summary`: faithful baseline and primary subject;
- `B-full`: full source history, a diagnostic ceiling rather than a deployable budget-matched competitor;
- `B-none`: no persistent memory, a control for probes solvable without memory.

B-full and B-none are trivial context switches in the harness, not additional memory algorithms. M0’s only implemented memory system is the faithful B-summary replica.

G0 prerequisites:

- all B-summary conformance cases pass;
- all 24 fixture schemas, provenance records, rubrics, and hashes are frozen;
- deterministic graders pass hand-constructed positive and negative tests;
- no sealed-holdout item-level trace has been disclosed.

**G0 common evidence floor and anti-cheating conditions:**

1. B-summary produces answer-level errors on at least `5/16` visible scenarios in at least `2/3` replicates.
2. B-summary produces answer-level errors on at least `2/8` sealed-holdout scenarios in at least `2/3` replicates.
3. The scenario-level B-summary majority failures supporting conditions 1–2 span at least two categories and are not exclusively deletion cases.
4. Every policy used by the selected path has no more than `6/24` non-unanimous scenarios: B-summary and B-full for Path A; B-summary, B-full, and B-none for Path B. Exceeding the ceiling invokes the five-run procedure in §6.1.

After the common conditions pass, **either** of these pre-registered headroom paths is sufficient:

- **Path A — compression headroom:** across the 24 scenario-level majority labels, at least `4/24` are same-scenario paired cases where B-summary is wrong and B-full is correct. The same paired contrast reaches at least `4/24` within at least `2/3` individual replicates.
- **Path B — applicability/use headroom:** supporting cases come only from the fixed 10-scenario union of context-exception (`6`) and no-memory (`4`) where B-summary and B-full are both wrong on the same scenario-majority label. At least `3/10` such paired cases are required, including at least one from each category, and the same paired condition reaches `3/10` within at least `2/3` individual replicates. A supporting no-memory case additionally requires B-none to be correct. A supporting context-exception case additionally requires the narrower applicable gold evidence to be present in B-full’s rendered context.

Therefore, **G0 = GO** when the common conditions and Path A **or** Path B pass. Path A supports a compression-specific opportunity; Path B supports an applicability/use opportunity even when full context is not better. The selected path and all counts must be reported. Neither threshold may be relaxed after outputs are observed.

Failure to pass G0 means treatment work stops. Thresholds, category mix, or rubrics are not loosened. The report must distinguish `STOP_COMMON_FLOOR` from “no failures observed.” For example, stable totals with visible `4/16` and holdout `3/8` are reported as: **“STOP — stable failures were observed, but the pre-registered visible-development floor was not met.”** They must not be described as evidence that the memory problem is absent.

### 8.2 G1 — does the treatment help?

**Timing:** end of M1, after the aware lifecycle, prompts, reason codes, and policy parameters are frozen on the 16 visible scenarios.

G1 compares applicability-aware treatment against B-summary under the same model and response protocol.

Before treatment work begins, define a per-scenario matched memory-context cap `C_s` as the maximum tokenizer count produced by B-summary for that scenario across the three frozen M0 replicates. The sealed evaluator enforces its holdout caps internally without disclosing item traces. The tokenizer and cap derivation rule are recorded in the M1 manifest. On each scenario, the aware policy may emit fewer tokens but never more than that scenario’s `C_s`; deterministic truncation is applied before the responder call. B-full remains explicitly exempt as a diagnostic ceiling.

**G1 = GO only if all conditions hold on scenario-level majority labels:**

1. The treatment repairs at least `4` B-summary failures across the 24 cases.
2. At least `1` repaired case is in the sealed holdout 8, and repairs span at least two categories.
3. At least `1` repaired failure is from correction or supersession. That case either is not exposed to the known exact-match bug or remains repaired under the pre-declared bug-fixed secondary control. No-memory alone cannot satisfy this condition.
4. At least `2` of the repaired failures are required-memory scenarios, and treatment’s rendered context is complete for all gold required IDs in each of those repaired cases.
5. The treatment introduces no more than `1/24` new answer regression.
6. Required-context completeness is lower than B-summary on no more than `1/24` scenarios.
7. Harmful-memory exposure is reduced on at least `3` scenarios and does not increase in any category.
8. Treatment has no more than `6/24` non-unanimous scenarios; exceeding the ceiling invokes the five-run procedure in §6.1.
9. Conditions 1, 5, and 6 also hold in at least `2/3` replicate-level comparisons.

G1 is not a claim of generalization or external superiority. It is permission to pay the cost of M2.

---

## 9. Milestones

## M0 — faithful-baseline headroom pilot

**Question:** Does the current summary-plus-Wiki behavior create repeatable, answer-level memory failures, and is the gap large enough to justify a new lifecycle?

### Work allowed

- fixture schema, provenance manifest, sealed evaluator, deterministic graders;
- B-summary behavioral replica and Java conformance fixtures;
- B-full and B-none diagnostic controls;
- trace capture and count-based report generation.

### Execution order

1. Freeze this v1.2 after adversarial review.
2. Author and hash all 24 scenarios and rubrics at the frozen distribution.
3. Seal the 8 holdout items and record that no independent reviewer is assigned.
4. Implement and pass B-summary conformance before running any scenario.
5. Freeze model/prompt/run manifests.
6. Run B-summary, B-full, and B-none for three randomized replicates.
7. Release visible item-level results and sealed aggregate results.
8. Apply G0 without changing thresholds.

### Required artifacts

- corpus/provenance manifest and hashes;
- baseline fidelity table;
- conformance results;
- raw run manifest and immutable outputs;
- `RESULTS_M0.md` with per-replicate and majority counts;
- signed G0 decision: `GO`, `STOP`, or `INCONCLUSIVE`.

No aware lifecycle, external memory system, or external benchmark data is implemented in M0.

## M1 — aware lifecycle and bottleneck localization

**Question:** Can explicit lifecycle and applicability semantics reduce harmful memory use without losing required recall, and which stage is the best repair target?

### Treatment contract

The treatment must:

- preserve stable memory IDs and source-event provenance;
- supersede explicit corrections and temporal replacements while retaining historical auditability;
- represent a general rule and a context-specific exception simultaneously;
- tombstone explicit deletions so they cannot be selected;
- decide applicability using current probe context;
- emit selection reason codes.

No embedding/knapsack/MMR tuning is part of M1. The simplest policy satisfying the contract is preferred.

### Bottleneck-localization protocol

For every failed visible scenario, run the following pre-registered repair ladder:

| Run | Replaced with oracle | Still real |
|---|---|---|
| L0 | nothing | S1–S4 |
| L1 | gold formed/resolved memory state | S3 selection and S4 response |
| L2 | gold state and gold required/harmful selection | S4 response |
| L3 | gold state, gold selection, and deterministic gold answer rendering | none |

Report the paired failures repaired from L0→L1, L1→L2, and the residual failures from L2→L3. L0→L1 can identify only a **joint formation/lifecycle bottleneck (S1+S2)** because both stages change together; separate S1 and S2 counts remain observational metrics, not oracle-isolated effects. L1→L2 identifies selection/applicability opportunity conditional on oracle state. L2→L3 is a residual S4 failure count, not an independent causal effect. The largest lift names the conditional repair target and never receives a causal percentage.

The localization report must separate:

- **missing-mechanism repairs:** wins enabled by adding a mechanism absent from B-summary;
- **non-trivial stage repairs:** wins where the fixture’s pre-registered mechanism inventory says B-summary already had the required mechanism but still failed;
- **oracle-clean S4 failures:** in L2, required evidence is complete, harmful evidence is absent, and the real responder still produces the wrong answer.

Missing/non-trivial classification is frozen per fixture before baseline execution; it is not inferred from category. In particular, no-memory defaults to missing applicability mechanism unless its fixture inventory proves otherwise. For the third row, report both the number of oracle-clean S4 failures and the number repaired by treatment. If it is `0/N`, record a null S4 finding; mere presence of required evidence alongside harmful evidence does not qualify as S4.

M1 may include one **secondary bug-fixed B-summary control** that changes only paraphrased trigger `increment`/`remove` matching to the same semantic-match behavior already used by `new`. It must retain every other B-summary contract, is run only on the pre-declared exact-match-exposed subset, and is not otherwise a G1 participant. The control is mandatory only when G1 condition 3 relies on an exact-match-exposed repair; otherwise it remains optional. If run, report whether the qualifying repair and principal localization findings survive; if legitimately omitted, record that the known-bug confound remains a declared limitation. This control isolates the known implementation defect; it is not a new treatment or milestone.

After visible tuning is frozen, run the sealed evaluator once and apply G1. Opening sealed item-level results before the G1 decision invalidates the holdout claim.

### Required artifacts

- frozen treatment contract and prompt hashes;
- stage traces and reason codes;
- localization repair table with counts;
- `RESULTS_M1.md`;
- signed G1 decision.

## M2 — generalization and external comparison

M2 begins only after G1 = GO. External systems and external benchmark data intentionally do not enter M0/M1.

### Internal expansion

Expand to exactly **80 frozen internal scenarios**, including the original 24:

| Category | Total at M2 |
|---|---:|
| correction | 20 |
| supersession | 16 |
| context-exception | 20 |
| no-memory | 12 |
| deletion | 12 |
| **Total** | **80** |

The same provenance and sealing rules apply. At least 32 of the 56 new items are independently authored or adapted by a non-implementer.

### External benchmark slice

Primary slice: **PersonaMem 32k**, because it directly exercises evolving profiles/preferences and its official repository is MIT-licensed. Before running any InnerFlow policy:

1. pin the dataset revision and license text;
2. define eligible rows as those with a unique non-empty `question_id`, non-empty `question_type`, valid `correct_answer`, non-empty `all_options`, resolvable `shared_context_id`, and non-negative integer `distance_to_ref_in_blocks`;
3. assign fixed distance bins `0–4`, `5–9`, `10–19`, and `20+`, then form non-empty `(question_type, distance_bin)` strata;
4. within each stratum, order items by ascending SHA-256 of `innerflow-personamem-v1:` plus `question_id`;
5. select **40 items** by round-robin over strata sorted lexicographically, taking the next hashed item from each non-empty stratum and skipping exhausted strata until 40 are selected;
6. publish eligible counts, stratum counts, ordered item IDs, and the selection manifest;
7. use the benchmark’s native answers and evaluation contract without rewriting gold.

If PersonaMem access, license, or schema changes make this impossible, the pre-declared fallback is a 40-item LongMemEval slice: `20` eligible Knowledge Updates and `20` eligible Abstention items. Within each task, order by ascending SHA-256 of `innerflow-longmemeval-v1:` plus the public item ID and take the first 20 without replacement. If one task has fewer than 20 eligible items, exhaust it and fill the remainder from the other task’s next hashed items; report the redistribution. The fallback reason and license audit must be recorded **before** running treatment outputs. Results from the external slice are reported separately from the internal 80; denominators are never pooled.

The external slice primarily validates S4 outcome generalization. Unless the public item includes source-level lifecycle/applicability labels, it must not be retrofitted with invented S1–S3 gold. Bottleneck-localization claims remain grounded in the frozen internal corpus.

### Real external memory-system baseline

Primary external system: **Mem0 open source**, selected because it has a local Python path and an Apache-2.0 repository. LangMem (MIT) or Graphiti (Apache-2.0) may replace it only if a documented compatibility blocker is discovered before external results are run.

Because these systems evolve, M2 must freeze:

- repository commit and package version;
- license file and third-party notices;
- dependency lock and container image digest where used;
- storage backend and schema;
- LLM and embedding model IDs;
- all memory prompts/configuration;
- telemetry setting;
- adapter commit and exact ingest/retrieve call sequence.

Primary comparison is **controlled mode**: same responder, probe prompt, and context budget; the external system only controls memory formation and retrieval. If its documented operation requires proprietary/default components, a secondary **native mode** may be reported, clearly separated. No claim may compare controlled InnerFlow with an undisclosed managed-service configuration.

Before execution, the external adapter freezes its retrieval limit and deterministic token-truncation rule under the same cap used for InnerFlow. These values are selected from documentation and visible development cases only; they cannot be tuned on the external 40-item outputs.

### M2 outputs

- results on internal 80, external 40, and each external-memory mode;
- per-category counts and paired regression/repair counts;
- cost/latency as secondary engineering measurements, never the main finding;
- updated localization table;
- limitations covering synthetic scenarios, single-annotator gold, provider drift, and external-system versioning.

## M3 — production integration and reliability

Only M1/M2-validated lifecycle invariants are integrated into the Java system. The integration must preserve:

- asynchronous compression;
- atomic Lua prefix replacement and window-message retention;
- existing deterministic concurrency and true-Redis Lua tests;
- provenance/tombstone/scope semantics validated in M1;
- stage trace IDs required by the evaluator.

Python remains the evaluation harness. Java/Redis/MySQL is the production SUT. No production rewrite into Python or TypeScript is part of this project.

M3 reruns:

- the memory compression unit and real-Redis integration tests;
- the M0 fidelity suite against the Java adapter;
- the frozen 24-scenario M1 set;
- a selected M2 smoke slice whose IDs are declared before integration.

## M4 — evidence pack and minimal inspector

M4 adds no memory policy. It packages evidence:

- one reproducible command per frozen run;
- manifests, raw outputs, and generated Markdown tables;
- a minimal stage-trace inspector showing source → lifecycle state → selected context → answer;
- one failed baseline case and its repaired treatment trace;
- versioned architecture and limitations note;
- final résumé counts copied only from frozen reports.

The inspector may be static HTML or a CLI-generated report. It is complete when a reviewer can verify a headline count back to raw source events without reading implementation code.

---

## 10. Fairness and reproducibility contract

All policy comparisons must obey:

1. Same source-event stream and timestamps.
2. Same responder model, prompt, temperature, limits, and context budget.
3. Same stage-specific model configuration when two policies perform the same stage.
4. Gold never enters the policy.
5. Full-context is labeled a diagnostic ceiling when it exceeds the deployable budget.
6. External systems receive documented, reasonable configuration; they are not intentionally weakened.
7. Failed API calls are reported separately and are not silently retried until correct. A fixed retry policy is part of the manifest.
8. Every prompt, fixture, gold file, policy config, and result file is content-hashed.
9. Cached generations are immutable and keyed by all model/config/prompt inputs plus replicate ID.
10. Changes after a frozen run create a new result version; old outputs remain available.

The existing `v2/src/innerflow_v2/eval` code can contribute schema validation, observation/gold ID isolation, count functions, and dev/locked/challenge conventions. Its old results and deterministic policies are not evidence for this project, and its decimal report rendering must be replaced by count-first reporting for the pilot.

---

## 11. Decision log: requested revisions

All ten requested revisions improve the design and are adopted, with two qualifications:

1. **Taxonomy anchoring:** useful for realism and prior-work grounding, but deletion has no exact one-to-one construct in the four cited benchmarks. The design records the nearest anchors and labels deletion as a product-invariant extension.
2. **External baseline selection:** naming Mem0 now is useful; pinning today’s moving `main` for an M2 run that occurs later would reduce reproducibility. The system choice is frozen now, while the exact commit/version is frozen at M2 entry, before any external output is observed.

No other requested item is weakened or deferred.

### 11.1 v1.1 adversarial-review dispositions

| Finding | Disposition | Written resolution |
|---|---|---|
| F1 — G0 conflates compression failure with use/applicability failure | **accept and patch** | G0 retains the paired B-summary-vs-B-full compression path and adds a parallel, pre-registered B-full applicability path over the fixed context-exception/no-memory subset. Either path may establish headroom after the common anti-cheating conditions pass. |
| F2 — treatment can win only by adding missing scope/tombstone mechanisms | **accept and patch** | G1 now requires at least one repaired correction, supersession, or no-memory case. Localization reports separate missing-mechanism wins, non-trivial stage wins, and correct-memory-present-but-misused S4 cases; a zero S4 count is reported as a null finding. |
| F3 — deletion fixtures may test only easy raw-memory removal | **accept and patch** | At least `1/4` pilot and `3/12` M2 deletion cases target content already absorbed into a summary or Wiki field. Passing requires tombstoning plus redaction/re-summarization of active derived representations. |
| F4 — known exact-match bug can contaminate lifecycle interpretation | **accept and patch** | Faithful B-summary keeps and tags the bug. S2 reports stratify exposed failures, M1 may run one tightly isolated bug-fixed secondary control, and the forbidden-interpretation list blocks attributing those failures to an inherent limitation of summarization. |
| Reviewer identity not operationalized | **accept and patch** | No independent human is currently assigned. v1.1 therefore selects `sealed holdout` as the default term and explicitly disclaims IAA and annotator independence. |

### 11.2 v1.2 independent-review dispositions

| Finding | Disposition | Written resolution |
|---|---|---|
| F1 — disjoint Path B errors and unstable comparators can produce false GO | **accept and patch** | Path B now requires same-scenario B-summary/B-full failures, a B-none or rendered-evidence mechanism check, replicate-level paired support, and the instability guard for every comparator used by the selected path. Path A also gains replicate-level paired support. |
| F2 — G1 can pass through suppression, guessing, no-memory-only wins, or the exact-match bug | **accept and patch** | G1 now requires a correction/supersession repair, at least two required-memory repairs with complete rendered evidence, treatment stability, and survival under the bug-fixed control when its qualifying repair is bug-exposed. |
| F3 — global maximum context cap is not scenario matched | **accept and patch** | Replace global `C` with per-scenario `C_s`, derived from that scenario’s three B-summary runs and enforced internally for holdout cases. |
| F4 — faithful baseline omits persistence/reflection/rendering details | **accept and patch** | Add summary persistence and counter behavior, exact first-extract predicate, merge/reflection/context allowlists, trigger-score formula, `AI:` rendering, and the settled-execution claim boundary. |
| F5 — oracle ladder overstates stage resolution | **accept and patch** | L0→L1 is explicitly joint S1+S2; S4 is defined only under L2’s required-complete/harmful-absent oracle context; per-fixture mechanism inventory replaces category-based classification. |
| F6 — sealing does not prevent candidate/rubric cherry-picking | **accept and patch** | Freeze a complete candidate registry, distinguish construction-pattern from item-level adaptations, and require deterministic location/value rules for derived deletion. Single-annotator construct bias and traffic representativeness remain a **declared limitation**. |
| F7 — external slice selection is under-specified | **accept and patch** | Add eligibility fields, fixed distance bins, SHA-256 ordering, deterministic round-robin strata selection, and an exact 20/20 LongMemEval fallback rule. |
| F8 — split-specific floors can produce a stable false STOP interpretation | **declared limitation** | Keep the pre-registered thresholds, but require `STOP_COMMON_FLOOR` and explicit wording that stable failures were observed; prohibit interpreting this result as absence of a problem. |
| F9 — replicate/gate execution semantics are ambiguous | **accept and patch** | Define cell status, API-failure handling, majority and paired identities, replicate aggregates, five-run escalation, instability, and reason codes in §6.1. |

---

## 12. Stop conditions and forbidden interpretation

Stop treatment work if G0 fails. Stop scaling if G1 fails. A null result is a valid result.

Declared validity limits include single-annotator construct bias, no real-traffic representativeness claim, settled-execution-only baseline fidelity, and the split-floor false-STOP shape described in §8.1. These limitations remain even when all gates pass.

The project must not claim:

- a newly invented memory benchmark;
- SOTA against the four external benchmarks;
- causal error shares by pipeline layer;
- generalization from 24 pilot scenarios;
- “production superiority” over Mem0 from a controlled 40-item slice;
- statistical significance from pilot counts;
- independent blind annotation if only one person authored and labeled the holdout;
- that an S2 failure exposed to the known paraphrased `increment`/`remove` exact-match bug demonstrates an inherent inability of summary compression to support lifecycle correctness;
- that `STOP_COMMON_FLOOR` means no stable memory failures were observed;
- that L0→L1 independently localizes S1 or S2, or that required evidence merely being present alongside harmful evidence proves an S4 bottleneck.

The strongest allowed final claim is conditional:

> On frozen internal scenarios and a pre-registered external slice, the applicability-aware lifecycle reduced a specified count of stale/context-invalid answers while preserving required-memory context; staged traces and oracle repairs identified the pipeline layer whose repair helped most.

The numbers are filled only from M2 frozen reports.

---

## 13. Adversarial-review checklist before freeze

The reviewer should attempt to reject v1.2 by answering:

1. Does B-summary omit any behavior that makes current InnerFlow stronger?
2. Can any gold rubric be passed by mentioning both conflicting choices?
3. Is the sealed subset truly hidden from the treatment implementer?
4. Can a scenario be traced to a source and transformation log?
5. Are correction and supersession operationally distinguishable?
6. Does the oracle ladder accidentally support a causal-percentage claim?
7. Can G0 be passed by deletion-only or unstable-model failures?
8. Can G1 pass by suppressing all memory?
9. Is the PersonaMem slice selected without treatment-result knowledge?
10. Is Mem0 configured as a credible system rather than a weak strawman?
11. Can every proposed artifact be traced to RB1, RB2, or RB3?
12. Is any result shown as a decimal or percentage despite a small denominator?
13. Can G0 recognize stable B-full failures in context-exception/no-memory without requiring a compression-specific paired win?
14. Does G1 include at least one correction/supersession repair, and does the report separate missing-mechanism from non-trivial stage wins?
15. Does at least `1/4` pilot deletion coverage require redacting content already absorbed into summary/Wiki state?
16. Are exact-match-bug-exposed S2 failures tagged, and is the optional secondary-control decision documented before any mechanism-level interpretation?
17. Does every report use `sealed holdout` and explicitly disclaim IAA/annotator independence while no reviewer is named?
18. Does Path B use the intersection of B-summary/B-full failures plus the required mechanism check on every supporting scenario?
19. Can a no-memory-only or empty-context treatment still satisfy G1?
20. Is treatment restricted by the matched `C_s` for each scenario rather than a global maximum?
21. Does faithful B-summary preserve `conversationSummary → reflection → Deep insight` and exact `AI:` role rendering?
22. Is S4 counted only when L2 supplies required-complete, harmful-absent context?
23. Does the candidate registry expose every rejected/replaced scenario and make derived-deletion grading deterministic?
24. Does the external-slice manifest produce the same 40 IDs for two independent implementers?
25. Do replicate churn, failed calls, and three-to-five-run escalation produce one unambiguous gate reason code?

Freeze requires a written disposition for every objection: accept and patch, reject with reason, or mark as a declared limitation. The five v1.1 findings are disposed in §11.1 and the nine independent-review findings in §11.2. After freeze, changing category counts, gates, gold, or primary metrics requires a new protocol version and invalidates comparison with v1.2.

### 13.1 Closure record

- Independent closure review: F1–F9 `CLOSED`.
- Final reviewer verdict: `FREEZE`.
- Owner confirmation: accepted.
- Frozen version: `v1.2`.
- Implementation boundary: merge this document first; then begin M0 only. No aware treatment is authorized before G0 returns GO.

---

## References

- InnerFlow [`MemoryService.java`](https://github.com/LING-6150/Ling-innerflow/blob/8a8aa69380fa873a9b803802751271d7e539a8ce/src/main/java/com/ling/linginnerflow/memory/MemoryService.java)
- InnerFlow [`MemoryCompressionService.java`](https://github.com/LING-6150/Ling-innerflow/blob/8a8aa69380fa873a9b803802751271d7e539a8ce/src/main/java/com/ling/linginnerflow/memory/MemoryCompressionService.java)
- [LongMemEval official repository](https://github.com/xiaowu0162/LongMemEval)
- [PersonaMem official repository](https://github.com/bowen-upenn/PersonaMem)
- [BenchPreS paper](https://arxiv.org/abs/2603.16557)
- [RPEval paper and repository link](https://arxiv.org/abs/2601.16621)
- [Mem0 official repository](https://github.com/mem0ai/mem0)
- [LangMem official repository](https://github.com/langchain-ai/langmem)
- [Graphiti official repository](https://github.com/getzep/graphiti)
