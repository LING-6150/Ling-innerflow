# PATTERN_STRUCTURE_MVP.md

> InnerFlow — Pattern Structure MVP design contract.
>
> Status: **paused — product/data contract only; not active implementation**
> Date: 2026-05-31
> Depends on: `docs/STATE.md`,
> `docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md`,
> `docs/postmortems/002-v2-abstain-roadmap-r1-to-r3.md`

---

## 0. Purpose

**Pause notice (2026-06-16):** this contract is retained for future use, but
Pattern Structure module implementation is paused. PR #47 merged an aggregate
shell as dormant infrastructure only. Do not implement Scene Distribution,
Relationship Objects, Temporal Structure, or Neighbor Patterns until the
discovery/abstain pipeline meets the recovery criteria in
`docs/product/pattern-structure-pause.md`.

Pattern Structure is a read-only layer over patterns the user has already
accepted or the system can treat as high-trust. It helps the user inspect the
observable shape of a recurring pattern across their own evidence.

It is a mirror layer. It does not explain psychological origin, infer hidden
motives, assign clinical labels, or turn a tentative candidate into a stronger
claim.

The MVP answers four product questions:

1. Where does this pattern appear?
2. Who or what relationship objects appear around it?
3. How is it distributed over time?
4. Which already-confirmed patterns appear nearby in the same evidence window?

---

## 1. Hard Scope

### 1.1 In Scope

- Structure generation for `confirmed`, `partially_confirmed`, or explicit
  `high_trust` pattern instances.
- Four modules only:
  - Scene Distribution
  - Relationship Objects
  - Temporal Structure
  - Neighbor Patterns
- Data contracts for backend API and frontend rendering.
- Deterministic aggregation wherever the source data already contains the needed
  fields.
- LLM extraction only for compact semantic labels that cannot be reliably
  computed from structured fields.

### 1.2 Out of Scope

- Any structure view for `candidate`, `rejected`, `archived`, abstained,
  low-trust, or unknown-trust patterns.
- Any explanation of root causes, personality traits, attachment style,
  disorder, trauma origin, family-system theory, or prediction of future state.
- Any clinical, diagnostic, or pathology wording.
- Any cross-pattern topology, influence map, or causal chain.
- Any product copy that upgrades evidence into certainty.

---

## 2. Eligibility Gate

Pattern Structure must be generated only after an explicit eligibility check.

```text
eligible_for_structure =
  pattern_instance.status in [confirmed, partially_confirmed]
  OR pattern_instance.trust_tier == high_trust
```

If the check fails, the API returns `STRUCTURE_NOT_AVAILABLE` and no module
payload is generated.

### 2.1 High-Trust Definition

`high_trust` is a product/runtime tier, not a new psychological claim. MVP may
set it from one of these sources:

- user explicitly confirmed the instance in an earlier flow;
- an evaluation-approved engine version marks the instance high-trust under a
  pre-registered threshold;
- a human review/admin workflow marks it high-trust for internal testing.

MVP must not infer `high_trust` from a persuasive summary alone.

V1 MVP only implements the `status in [confirmed, partially_confirmed]`
eligibility path. The `high_trust` tier is documented as a future extension
point and is not part of MVP runtime behavior.

---

## 3. First Open Experience

When the user first opens Pattern Structure for an eligible pattern, they see a
single pattern-centered page.

### 3.1 Header

The header shows:

- pattern display name;
- confirmation state, such as `Confirmed` or `Partially confirmed`;
- neutral one-sentence summary from `PatternInstance.personalized_summary`;
- evidence count and date range;
- a safety note: `This view organizes examples you have already accepted. It is
  not a diagnosis or an explanation of why this happens.`

### 3.2 Four Cards

Below the header, the user sees four cards in this order:

1. **Scene Distribution** — top contexts where evidence appears.
2. **Relationship Objects** — people, roles, or relationship objects appearing
   in evidence.
3. **Temporal Structure** — first observed date, recent density, and time
   concentration points.
4. **Neighbor Patterns** — already-confirmed patterns that co-occur within the
   same local evidence windows.

Each card uses short labels, counts, and clickable evidence samples. The default
view shows aggregates first, then lets the user expand to source excerpts.

### 3.3 Empty and Partial States

The first open state must remain useful with sparse data:

- If a module has insufficient evidence, show `Not enough accepted evidence yet`.
- If Relationship Objects extraction is disabled, show only deterministic role
  tags already present in metadata.
- If Neighbor Patterns has no confirmed co-occurrence, show `No confirmed nearby
  patterns found in the current evidence window`.

---

## 4. Source Data Mapping

Pattern Structure reads only from existing pattern evidence artifacts and
approved metadata.

### 4.1 PatternInstance

Used for:

- `pattern_instance_id`
- `user_id`
- `pattern_key`
- `domain`
- `status`
- `trust_tier` if available
- `confidence` for audit display only, not for persuasive copy
- `personalized_summary`
- `evidence_chain_id`
- `first_observed_at`
- `last_observed_at`
- `last_reviewed_at`

### 4.2 EvidenceChain

Used for:

- active chain identity;
- generated timestamp;
- generator version;
- ordered list of evidence item IDs;
- chain-level strength only for audit metadata, not for user-facing claims.

### 4.3 EvidenceItem

Used for:

- `source_type`
- `source_ref`
- `occurred_at`
- `excerpt`
- `is_verbatim`
- `interpretation`
- source metadata if already available, such as channel, journal tag,
  conversation ID, participant IDs, or user-supplied context tag.

EvidenceItem remains the citation unit. Every visible aggregate must be
expandable to one or more EvidenceItems unless safety filtering hides the raw
excerpt.

---

## 5. Module Contracts

### 5.1 Scene Distribution

Purpose: show the situations or domains where the accepted pattern appears.

#### Inputs

- Eligible `PatternInstance`.
- Active `EvidenceChain` for that instance.
- EvidenceItems in the chain.
- Optional source metadata:
  - `source_type`
  - conversation/channel type
  - journal/check-in tags
  - domain labels from `PatternInstance.domain`
  - user-provided context labels

#### Deterministic Computation

- Count EvidenceItems by `source_type`.
- Count EvidenceItems by available domain/context metadata.
- Compute share percentage per context.
- Select representative EvidenceItems per context by recency and diversity.
- Apply minimum display threshold, such as at least 2 items or at least 20% of
  accepted evidence.

#### LLM Generation

LLM may generate a short neutral scene label when raw metadata is too noisy.
The label must be grounded in evidence text and must not infer motive.

Allowed labels:

- `work planning`
- `family calls`
- `partner conversations`
- `deadline reflection`
- `post-meeting notes`

Disallowed labels:

- labels that explain origin;
- labels that assign traits;
- labels that use clinical framing;
- labels that state certainty beyond observed evidence.

#### Output

```text
SceneDistributionModule {
  module: "scene_distribution"
  status: available | insufficient_data | hidden_for_safety
  scenes: [
    {
      scene_id: string
      label: string
      source: deterministic | llm_assisted
      evidence_count: integer
      evidence_share: number
      first_seen_at: timestamp
      last_seen_at: timestamp
      representative_evidence_item_ids: [uuid]
    }
  ]
}
```

---

### 5.2 Relationship Objects

Purpose: show which people, roles, or relationship objects appear around the
accepted evidence.

A relationship object can be a named person already known to the user, a stable
role, or a user-created label. It must not become a judgment about that person.

#### Inputs

- Eligible `PatternInstance`.
- Active `EvidenceChain`.
- EvidenceItems in the chain.
- Optional source metadata:
  - chat participant IDs
  - contact display names
  - user-authored aliases
  - relationship role tags, such as `mother`, `manager`, `friend`, `partner`

#### Deterministic Computation

- Count participant IDs in source metadata.
- Count known relationship tags.
- De-duplicate aliases that resolve to the same contact.
- Keep private source refs hidden unless the user opens the evidence drawer.

#### LLM Generation

LLM may extract relationship object mentions from excerpts when no structured
participant metadata exists.

LLM output must be one of:

- `person_name_or_alias`
- `role_label`
- `group_label`
- `unknown_relationship_object`

The model must not infer private facts, assign blame, or describe someone as the
cause of the pattern.

#### Output

```text
RelationshipObjectsModule {
  module: "relationship_objects"
  status: available | insufficient_data | hidden_for_safety
  objects: [
    {
      object_id: string
      display_label: string
      object_type: person | role | group | unknown
      source: metadata | llm_assisted | user_label
      evidence_count: integer
      first_seen_at: timestamp
      last_seen_at: timestamp
      representative_evidence_item_ids: [uuid]
      privacy_level: normal | sensitive | hidden
    }
  ]
}
```

---

### 5.3 Temporal Structure

Purpose: show when the accepted pattern appears in the user's evidence.

#### Inputs

- Eligible `PatternInstance`.
- Active `EvidenceChain`.
- EvidenceItems with `occurred_at`.
- Optional historical EvidenceChains for the same PatternInstance if retained.

#### Deterministic Computation

- `first_observed_at`: earliest EvidenceItem `occurred_at`, falling back to
  `PatternInstance.first_observed_at`.
- `last_observed_at`: latest EvidenceItem `occurred_at`, falling back to
  `PatternInstance.last_observed_at`.
- Recent density: count EvidenceItems in a fixed lookback window, such as 7, 30,
  and 90 days.
- Time concentration points: group EvidenceItems by day/week/month, then return
  buckets above a minimum count.
- Evidence timeline: sorted EvidenceItem IDs.

#### LLM Generation

No LLM is required for the MVP temporal module. If a user-facing phrase is
needed, it must be templated from deterministic fields.

Allowed template:

```text
"Most accepted evidence in this chain appears around {date_or_period}."
```

#### Output

```text
TemporalStructureModule {
  module: "temporal_structure"
  status: available | insufficient_data | hidden_for_safety
  first_observed_at: timestamp
  last_observed_at: timestamp
  recent_density: {
    last_7_days: integer
    last_30_days: integer
    last_90_days: integer
  }
  concentration_points: [
    {
      bucket_start: timestamp
      bucket_end: timestamp
      granularity: day | week | month
      evidence_count: integer
      representative_evidence_item_ids: [uuid]
    }
  ]
  timeline_evidence_item_ids: [uuid]
}
```

---

### 5.4 Neighbor Patterns

Purpose: show which other already-confirmed patterns appear near the current
accepted evidence.

Neighbor Patterns are co-occurrence hints only. They are not dependencies,
causes, or explanations.

#### Inputs

- Eligible current `PatternInstance`.
- Active EvidenceChain for the current instance.
- Other PatternInstances for the same user with status `confirmed` or
  `partially_confirmed`.
- Active EvidenceChains for those other eligible PatternInstances.
- EvidenceItem timestamps and source refs.

#### Deterministic Computation

A neighbor exists when another confirmed pattern has EvidenceItems that match at
least one of these windows:

- same `source_ref`;
- same conversation/thread ID if available;
- timestamp within a fixed window, such as ±24 hours;
- same user-provided event/context tag.

Compute:

- co-occurrence count;
- shared source count;
- first/last co-occurrence;
- representative paired EvidenceItem IDs.

#### LLM Generation

No LLM is required to find neighbors. LLM may generate only a short neutral
connector label after deterministic co-occurrence has been established.

Allowed connector labels:

- `same conversation`
- `same week`
- `same journal entry`
- `same context tag`

Disallowed connector labels:

- any causal wording;
- any wording that ranks patterns by importance;
- any wording that says one pattern explains another.

Rendering constraint: Neighbor list MUST be rendered as a flat list of cards
(linear). Do NOT render neighbors as connected graph nodes, lines, or arrows.

#### Output

```text
NeighborPatternsModule {
  module: "neighbor_patterns"
  status: available | insufficient_data | hidden_for_safety
  window_policy: {
    same_source_ref: boolean
    same_thread: boolean
    time_window_hours: integer
    same_context_tag: boolean
  }
  neighbors: [
    {
      pattern_instance_id: uuid
      pattern_key: string
      display_name: string
      status: confirmed | partially_confirmed
      cooccurrence_count: integer
      shared_source_count: integer
      first_cooccurred_at: timestamp
      last_cooccurred_at: timestamp
      connector_label: string
      representative_pairs: [
        {
          current_evidence_item_id: uuid
          neighbor_evidence_item_id: uuid
          relation_basis: same_source_ref | same_thread | time_window | same_context_tag
        }
      ]
    }
  ]
}
```

---

## 6. Aggregate API Contract

### 6.1 Request

```text
GET /api/pattern-instances/{patternInstanceId}/structure
```

Query parameters:

```text
includeEvidenceSamples: boolean = false
module: scene_distribution | relationship_objects | temporal_structure | neighbor_patterns | all
```

### 6.2 Response

```text
PatternStructureResponse {
  pattern_instance_id: uuid
  pattern_key: string
  display_name: string
  eligibility: {
    eligible: boolean
    reason: confirmed | partially_confirmed | high_trust | not_available
    status: string
    trust_tier: string nullable
  }
  generated_at: timestamp
  generator_version: string
  source_chain_ids: [uuid]
  evidence_window: {
    evidence_count: integer
    first_observed_at: timestamp nullable
    last_observed_at: timestamp nullable
  }
  modules: {
    scene_distribution: SceneDistributionModule
    relationship_objects: RelationshipObjectsModule
    temporal_structure: TemporalStructureModule
    neighbor_patterns: NeighborPatternsModule
  }
  safety: {
    raw_excerpt_filter_applied: boolean
    hidden_evidence_count: integer
    llm_fields_present: [string]
    deterministic_fields_present: [string]
  }
}
```

### 6.3 Error Response

```text
PatternStructureError {
  error: "STRUCTURE_NOT_AVAILABLE" | "PATTERN_NOT_FOUND" | "FORBIDDEN" | "INSUFFICIENT_EVIDENCE"
  pattern_instance_id: uuid nullable
  message: string
}
```

For ineligible patterns, `message` must use neutral language:

```text
"Structure is available only for patterns you have confirmed or that meet the high-trust threshold."
```

---

## 7. Deterministic vs LLM Responsibilities

### 7.1 Must Be Deterministic

- Eligibility gate.
- Status and trust-tier checks.
- EvidenceItem counts.
- Date ranges.
- Recent density.
- Time concentration buckets.
- Neighbor co-occurrence windows.
- Source refs and EvidenceItem IDs.
- Privacy/safety hiding after a content safety flag exists.
- API error behavior.

### 7.2 May Be LLM-Assisted

- Scene labels when source metadata is incomplete or noisy.
- Relationship object extraction from unstructured excerpts.
- Optional short connector labels for already-computed neighbors.
- Optional display-name normalization for repeated aliases.

### 7.3 Must Not Be LLM-Generated

- Eligibility decisions.
- Trust tier assignment.
- Counts, date ranges, or densities.
- Neighbor existence.
- Any causal or origin explanation.
- Any clinical framing.
- Any hidden inference about other people.
- Any stronger claim than the accepted evidence supports.

---

## 8. Safety Boundary

Pattern Structure is safer than candidate discovery only if it stays inside the
accepted-evidence boundary.

### 8.1 Eligibility Safety

- Do not generate structure for abstained candidates.
- Do not generate structure for low-trust candidates.
- Do not generate structure for rejected patterns.
- Do not generate structure for ordinary unreviewed candidates.
- Do not let the frontend call module endpoints that bypass eligibility.

### 8.2 Language Safety

The UI and LLM prompts must avoid:

- diagnostic labels;
- clinical language;
- hidden-motive claims;
- explanations of childhood, trauma, personality, or origin;
- blame toward relationship objects;
- certainty language such as `always`, `proves`, or `the real reason`.

Preferred copy pattern:

```text
"In accepted examples, this appears most often in {scene}."
"These relationship objects appear in the accepted evidence."
"Nearby confirmed patterns are shown only when evidence overlaps in time or source."
```

### 8.3 Evidence Safety

- Every aggregate must trace back to EvidenceItems.
- Excerpts with crisis, self-harm, sexual, medical, legal, or highly sensitive
  content may be counted but hidden from default display.
- If all representative excerpts for a module are hidden, show aggregate counts
  without raw text.
- Never expose source refs to another user.
- Respect deletion: if a source item is deleted, remove it from future structure
  responses.

### 8.4 Product Safety

- Structure must not make a pattern feel more certain than the user's confirmed
  state.
- `partially_confirmed` patterns must display the user's edited summary when
  present.
- Neighbor Patterns must be introduced as co-occurrence, not explanation.
- LLM-assisted labels must be auditable through `source` fields and
  `llm_fields_present`.

---

## 9. Minimum MVP Acceptance Criteria

The MVP is acceptable when:

- ineligible patterns return `STRUCTURE_NOT_AVAILABLE` with no generated module
  payload;
- every available module includes EvidenceItem IDs;
- Temporal Structure and Neighbor Patterns can run with no LLM call;
- all LLM-assisted fields are marked as such;
- no response field contains causal, diagnostic, or origin-explanation copy;
- the first-open page renders useful empty states for sparse accepted evidence;
- module outputs can be recomputed from PatternInstance, EvidenceChain, and
  EvidenceItem plus approved source metadata.

---

## 10. Non-Goals for This Spec

This spec does not define candidate discovery changes, abstain research, new
pattern taxonomies, or frontend visual polish. It defines only the minimum safe
contract for showing structure after a pattern is accepted or high-trust.
