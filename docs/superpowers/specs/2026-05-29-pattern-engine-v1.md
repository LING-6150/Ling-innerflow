# PATTERN_ENGINE_V1.md

> InnerFlow — AI System Design for the Pattern Discovery engine.
>
> Status: **V1 Engine Design (implementation-ready)**
> Date: 2026-05-29
> Companion to: `PATTERN_DISCOVERY_V1.md` (frozen). This document assumes that
> product spec is final and does not revisit product, UI, journeys, or scope.
>
> Mode: **AI System Design**, not Product Design. Everything here is about how
> the system *computes* a `PatternInstance` and its `EvidenceChain` correctly,
> reproducibly, and measurably.

---

## 0. Scope & Contract With the Product Spec

This document specifies the machinery behind §12.2 of `PATTERN_DISCOVERY_V1.md`
("Pattern Engine ← User Wiki + Memory, read path"). It treats the following as
fixed inputs/outputs and does not redesign them:

| Frozen artifact (from product spec) | Role in this doc |
|---|---|
| 6 Domains, 12 `pattern_key` closed taxonomy (§6, §7) | Detection label space (closed-set classification) |
| `PatternDefinition` YAML (§8.1) | Detection priors + verification rubric source |
| `PatternInstance` (§8.2) | Engine output row |
| `EvidenceChain` / `EvidenceItem` (§9) | Engine output payload; ≥3 items, ≥1 verbatim |
| Confirmation states (§10) | Supervision signal for evaluation |
| `(user_id, pattern_key, domain)` uniqueness + 90-day reject cooldown (§8.2) | Dedup + cooldown constraints |
| Language firewall, crisis exclusion (§13) | Hard filters in the pipeline |
| Success/Failure metrics (§16, §17) | Targets the eval methodology must measure |

**Reused existing infrastructure (do not rebuild):**

- `rag.HybridSearchService` — HyDE → Pinecone(vector) → ES BM25 → RRF → LLM
  re-rank. We generalize it from CBT-doc retrieval to user-history retrieval.
- `rag.HyDEService` — hypothetical-document query expansion.
- `rag.LLMRerankerService` — single-call cross-encoder-approximation re-rank.
- `memory.MemoryService#findSimilarTrigger` / `cosineSimilarity` — cosine dedup,
  threshold `0.88`, batch-embed pattern. We reuse the exact mechanism for §6.
- `memory.MemoryService#computeScore` — `countScore × recencyScore`, 90-day
  half-life. We reuse the recency-decay term in §5.
- Embedding model: OpenAI `text-embedding-ada-002`, 1536-dim (Spring AI default).
- Chat model: `gpt-4o-mini` (`spring.ai.openai.chat.options.model`).
- Elasticsearch (BM25), Pinecone (`innerflow-cbt` index today; new namespace for
  user history, see §2).

The engine is an **offline batch job** (nightly per user + gated manual
trigger). It never runs in the real-time chat path (§13.3 of product spec).

---

## 1. Pattern Detection Pipeline

### 1.1 Problem framing

Detection is **closed-set, multi-label, evidence-grounded classification over a
longitudinal corpus**, not free-form generation:

- **Closed-set:** output labels ∈ the 12 `pattern_key`s. The LLM may not invent
  keys (product §7). This turns "detect patterns" into "for each of 12 known
  patterns, decide present/absent in a given domain, and if present, cite ≥3
  pieces of the user's own evidence."
- **Multi-label:** a user can exhibit several patterns; patterns can co-occur in
  one domain.
- **Per-(pattern, domain):** the unit of output is a `(pattern_key, domain)`
  pair, matching the `PatternInstance` uniqueness key.
- **Evidence-grounded:** a label without ≥3 evidence items (≥1 verbatim) is
  discarded before it can become a candidate (product §9). Detection is only
  "done" when retrieval has succeeded.

This framing is deliberate: it converts an open-ended, hallucination-prone task
into 12 bounded sub-problems, each independently verifiable against the user's
corpus. It is the single most important decision in the engine.

### 1.2 Pipeline stages

```
                 PER USER, NIGHTLY (or manual gated trigger)
┌──────────────────────────────────────────────────────────────────────┐
│ S0  Corpus assembly        load + normalize user records → CorpusDoc[] │
│ S1  Pre-filter / gating    skip users below evidence floor             │
│ S2  Candidate generation   per-pattern recall: which of 12 plausible?  │
│ S3  Evidence retrieval     for each candidate, retrieve top-k CorpusDoc │  → §3
│ S4  Evidence verification  validate each retrieved item supports label │  → §4
│ S5  Instance assembly      build EvidenceChain (≥3, ≥1 verbatim)       │
│ S6  Confidence scoring     score each surviving (pattern,domain)        │  → §5
│ S7  Deduplication          merge vs existing active instances           │  → §6
│ S8  Cooldown / safety gate reject-cooldown, crisis filter, firewall     │
│ S9  Persistence            upsert PatternInstance + EvidenceChain (tx)  │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.3 S0 — Corpus assembly

Normalize all sources into a single retrievable unit, `CorpusDoc`:

```
CorpusDoc {
  doc_id:        string            // stable: "{source_type}:{source_ref}"
  user_id:       fk
  source_type:   enum(chat_message, journal_entry, checkin, wiki_fact)
  source_ref:    string            // message_id / journal_id / checkin_id / wiki_change_id
  occurred_at:   timestamp
  text:          string            // normalized, PII-stable, ≤ 512 tokens (chunked if longer)
  role:          enum(user, assistant) | null   // chat only; assistant turns are context, never evidence
  crisis_flag:   boolean           // copied from source; crisis docs excluded from evidence (product §13.3)
  embedding:     float[1536]       // ada-002, computed once, cached
}
```

Rules:
- **Only `role=user` chat turns are evidence-eligible.** Assistant turns may be
  retrieved as *context* for the verification LLM but can never become an
  `EvidenceItem` (the mirror cites the user's words, not its own — product §9).
- Window: last `N=200` user-authored records OR last 180 days, whichever is
  smaller (product §12.2 step 1). Older records remain embedded for recency
  decay but are not re-scanned each night.
- Chunking: records > 512 tokens are split; `doc_id` gets a `#chunk` suffix.
- `crisis_flag` is propagated from the existing agent crisis detector. Crisis
  docs are **indexed** (so retrieval is coherent) but hard-filtered at S4/S8.

### 1.4 S1 — Pre-filter / gating

The engine must not hallucinate patterns from thin data (this is the #1 failure
mode for this product). Gate:

- Skip user entirely if evidence-eligible `CorpusDoc` count < **20** in the
  window. (Below this, recall is noise; product empty-state copy already covers
  "再聊一会儿吧".)
- Per candidate pattern, require minimum corpus support before even calling the
  verifier (see §3.4).

These thresholds are config (`pattern.gate.min-corpus-docs=20`) so the eval
harness can sweep them.

### 1.5 S2 — Candidate generation (recall stage)

Goal: cheaply narrow 12 patterns → a short list worth the expensive
retrieve+verify loop. Two-signal recall, union'd:

1. **Embedding recall.** Each `PatternDefinition` has a stable *probe vector* =
   mean of embeddings over `neutral_description + evidence_shapes` (computed once
   at startup, cached; recomputed only on YAML `version` bump). Score each
   pattern by max cosine similarity between its probe vector and any user
   `CorpusDoc` embedding. Keep patterns with max-sim ≥ `0.78`.
2. **LLM recall.** One `gpt-4o-mini` call: given the 12 short names + neutral
   descriptions and a *sampled* corpus digest (most-recent 30 + 20 highest-norm
   user docs), return the subset of `pattern_key`s plausibly present, with a
   coarse 0–2 prior each. Closed-set enforced by validating returned keys
   against the taxonomy; unknown keys dropped.

Candidate set = union of (1) and (2). Domain for each candidate is assigned in
S3 from where its evidence actually concentrates, not guessed here.

Rationale for two signals: embedding recall has high recall / low precision and
is cheap; LLM recall adds precision and reads context the bi-encoder misses
(this mirrors why `HybridSearchService` fuses vector + BM25 + rerank). Union
favors recall — precision is recovered downstream by verification (§4) and
confidence (§5).

### 1.6 S3–S9

Detailed in §3 (retrieval), §4 (verification), §5 (confidence), §6 (dedup), and
below for S8/S9.

**S8 — Cooldown / safety gate** (order matters, cheapest-rejecting-first):
1. Crisis filter: drop any `EvidenceItem` whose `CorpusDoc.crisis_flag=true`. If
   this drops the chain below 3 items or below 1 verbatim, **discard the whole
   instance** (product §17 "crisis leakage" = hard fail).
2. Reject-cooldown: if `(pattern_key, domain)` has a `rejected` instance with
   `last_reviewed_at` < 90 days ago, suppress unless the new EvidenceChain is
   *substantially different* — defined as Jaccard(new source_refs, rejected
   source_refs) < `0.5` AND ≥2 of the new items post-date the rejection.
3. Language firewall: run `personalized_summary` through the regex blacklist +
   LLM-judge (product §13.1). Regenerate up to 2×; if still failing, discard.

**S9 — Persistence:** single transaction per `(pattern_key, domain)`:
upsert `PatternInstance`, insert new immutable `EvidenceChain` (+ items), link,
bump `refresh_count`, set `last_observed_at`. Never deletes a user-confirmed
instance (product §12.2 invariant).

### 1.7 Determinism

To hit the product's "refresh determinism ≥ 90% Jaccard" metric (§16):
- All LLM calls use `temperature=0`, fixed seed where the API supports it.
- Embeddings cached by `doc_id` (content hash); identical input → identical
  vector → identical recall ordering.
- Retrieval tie-breaks are deterministic (sort by score, then `occurred_at`,
  then `doc_id`).
- `generator_version` (product §9) = `{chat_model}-{prompt_hash}-{taxonomy_ver}`,
  written on every chain for reproducibility and eval slicing.

---

## 2. Retrieval Architecture

### 2.1 Why retrieval at all

The critical-review gap: "Retrieval is missing." Without retrieval the only
option is stuffing 200 records into one prompt — which (a) overflows context as
history grows, (b) destroys evidence traceability (the LLM paraphrases from a
blob and `source_ref` becomes a guess), and (c) makes determinism impossible.
Retrieval makes evidence **addressable**: every `EvidenceItem.source_ref` is a
real `doc_id` returned by a query, not a hallucinated citation.

### 2.2 Index topology

We reuse the existing stack with a **new user-history namespace**, isolated from
the CBT knowledge base:

| Store | Existing use | Pattern-engine use |
|---|---|---|
| Pinecone | `innerflow-cbt` index | **new namespace `user-history`**, partitioned by `user_id` (metadata filter). 1536-dim ada-002 vectors of `CorpusDoc.text`. |
| Elasticsearch | CBT BM25 | **new index `user_history`**: BM25 over `CorpusDoc.text`, filtered by `user_id`, with `occurred_at`, `source_type`, `crisis_flag` as filterable fields. |
| MySQL | source-of-truth records | `CorpusDoc` metadata table; embedding cache key. |

Strict per-user isolation is mandatory: every vector/BM25 query carries a
`user_id` filter. A cross-user evidence leak is a privacy incident, not just a
quality bug.

### 2.3 Retrieval modes

The engine uses **two distinct retrieval calls** with different queries:

1. **Pattern-probe retrieval (recall, §1.5):** query = pattern probe vector.
   Pinecone-only, cheap, used to decide candidacy.
2. **Evidence retrieval (precision, §3):** query = pattern-specialized HyDE
   expansion. Full hybrid pipeline (vector + BM25 + RRF + rerank).

This reuses `HybridSearchService`'s exact 5-stage shape, repointed from CBT docs
to `user_history`. We generalize the service to take a `corpus` parameter
(`CBT` | `USER_HISTORY`) rather than forking it.

---

## 3. Evidence Retrieval Strategy

For each candidate `pattern_key` from S2:

### 3.1 Pattern-conditioned HyDE

`HyDEService` today writes a hypothetical *CBT entry*. For evidence retrieval we
add a second prompt template: given a `PatternDefinition`'s `evidence_shapes`,
generate **3 hypothetical first-person user utterances** that would exemplify the
pattern, then embed each.

> Example for `people_pleasing`: "I said yes to covering her shift even though I
> was exhausted, then I was angry at myself the whole drive home."

Rationale identical to the existing HyDE rationale: a user rarely writes "I have
people-pleasing tendencies." They write the *symptom*. Embedding a symptom-shaped
hypothetical sits closer to real user docs than embedding the abstract pattern
name. We embed all 3 and query with each (multi-probe), which raises recall on
patterns that surface heterogeneously.

### 3.2 Hybrid fusion (reuse `HybridSearchService`)

Per candidate:
1. Vector search: each of the 3 HyDE vectors → Pinecone `user-history`,
   `user_id`-filtered, `candidate-k=10` each → dedup by `doc_id`.
2. BM25 search: keyword query built from `PatternDefinition` lexical cues
   (curated `lexical_cues` list added to each YAML — see §3.5) → ES
   `user_history`, `user_id`-filtered, `es-candidate-k=10`.
3. **RRF fusion** (reuse existing `RRF_K=60`) → single ranked candidate pool.
4. **LLM re-rank** (`LLMRerankerService`): query = the pattern's
   `neutral_description`; docs = pooled `CorpusDoc.text`. Returns top-N by
   relevance. We raise `TOP_N` from 3 → **7** here (product allows 3–7 evidence
   items; we retrieve 7, verification §4 prunes to the survivors, floor 3).

### 3.3 Recency-aware retrieval

Pattern evidence should skew recent without ignoring deep history. We
post-multiply each candidate's fused score by the **existing recency factor**
`exp(−daysSince / 90)` from `MemoryService.computeScore`, with a floor of `0.3`
so a single very-old-but-perfect match can still appear. This reuses the exact
90-day half-life already in the codebase — no new decay constant.

### 3.4 Per-candidate corpus-support gate

Before spending the verifier LLM call: require ≥ **5** retrieved docs above
rerank-relevance `0.5` for the candidate. Fewer → drop the candidate (it cannot
reach 3 verified items reliably; this protects precision and cost).

### 3.5 YAML additions (non-breaking)

Each `PatternDefinition` YAML gains two optional fields the loader reads:
```yaml
lexical_cues: ["说了yes", "对不起", "不好意思", "怕他失望"]   # BM25 seed terms
hyde_exemplars: 3   # how many hypothetical utterances to generate (default 3)
```
These are additive; existing fields (product §8.1) are untouched.

---

## 4. Evidence Verification

Retrieval returns *relevant* docs. Relevant ≠ *supporting*. Verification is the
gate that makes the EvidenceChain "citation-grade" (product §9) and prevents the
#1 reviewer complaint ("I can't tell why the system thinks this").

### 4.1 Per-item entailment check

For each of the ≤7 retrieved docs, one structured `gpt-4o-mini` call (batched —
all items for one pattern in a single call to control cost) answers, per item:

```json
{
  "doc_id": "...",
  "supports": true|false,            // does THIS text evidence THIS pattern?
  "is_verbatim_quotable": true|false,// can a ≤280-char exact span be quoted?
  "verbatim_span": "exact substring or null",
  "interpretation": "one neutral sentence linking excerpt → pattern",
  "strength": 0|1|2                  // 0 weak/ambiguous, 1 clear, 2 textbook
}
```

Rules enforced in code (not trusted to the LLM):
- `verbatim_span`, if present, MUST be an exact substring of `CorpusDoc.text`
  (assert `text.contains(span)`); otherwise `is_verbatim=false`. This makes
  verbatim quotes *non-hallucinatable*.
- `interpretation` is linted by the language firewall (§13.1) here too, not only
  on the summary.
- Items with `supports=false` are dropped.

### 4.2 Chain assembly invariants (hard, product §9)

After per-item verification, assemble the chain only if **all** hold:
- ≥ 3 items with `supports=true`.
- ≥ 1 item with `is_verbatim=true` (validated substring).
- Not all items from a single day/single conversation — require evidence spanning
  ≥ **2 distinct `occurred_at` days** (a "pattern" needs recurrence, not one bad
  evening). Configurable `pattern.evidence.min-distinct-days=2`.
- 0 items with `crisis_flag=true` (already filtered S8, asserted again here).

If any invariant fails → **no instance**. Silent drop, logged with reason code
for eval (`DROP_INSUFFICIENT_EVIDENCE`, `DROP_NO_VERBATIM`, `DROP_SINGLE_DAY`).

### 4.3 Domain assignment

The instance `domain` (product §8.2: per-instance, overrides taxonomy default)
is set by majority vote of the surviving evidence items' inferred domain (the
verifier returns a `domain` per item from the closed 6-domain set), tie broken
toward the `PatternDefinition.primary_domain`. This is why domain is assigned
*after* evidence, not guessed at recall.

### 4.4 Summary generation + firewall

`personalized_summary` is generated from the **verified chain only** (the LLM
sees the surviving excerpts + interpretations, nothing else), forcing the
narrative to be grounded in shown evidence. Then language firewall (product
§13.1), regenerate ≤2×, else discard.

---

## 5. Confidence Scoring

The review said confidence is "unclear." We make it an explicit, inspectable,
bounded function — **not** a number the LLM emits (LLM self-confidence is
uncalibrated and gameable).

### 5.1 Definition

`confidence ∈ [0,1]` is computed from four observable factors:

```
confidence = w_e·Evidence + w_s·Strength + w_r·Recurrence + w_t·Recency
             (weights sum to 1; V1 defaults below, all config)

Evidence    = min(1.0, verified_item_count / 5)        // reuse computeScore's "/5 → full" shape
Strength    = mean(item.strength) / 2                  // 0..1, from §4.1
Recurrence  = min(1.0, distinct_days / 4)              // recurrence across time
Recency     = exp(−daysSinceLatestItem / 90)           // reuse 90-day half-life

w_e=0.35, w_s=0.30, w_r=0.20, w_t=0.15
```

Every term is derived from the EvidenceChain we already built — so confidence is
fully explainable from the same data the user sees ("5 verified items, 2 of them
textbook-strength, across 4 different days, most recent 3 days ago → 0.81"). This
directly answers the reviewer's "why does the system think this?"

### 5.2 Surfacing threshold

A candidate becomes a surfaced `candidate` (product §10) only if `confidence ≥
τ_surface` (default `0.6`, config `pattern.confidence.surface-threshold`).
Below τ it is persisted as a sub-threshold instance (status `candidate`,
`hidden=true`) so the next refresh can accrue evidence rather than starting cold.

### 5.3 Confidence on stale instances

Per product §12.2 ("lower confidence on an inactive instance"), each refresh
recomputes `Recency` for existing active instances even when no new evidence
arrives. An instance whose newest evidence is 6 weeks old decays via the same
`exp(−days/90)` term, surfacing the "I haven't seen this lately" signal without
deleting anything.

### 5.4 Calibration (measured, not assumed)

τ_surface and the weights are **not** hand-waved. §8 specifies a calibration
procedure: sweep τ on the labeled eval set, plot precision/recall of
"surfaced ⇒ confirmed", and pick τ at the knee. We report a reliability diagram
(predicted confidence vs. empirical confirm-rate) and Expected Calibration Error
(ECE). Target: ECE < 0.15 on the eval set.

---

## 6. Pattern Deduplication

Three distinct dedup problems; the reviewer's concern is mostly #2 and #3.

### 6.1 Key-level dedup (structural, free)

The `(user_id, pattern_key, domain)` uniqueness invariant (product §8.2) means a
refresh **upserts**: if an active instance exists for the key, we attach a new
`EvidenceChain` and recompute confidence rather than inserting a row. No fuzzy
logic needed — the database constraint enforces it.

### 6.2 Semantic near-duplicate dedup (reuse P3-11)

Two *different* `pattern_key`s can produce near-identical `personalized_summary`
text for one user (e.g. `self_criticism` and `worth_through_achievement` both
narrating "I only feel okay when I outperform"). Before surfacing, run the
**exact existing mechanism** `MemoryService#findSimilarTrigger`:
- Batch-embed the new summary + all the user's active-instance summaries
  (ada-002).
- Cosine similarity; if max ≥ **0.88** (the existing `DEDUP_THRESHOLD`) against
  an instance of a *different* key, do not surface both independently. Keep the
  higher-confidence instance; attach the loser as a `related_key` reference on
  the survivor (additive field) so evidence isn't lost.

We deliberately reuse 0.88 — it's already tuned and tested in this codebase for
exactly "same meaning, different words." No new threshold to defend.

### 6.3 Evidence-item dedup within a chain

A single user utterance can be retrieved by multiple HyDE probes. Dedup
`EvidenceItem`s by `doc_id` (exact) before the 3-item count check, so we never
satisfy "≥3 items" with the same sentence three times. For multi-chunk docs,
dedup by `source_ref` (parent record), not `doc_id` (chunk).

### 6.4 Refresh-vs-prior-chain stability

To support determinism (§1.7) and the cooldown "substantially different" test
(§1.6 S8), we compute **Jaccard over `source_ref` sets** between a new chain and
the prior chain on the same instance. This single similarity function serves
three callers: determinism metric, cooldown gate, and the eval harness.

---

## 7. Ground Truth Construction

The review said "evaluation is weak" and "baseline is missing." Both stem from
having no ground truth. This section builds it.

### 7.1 The labeling unit

A ground-truth label is a tuple:
```
GTLabel {
  user_id
  pattern_key        // ∈ 12
  domain             // ∈ 6
  present:  bool     // is this pattern truly present for this user in this domain?
  evidence_refs: [doc_id]   // the human-chosen supporting records (gold evidence)
  rationale: text
}
```
Note labels are at `(user, pattern_key, domain)` granularity — the same unit the
engine outputs — so precision/recall are directly computable.

### 7.2 Three-tier corpus

| Tier | Source | Count (V1) | Purpose |
|---|---|---|---|
| A. Synthetic personas | Hand-authored multi-month histories with *designer-known* patterns | 8–12 personas, ~60–150 docs each | Known-answer set; the only set with complete negative labels |
| B. Real opt-in users | 3–5 real histories (product §16) with consented labeling | 3–5 | Ecological validity; partial labels |
| C. Confirmation log | Live `confirm/partial/reject` actions (product §10) | grows | Cheap weak labels; powers continuous eval |

Tier A is the backbone because it is the **only tier where we know the full
negative set** (which patterns are *absent*) — essential for precision and for a
non-degenerate baseline. Real data (B) almost never has reliable negatives.

### 7.3 Synthetic persona construction (Tier A)

Each persona is authored as a spec, then a generator (LLM, offline, reviewed by a
human) expands it into dated chat/journal/checkin records:
```
Persona {
  persona_id
  true_patterns:  [{pattern_key, domain, intended_strength}]   // the answer key
  decoy_patterns: [{pattern_key, domain}]                       // present-looking but NOT intended → hard negatives
  noise_topics:   [...]                                         // unrelated content to lower base rate
  timeline:       date range, message cadence
}
```
Critical design choices:
- **Decoys / hard negatives** are mandatory: surface-similar content that should
  *not* trigger a pattern (e.g. one-off venting that isn't recurrence). Without
  hard negatives a detector that says "yes" to everything scores well — the
  classic trap.
- **Verbatim seeding:** each true pattern is given ≥1 strongly quotable line, so
  the verbatim-invariant is testable.
- **Crisis-language seeding:** a few personas include crisis-flagged docs that
  *also* look pattern-relevant, to test the §13.3 exclusion (a docs that must
  never appear as evidence even though it's topically perfect).
- Generation is human-reviewed; the persona spec, not the generated text, is the
  authority. Generated text is frozen and version-pinned (`eval/personas/vN/`).

### 7.4 Dual annotation + adjudication (Tiers A & B)

- Two annotators independently label `present` + `evidence_refs` per
  `(user, pattern_key, domain)`.
- Report **Cohen's κ** for inter-annotator agreement. Target κ ≥ 0.6
  (substantial) before the eval set is considered trustworthy; below that, the
  taxonomy descriptions are ambiguous and get sharpened (product §7 "what it is
  not").
- Disagreements adjudicated by a third reviewer; adjudicated label is gold.

### 7.5 Storage

`eval/groundtruth/` in-repo (synthetic + adjudicated labels; **no real user
text in git** — Tier B labels reference IDs only, text stays in a secured store).
Version-pinned alongside `taxonomy_version` so eval results are reproducible.

---

## 8. Evaluation Methodology

Evaluation operates at three layers; each maps to specific product metrics
(§16/§17).

### 8.1 Layer 1 — Detection quality (vs. ground truth, Tier A primary)

Unit of evaluation: `(user, pattern_key, domain)`.

| Metric | Definition | Target (V1) |
|---|---|---|
| **Precision** | surfaced ∧ truly-present / all surfaced | ≥ 0.70 |
| **Recall** | surfaced ∧ truly-present / all truly-present | ≥ 0.55 |
| **F1** | harmonic mean | ≥ 0.62 |
| **Hard-negative FP rate** | decoys surfaced / all decoys | ≤ 0.15 |
| **Recognition rate (proxy)** | on Tier C, confirm+partial / surfaced | ≥ 0.60 (product §16) |

Precision is weighted above recall: surfacing a wrong pattern damages trust more
than missing one (product is "pull-only, user is final authority"). The
hard-negative FP rate is the metric that actually proves the detector isn't just
saying "yes."

### 8.2 Layer 2 — Evidence quality

| Metric | Definition | Target |
|---|---|---|
| **Evidence-grounding rate** | instances with ≥3 items ∧ ≥1 verbatim / all surfaced | 100% (hard, product §16) |
| **Verbatim faithfulness** | verbatim spans that are exact substrings of source | 100% (asserted in code, §4.1) |
| **Evidence precision** | engine evidence_refs ∩ gold evidence_refs / engine refs | ≥ 0.70 |
| **Evidence recall** | engine refs ∩ gold / gold refs | ≥ 0.50 |
| **Interpretation firewall pass** | interpretations passing language firewall | ≥ 0.99 first-pass (product §16) |

Evidence precision/recall (vs. the gold `evidence_refs` from §7) is what
distinguishes "right label for the right reason" from "right label, retrieved
junk." A high label-F1 with low evidence-precision = the detector is guessing.

### 8.3 Layer 3 — System properties

| Metric | Definition | Target |
|---|---|---|
| **Refresh determinism** | mean Jaccard(source_refs) over 2 identical re-runs | ≥ 0.90 (product §16) |
| **Dedup effectiveness** | duplicate active instances per key / 30-day window | < 5% (product §16) |
| **Cooldown integrity** | rejected keys re-surfaced inside 90d w/o substantial-new evidence | 0 (product §17) |
| **Crisis-leakage** | evidence items sourced from crisis-flagged docs | 0 (product §17) |
| **Confidence calibration (ECE)** | predicted confidence vs empirical confirm-rate | < 0.15 (§5.4) |
| **Cost / latency** | LLM calls & wall-time per user-refresh | tracked, budget in §9.4 |

### 8.4 Slicing

Every metric is reported sliced by: `pattern_key` (which of the 12 are weak?),
`domain`, evidence-volume bucket (cold-start vs rich users), and
`generator_version` (did a prompt change regress?). A single aggregate number
hides exactly the patterns that are broken.

### 8.5 Eval harness as a test

The Layer 1–3 eval runs as a gated, offline test target (`./mvnw test
-Peval`) reading `eval/groundtruth/`. Hard invariants (grounding rate=100%,
crisis-leakage=0, verbatim faithfulness=100%) are **CI-blocking** unit tests
(product §16/§17 mark these as ship-blockers). Quality targets (F1, ECE) are
reported, regression-tracked, and block on *regression beyond a delta*, not on
an absolute bar (so a noisy real-data day doesn't red the build).

---

## 9. Baseline Design

"Baseline is missing." We define a ladder of baselines so every claimed gain is
attributable to a specific mechanism. All run on the same Tier A/B set, scored by
the same §8 metrics.

### 9.1 B0 — Random / prior

Surface each `(pattern_key, domain)` with probability = its base rate in the
ground-truth set. Establishes chance-level F1 and proves the eval set isn't
degenerate (if a real method can't beat B0, the method is broken).

### 9.2 B1 — Keyword / lexical

BM25-only: surface a pattern if its `lexical_cues` (§3.5) match ≥ k user docs.
No embeddings, no LLM. Cheap, interpretable floor; isolates "how much is just
keyword spotting?"

### 9.3 B2 — Single-prompt LLM (no retrieval)

Stuff the (truncated) corpus into one `gpt-4o-mini` prompt with the 12-pattern
taxonomy; ask for present patterns + quoted evidence. **This is the baseline the
critical review implicitly assumes most teams ship.** It exposes exactly the
failures we built retrieval/verification to fix:
- citation hallucination (quotes not in corpus) → measured against §8.2 verbatim
  faithfulness,
- context overflow as history grows,
- non-determinism.

### 9.4 B3 — Embedding-retrieval, no verification

S2 recall + S3 retrieval, but skip §4 verification: take top-k retrieved docs as
evidence directly. Isolates the **marginal value of the verification stage** —
the gap B3→full is the headline number that justifies §4's cost.

### 9.5 Ablations of the full system

Beyond baselines, ablate one mechanism at a time from the full engine:

| Ablation | Tests value of |
|---|---|
| − HyDE (raw-query embedding) | pattern-conditioned HyDE (§3.1) |
| − BM25 (vector only) | hybrid fusion (§3.2) |
| − LLM rerank | rerank stage |
| − recurrence/recency in confidence | confidence design (§5) |
| − hard-negative-aware τ calibration | calibration (§5.4) |
| LLM-emitted confidence vs computed | §5's "don't trust LLM self-confidence" claim |

### 9.6 Reporting

One table: rows = {B0, B1, B2, B3, full, full−ablation…}, columns = §8 metrics.
Every design decision in this document must be *defensible by a row in that
table*. If an ablation doesn't hurt a metric, that mechanism is cut from V1.
This is the artifact that answers the critical review directly.

### 9.7 Cost/latency budget

Per user-refresh, full pipeline (12 patterns, typical user):
- Recall: 1 LLM call (S2) + cached embeddings.
- Evidence: ≤ candidate-count × (HyDE gen 1 + rerank 1) LLM calls.
- Verify: 1 batched LLM call per candidate.
- Summary + firewall: 1–3 LLM calls per surviving instance.

Budget target: ≤ **25 `gpt-4o-mini` calls / user / nightly refresh**, ≤ 60s wall
time. Tracked as a §8.3 metric; B2 (single prompt) is cheaper per-call but is the
quality floor — the cost table shows the price of correctness.

---

## 10. Failure Cases

Enumerated so they are designed-for and **tested-for**, not discovered in
production. Each maps to a detector and a behavior.

### 10.1 Detection failures

| Failure | Cause | Mitigation | Tested by |
|---|---|---|---|
| **Over-detection / "everything is a pattern"** | recall union too loose; verifier too lax | hard-negative FP metric (§8.1); per-candidate corpus gate (§3.4); precision-weighted τ | Tier A decoys |
| **Single-event labeling** | one bad day read as recurrence | `min-distinct-days≥2` (§4.2) | `DROP_SINGLE_DAY` test |
| **Cold-start hallucination** | thin corpus | S1 gate (≥20 docs) + per-candidate gate | low-volume persona |
| **Taxonomy collision** | two keys describe same user reality | semantic dedup 0.88 (§6.2) | collision persona |
| **Domain misassignment** | guessing domain before evidence | evidence-majority assignment (§4.3) | cross-domain persona |

### 10.2 Retrieval / evidence failures

| Failure | Cause | Mitigation | Tested by |
|---|---|---|---|
| **Citation hallucination** | LLM invents a quote | verbatim must be exact substring (§4.1) | verbatim-faithfulness=100% test |
| **Irrelevant evidence** | retrieval recall, not support | per-item entailment verifier (§4.1) | evidence-precision metric |
| **Assistant-turn leakage** | quoting the bot back to user | `role=user` eligibility filter (§1.3) | unit test on mixed corpus |
| **Stale-only evidence** | all matches months old | recency decay + freshness in confidence (§3.3/§5) | aged persona |
| **Chunk-duplicate padding** | same record fills 3 slots | item dedup by source_ref (§6.3) | multi-chunk doc test |

### 10.3 Safety failures (product §17 = ship-blockers)

| Failure | Cause | Mitigation | Tested by |
|---|---|---|---|
| **Crisis leakage** | crisis doc used as evidence | crisis filter at S8 + re-asserted S4.2; drop instance if chain falls below floor | crisis-seeded persona; crisis-leakage=0 test |
| **Diagnostic drift** | summary/interpretation uses prohibited language | firewall on summary AND interpretations; regenerate ≤2 else discard | firewall pass-rate test |
| **Cross-user evidence leak** | missing user_id filter | every query user_id-filtered (§2.2); | injected multi-user index test |
| **Cooldown violation** | rejected pattern re-surfaces | substantial-difference gate (§1.6 S8) | reject→refresh persona |

### 10.4 System failures

| Failure | Cause | Mitigation |
|---|---|---|
| **Non-determinism** | temp>0, uncached embeddings | temp=0, seeded, content-hash embedding cache (§1.7) |
| **Embedding-service outage** | API down | reuse `MemoryService` graceful fallback: skip dedup/recall embedding, treat as new, log — never crash the refresh |
| **LLM JSON malformed** | verifier returns junk | strict schema parse; on failure drop that candidate, log `VERIFY_PARSE_FAIL`, never fabricate |
| **Cost blowout** | pathological user, many candidates | per-user call budget cap (§9.7); over-budget → defer remaining candidates to next night |
| **Partial transaction** | crash mid-write | per-instance transaction (S9); chain is immutable so re-run is idempotent on `doc_id` set |

### 10.5 Eval failures (meta)

| Failure | Cause | Mitigation |
|---|---|---|
| **Eval-set overfit** | tuning τ/prompts on the test set | hold out a sealed Tier-A slice never used for tuning; report on it only at milestones |
| **Degenerate ground truth** | no negatives / low κ | mandatory hard negatives (§7.3); κ ≥ 0.6 gate (§7.4) |
| **Metric gaming** | high label-F1, junk evidence | always co-report evidence-precision (§8.2); a label metric is never reported alone |

---

## 11. Open Questions (deferred, not blocking V1 build)

These are acknowledged unknowns the eval is designed to *answer*, not decisions
to make now:

1. Optimal τ_surface and confidence weights — output of §5.4 calibration, not
   guessed here.
2. Whether all 12 patterns are individually viable, or some are too rare/ambiguous
   to hit F1 ≥ 0.62 — §8.4 per-pattern slicing decides; weak patterns ship
   disabled, not deleted (consistent with product §17 feature-flag posture).
3. Multi-probe HyDE count (3 vs more) — ablation in §9.5.
4. Whether B2 (single-prompt) is "good enough" for the rarest patterns where
   retrieval has too little to work with — the cost table (§9.6) decides per
   pattern.

---

*End of PATTERN_ENGINE_V1.md.*
