# Pattern Structure MVP API Contract

## 0. Purpose

This document defines the external API and wire DTO contract for the Pattern
Structure MVP. It is a product and technical design contract for backend and
frontend implementation, not an implementation plan.

Pattern Structure exposes the observable shape of an eligible pattern across
four dimensions:

- scene distribution;
- relationship objects;
- temporal structure;
- neighbor patterns.

The contract is intentionally limited to inspectable, evidence-backed structure.
It does not define internal persistence entities, review transitions, safety
lint implementation, frontend components, or future Pattern Evolution features.

## 1. Source Inputs and Ownership Boundaries

This design leans most heavily on:

- `docs/product/phase-2-pattern-understanding-plan.md` sections 4-7 for the
  product layer boundary, trust boundary, shape-not-origin stance, and four MVP
  dimensions.
- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md` sections 2, 4,
  5, 6, and 8 for eligibility, source data mapping, module-level outputs,
  aggregate contract, and safety boundaries.
- `docs/product/pattern-structure-ux-audit.md` sections 5, 7, and 8 for
  navigation placement, frontend DTO needs, and user-facing guardrails.

Cross-line ownership:

- This document owns the external wire format, endpoint paths, request fields,
  response fields, and error shapes for Pattern Structure MVP.
- The Backend Domain Model design owns internal domain objects, internal
  relationships, and persistence mapping.
- The Frontend design must consume DTOs from this contract and must not redefine
  field names or status literals.
- The Review State Machine design owns review status semantics and transition
  rules. This contract may reference statuses but must not redefine how a
  pattern moves between them.
- The Safety/Lint design owns safety checks and content filtering. This contract
  may expose safety-blocked response states but must not define lint rules or
  detection implementation.
- `partially_confirmed` handling is jointly owned by the Review State Machine
  design for transition semantics and the Safety/Lint design for which summary
  or version may be shown. This API contract only defines how the status appears
  on the wire.

## 2. URL Versioning and Compatibility

### 2.1 Version Convention

Pattern Structure MVP uses path-based API versioning:

```text
/api/v1/pattern-instances/{pattern_instance_id}/structure
/api/v1/pattern-instances/{pattern_instance_id}/structure/eligibility
/api/v1/pattern-instances/{pattern_instance_id}/structure/evidence
```

Rationale:

- Path versioning is explicit for frontend routing, logging, caching, and API
  gateway rules.
- Header-based versioning is not used for MVP because the frontend needs stable,
  inspectable URLs during design and QA.
- Clients may still send normal content negotiation headers such as
  `Accept: application/json`, but those headers do not select the contract
  version.

### 2.2 Breaking Changes

Breaking wire-format changes must be introduced under `/api/v2/`. Examples:

- removing or renaming a stable field;
- changing a stable field type;
- changing an enum literal used by stable fields;
- changing eligibility semantics in a way that alters whether clients may render
  structure;
- changing evidence citation requirements for structural claims.

V1 endpoints must remain backward compatible while supported. Additive fields
are allowed in V1 when they are optional, documented, and safe for older clients
to ignore.

### 2.3 Stable and Experimental Fields

Stable V1 fields are:

- top-level identity and metadata fields;
- `eligibility` fields;
- `evidence_window` fields;
- module `module`, `status`, and evidence reference fields;
- all error response fields;
- all `evidence_items` fields in the evidence endpoint.

Experimental V1 fields must be grouped under `experimental` objects. Clients may
render them only behind product-controlled feature flags. Experimental fields
must not be required for MVP rendering, review actions, or safety decisions.

`high_trust` is future extension only. V1 may expose `trust_tier: null` or
`trust_tier: "confirmed_by_user"`, but backend and frontend must not implement
high-trust rendering as an MVP eligibility path. If a future version adds
high-trust eligibility, it must be documented as an additive V1 extension only
when it does not change V1 behavior for existing clients; otherwise it belongs
in V2.

## 3. Endpoint List

### 3.1 Get Pattern Structure

```text
GET /api/v1/pattern-instances/{pattern_instance_id}/structure
```

Returns the aggregate Pattern Structure payload for one pattern instance. The
response always includes eligibility. If the pattern is not renderable, modules
must be empty or explicitly unavailable rather than guessed.

Query parameters:

| Field | Type | Required | Default | Purpose | Safety rationale |
| --- | --- | --- | --- | --- | --- |
| `module` | enum | no | `all` | Allows clients to request one structure dimension or all dimensions. | Reduces unnecessary data exposure when a UI needs only one dimension. |
| `include_evidence_samples` | boolean | no | `false` | Allows inline evidence excerpts for expanded evidence drawers. | Defaults to IDs-only to minimize raw text exposure. |

Allowed `module` values:

```text
all
scene_distribution
relationship_objects
temporal_structure
neighbor_patterns
```

### 3.2 Get Structure Eligibility

```text
GET /api/v1/pattern-instances/{pattern_instance_id}/structure/eligibility
```

Returns only the eligibility state for a pattern instance. The frontend should
use this endpoint when it needs to decide whether to show a Structure entry
point without loading the full structure payload.

Query parameters: none.

### 3.3 Get Structure Evidence References

```text
GET /api/v1/pattern-instances/{pattern_instance_id}/structure/evidence
```

Returns the evidence items referenced by the current structure payload. This is
a citation expansion endpoint, not a general evidence search endpoint.

Query parameters:

| Field | Type | Required | Default | Purpose | Safety rationale |
| --- | --- | --- | --- | --- | --- |
| `evidence_item_ids` | comma-separated UUID list | no | all referenced items | Limits response to selected evidence IDs. | Supports drawer-level expansion without exposing unrelated evidence. |
| `include_hidden_counts` | boolean | no | `false` | Includes counts for safety-filtered evidence. | Keeps redaction metadata opt-in while still allowing explicit audit views. |

## 4. Common Types

### 4.1 Identifiers and Timestamps

- UUID fields are serialized as strings.
- Timestamps are ISO 8601 strings with timezone offsets.
- Percentages are numbers from `0` to `1`, not display-formatted strings.
- Empty arrays are returned as `[]`; unknown scalar values are returned as
  `null` only when the field explicitly allows null.

### 4.2 Pattern Status

`pattern_status` references review state labels owned by the Review State
Machine design:

```text
candidate
confirmed
partially_confirmed
deferred
rejected
archived
```

The API may return these literals but does not define transition rules between
them.

### 4.3 Module Status

Each structure module uses the same status enum:

```text
available
insufficient_data
hidden_for_safety
not_applicable
not_requested
```

Purpose and safety rationale:

- `available` means the module has evidence-backed content safe to render.
- `insufficient_data` means the module should show an explicit empty state, not
  a guessed summary.
- `hidden_for_safety` means the Safety/Lint layer blocked display for that
  module.
- `not_applicable` means the dimension does not apply to the current eligible
  pattern or evidence window.
- `not_requested` means the client requested another module.

## 5. Eligibility Contract

```text
StructureEligibilityDto {
  state: EligibilityState
  can_show_structure: boolean
  pattern_status: PatternStatus
  trust_tier: TrustTier | null
  reason_code: EligibilityReasonCode
  display_message: string
  required_actions: EligibilityAction[]
  evidence_summary: EligibilityEvidenceSummary
  safety_summary: EligibilitySafetySummary
  cooldown_summary: EligibilityCooldownSummary | null
}
```

Fields:

| Field | Type | Purpose | Safety rationale |
| --- | --- | --- | --- |
| `state` | enum | Single render-state discriminator for frontend branching. | Prevents clients from inferring state from partial fields. |
| `can_show_structure` | boolean | Indicates whether full structure modules may be rendered. | Keeps eligibility enforcement backend-owned. |
| `pattern_status` | enum | Mirrors review status for display and audit. | References, but does not redefine, review semantics. |
| `trust_tier` | enum or null | Exposes current trust tier if available. | V1 does not use future high-trust rendering. |
| `reason_code` | enum | Machine-readable reason for state. | Supports consistent empty-state UX without ad hoc copy. |
| `display_message` | string | Neutral user-facing message. | Avoids client-generated explanations for blocked states. |
| `required_actions` | array | Indicates possible next UI actions such as review or add evidence. | Encourages explicit user control instead of automatic inference. |
| `evidence_summary` | object | Summarizes evidence sufficiency. | Makes sparse states explicit. |
| `safety_summary` | object | Summarizes safety blocking at a high level. | Makes restraint visible without exposing lint internals. |
| `cooldown_summary` | object or null | Summarizes rejected/cooldown timing when relevant. | Exposes cooldown state without redefining review transition rules. |

```text
EligibilityState =
  allowed |
  insufficient_evidence |
  rejected |
  unreviewed |
  deferred |
  crisis_safety_blocked |
  unsupported

TrustTier =
  confirmed_by_user
```

`TrustTier` intentionally excludes `high_trust` in V1 MVP. `high_trust` is a
reserved future extension and must not be required by V1 clients.

```text
EligibilityReasonCode =
  confirmed_pattern |
  partially_confirmed_pattern |
  insufficient_evidence |
  too_few_evidence_items |
  evidence_window_too_sparse |
  rejected_by_user |
  rejection_cooldown_active |
  archived |
  stale_evidence |
  awaiting_user_review |
  user_deferred_review |
  safety_blocked_crisis |
  unsupported_pattern_type |
  unsupported_source_type |
  structure_not_enabled

EligibilityAction =
  open_review |
  add_or_wait_for_evidence |
  view_evidence |
  no_action_available

EligibilityEvidenceSummary {
  evidence_count: integer
  minimum_required_count: integer
  source_chain_ids: uuid[]
  first_observed_at: timestamp | null
  last_observed_at: timestamp | null
}

EligibilitySafetySummary {
  safety_blocked: boolean
  blocked_reason: crisis | policy | unavailable | null
  hidden_evidence_count: integer
}

EligibilityCooldownSummary {
  rejected_at: timestamp
  cooldown_expires_at: timestamp
  can_reopen: boolean
}
```

`cooldown_summary` appears only when relevant to rejected or cooldown states.
The Review State Machine design owns cooldown semantics and reopen rules.

### 5.2 Required Eligibility States

#### Allowed

```text
state: allowed
can_show_structure: true
reason_code: confirmed_pattern | partially_confirmed_pattern
```

Allowed means structure modules may be returned when evidence and safety checks
permit. `partially_confirmed_pattern` may appear only under rules defined by the
Review State Machine and Safety/Lint designs.

#### Insufficient Evidence

```text
state: insufficient_evidence
can_show_structure: false
reason_code: too_few_evidence_items | evidence_window_too_sparse
```

The response must include evidence counts and minimum requirements. Modules must
not invent labels, timelines, relationship objects, or neighbors.

#### Rejected

```text
state: rejected
can_show_structure: false
reason_code: rejected_by_user
```

Rejected patterns must not return structure modules. The response may include a
neutral message that the pattern is hidden because the user rejected it.

#### Unreviewed

```text
state: unreviewed
can_show_structure: false
reason_code: awaiting_user_review
```

Unreviewed candidates must not show Pattern Structure. The client may show a
review entry point if allowed by the Review State Machine design.

#### Deferred

```text
state: deferred
can_show_structure: false
reason_code: user_deferred_review
```

Deferred patterns must not show structure while the review decision is pending
or intentionally postponed.

#### Crisis/Safety-Blocked

```text
state: crisis_safety_blocked
can_show_structure: false
reason_code: safety_blocked_crisis
```

Safety-blocked responses must not include raw excerpts or structural summaries.
The Safety/Lint design owns the detection rules and allowed alternative UI.

#### Unsupported

```text
state: unsupported
can_show_structure: false
reason_code: unsupported_pattern_type | unsupported_source_type | structure_not_enabled
```

Unsupported means the API recognizes the pattern instance but cannot produce
Pattern Structure for this pattern type, source type, or deployment setting.

## 6. Aggregate Structure Response

```text
PatternStructureResponse {
  api_version: "v1"
  pattern_instance_id: uuid
  pattern_key: string
  display_name: string
  pattern_status: PatternStatus
  eligibility: StructureEligibilityDto
  generated_at: timestamp | null
  generator_version: string | null
  source_chain_ids: uuid[]
  evidence_window: EvidenceWindowDto
  modules: PatternStructureModulesDto
  safety: StructureSafetyDto
  experimental: object | null
}
```

Fields:

| Field | Type | Purpose | Safety rationale |
| --- | --- | --- | --- |
| `api_version` | string | Identifies the wire contract version. | Helps clients reject unsupported shapes safely. |
| `pattern_instance_id` | UUID | Identifies the pattern instance being described. | Prevents mixing structure across patterns. |
| `pattern_key` | string | Stable product key for the pattern family. | Supports display grouping without exposing internal IDs. |
| `display_name` | string | User-facing pattern label. | Avoids client-generated labels. |
| `pattern_status` | enum | Mirrors review status. | Keeps rendering aligned with review state. |
| `eligibility` | object | Declares whether structure may be shown. | Backend-owned gate. |
| `generated_at` | timestamp or null | Shows when structure was generated. | Null for unavailable states prevents stale certainty. |
| `generator_version` | string or null | Audits generator version. | Supports traceability without exposing implementation details. |
| `source_chain_ids` | UUID array | Lists evidence chains used. | Enables audit and reproducibility. |
| `evidence_window` | object | Summarizes evidence span. | Makes sparse or partial evidence visible. |
| `modules` | object | Contains the four MVP dimensions. | Each claim remains module-scoped and evidence-backed. |
| `safety` | object | Reports redaction/filter metadata. | Makes safety restraint explicit. |
| `experimental` | object or null | Holds optional future fields. | Prevents unstable fields from polluting stable DTOs. |

```text
EvidenceWindowDto {
  evidence_count: integer
  first_observed_at: timestamp | null
  last_observed_at: timestamp | null
  evidence_item_ids: uuid[]
}
```

`evidence_item_ids` is the complete set of citation units used by the structure
payload after safety filtering. If `can_show_structure` is false, it must be
`[]` unless the eligibility state explicitly permits a count-only evidence view.

```text
StructureSafetyDto {
  raw_excerpt_filter_applied: boolean
  hidden_evidence_count: integer
  llm_fields_present: string[]
  deterministic_fields_present: string[]
}
```

`llm_fields_present` and `deterministic_fields_present` are audit metadata only.
They must not be used by the frontend to override eligibility or safety states.

```text
PatternStructureModulesDto {
  scene_distribution: SceneDistributionModuleDto
  relationship_objects: RelationshipObjectsModuleDto
  temporal_structure: TemporalStructureModuleDto
  neighbor_patterns: NeighborPatternsModuleDto
}
```

If a module is not available, its array fields must be empty and its `status`
must explain why.

## 7. Evidence Reference Contract

Every visible structural claim must point to one or more evidence item IDs.
EvidenceItem is the external citation unit for Pattern Structure.

Required rule:

- scene claims must include `representative_evidence_item_ids`;
- relationship object claims must include `representative_evidence_item_ids`;
- temporal concentration claims must include `representative_evidence_item_ids`;
- temporal timelines must include `timeline_evidence_item_ids`;
- neighbor claims must include `representative_pairs` with current and neighbor
  evidence item IDs;
- optional summary text must include `evidence_item_ids` for every sentence-level
  or card-level claim, or be omitted.

The API must never return a structural claim that cannot be traced to evidence
IDs. If raw text is hidden by safety filtering, the evidence ID may still appear
with `excerpt_visibility: hidden_for_safety` in the evidence endpoint.

## 8. Structure Dimension DTOs

### 8.1 Scene Distribution

```text
SceneDistributionModuleDto {
  module: "scene_distribution"
  status: ModuleStatus
  scenes: SceneDistributionItemDto[]
  empty_state: ModuleEmptyStateDto | null
}

SceneDistributionItemDto {
  scene_id: string
  label: string
  label_source: deterministic | llm_assisted
  evidence_count: integer
  evidence_share: number
  first_seen_at: timestamp | null
  last_seen_at: timestamp | null
  representative_evidence_item_ids: uuid[]
}
```

Field purposes and safety rationale:

- `scene_id` is a stable response-local identifier for UI keys and analytics.
- `label` names an observable situation or context, not a reason or origin.
- `label_source` distinguishes metadata-derived labels from LLM-assisted labels
  for auditability.
- `evidence_count` and `evidence_share` prevent overgeneralizing from a narrow
  evidence slice.
- `first_seen_at` and `last_seen_at` bound the observation period.
- `representative_evidence_item_ids` are required citations for the scene.

### 8.2 Relationship Objects

```text
RelationshipObjectsModuleDto {
  module: "relationship_objects"
  status: ModuleStatus
  objects: RelationshipObjectItemDto[]
  empty_state: ModuleEmptyStateDto | null
}

RelationshipObjectItemDto {
  object_id: string
  display_label: string
  object_type: person | role | group | unknown
  label_source: metadata | llm_assisted | user_label
  evidence_count: integer
  first_seen_at: timestamp | null
  last_seen_at: timestamp | null
  representative_evidence_item_ids: uuid[]
  privacy_level: normal | sensitive | hidden
}
```

Field purposes and safety rationale:

- `object_id` is response-local unless the Backend Domain Model explicitly maps
  it to a stable internal object.
- `display_label` may name a user-known person, role, group, or unknown object,
  but must not assign blame or intent.
- `object_type` keeps rendering concrete without making assumptions beyond
  available evidence.
- `label_source` exposes whether the label came from metadata, user label, or
  LLM-assisted extraction.
- `privacy_level` lets the frontend avoid exposing sensitive labels by default.
- `representative_evidence_item_ids` are required citations for the association.

### 8.3 Temporal Structure

```text
TemporalStructureModuleDto {
  module: "temporal_structure"
  status: ModuleStatus
  first_observed_at: timestamp | null
  last_observed_at: timestamp | null
  recent_density: RecentDensityDto
  concentration_points: TemporalConcentrationPointDto[]
  timeline_evidence_item_ids: uuid[]
  empty_state: ModuleEmptyStateDto | null
}

RecentDensityDto {
  last_7_days: integer
  last_30_days: integer
  last_90_days: integer
}

TemporalConcentrationPointDto {
  bucket_start: timestamp
  bucket_end: timestamp
  granularity: day | week | month
  evidence_count: integer
  representative_evidence_item_ids: uuid[]
}
```

Field purposes and safety rationale:

- `first_observed_at` and `last_observed_at` describe the evidence window, not a
  lifetime trait.
- `recent_density` reports counts only and must not imply importance.
- `concentration_points` show where accepted evidence clusters in time.
- `timeline_evidence_item_ids` gives an ordered citation list for inspection.
- `representative_evidence_item_ids` are required for each concentration point.

### 8.4 Neighbor Patterns

```text
NeighborPatternsModuleDto {
  module: "neighbor_patterns"
  status: ModuleStatus
  window_policy: NeighborWindowPolicyDto
  neighbors: NeighborPatternItemDto[]
  empty_state: ModuleEmptyStateDto | null
}

NeighborWindowPolicyDto {
  same_source_ref: boolean
  same_thread: boolean
  time_window_hours: integer
  same_context_tag: boolean
}

NeighborPatternItemDto {
  pattern_instance_id: uuid
  pattern_key: string
  display_name: string
  pattern_status: confirmed | partially_confirmed
  cooccurrence_count: integer
  shared_source_count: integer
  first_cooccurred_at: timestamp | null
  last_cooccurred_at: timestamp | null
  connector_label: string
  representative_pairs: NeighborEvidencePairDto[]
}

NeighborEvidencePairDto {
  current_evidence_item_id: uuid
  neighbor_evidence_item_id: uuid
  relation_basis: same_source_ref | same_thread | time_window | same_context_tag
}
```

Field purposes and safety rationale:

- `window_policy` makes the co-occurrence rule inspectable.
- `neighbors` is a flat list. It must not be represented as graph, network,
  topology, connected-node, or edge data.
- `pattern_status` is limited to reviewed statuses allowed by the Review State
  Machine and Safety/Lint designs.
- `cooccurrence_count` and `shared_source_count` describe proximity only.
- `connector_label` must be neutral and must not imply explanation or priority.
- `representative_pairs` are required citations connecting the current pattern's
  evidence to the neighbor pattern's evidence.

## 9. Empty and Partial State DTO

```text
ModuleEmptyStateDto {
  code: ModuleEmptyStateCode
  display_message: string
  evidence_count: integer
  minimum_required_count: integer | null
}

ModuleEmptyStateCode =
  not_enough_accepted_evidence |
  no_safe_evidence_to_show |
  no_confirmed_neighbors |
  extraction_not_available |
  not_requested
```

Fields:

| Field | Purpose | Safety rationale |
| --- | --- | --- |
| `code` | Machine-readable empty-state reason. | Prevents client-side guessing. |
| `display_message` | Neutral user-facing copy. | Keeps copy consistent and non-persuasive. |
| `evidence_count` | Count available to the module. | Makes sparse data explicit. |
| `minimum_required_count` | Threshold when applicable. | Shows restraint criteria without exposing internals. |

Recommended messages:

- `not_enough_accepted_evidence`: `Not enough accepted evidence yet.`
- `no_safe_evidence_to_show`: `Some evidence is hidden for safety.`
- `no_confirmed_neighbors`: `No confirmed nearby patterns found in the current evidence window.`
- `extraction_not_available`: `This section is not available for the current evidence.`
- `not_requested`: `This section was not requested.`

## 10. Evidence Endpoint Response

```text
PatternStructureEvidenceResponse {
  api_version: "v1"
  pattern_instance_id: uuid
  evidence_items: EvidenceItemDto[]
  hidden_evidence_count: integer
}

EvidenceItemDto {
  id: uuid
  source_type: chat | journal | checkin | imported_note | unknown
  source_ref: string
  occurred_at: timestamp | null
  excerpt: string | null
  excerpt_visibility: visible | hidden_for_safety | unavailable
  is_verbatim: boolean
  interpretation: string | null
  deep_link: string | null
}
```

Fields:

| Field | Purpose | Safety rationale |
| --- | --- | --- |
| `id` | Citation target used by structure modules. | Keeps evidence references stable. |
| `source_type` | Coarse source category. | Supports context without exposing private storage details. |
| `source_ref` | Source reference for dedupe and audit. | May be opaque to avoid leaking source internals. |
| `occurred_at` | Evidence timestamp when available. | Supports temporal inspection. |
| `excerpt` | User-facing evidence text when safe. | Null when hidden or unavailable. |
| `excerpt_visibility` | Explains whether excerpt can be shown. | Prevents clients from treating null as missing data only. |
| `is_verbatim` | Indicates whether excerpt is exact source text. | Avoids overstating generated or transformed snippets. |
| `interpretation` | Existing evidence interpretation when safe. | Must not introduce new structural claims. |
| `deep_link` | Optional link to the source item. | Lets users inspect evidence under existing permissions. |

## 11. Error Response Contract

HTTP status codes:

- `400` for invalid query parameters.
- `401` for unauthenticated requests.
- `403` for authenticated users without access to the pattern instance.
- `404` when the pattern instance does not exist or is not visible to the user.
- `409` when structure generation is temporarily inconsistent with current
  review state.
- `422` when the request is valid but structure cannot be produced for the
  requested module.
- `500` for unexpected server errors.

```text
PatternStructureErrorResponse {
  api_version: "v1"
  error: PatternStructureErrorDto
}

PatternStructureErrorDto {
  code: PatternStructureErrorCode
  message: string
  pattern_instance_id: uuid | null
  request_id: string
  retryable: boolean
  details: object | null
}

PatternStructureErrorCode =
  invalid_request |
  pattern_not_found |
  forbidden |
  structure_not_available |
  insufficient_evidence |
  safety_blocked |
  unsupported_module |
  stale_structure |
  internal_error
```

Fields:

| Field | Purpose | Safety rationale |
| --- | --- | --- |
| `code` | Stable machine-readable error. | Avoids parsing messages. |
| `message` | Neutral user- or developer-facing text. | Must not add unsupported explanation. |
| `pattern_instance_id` | Echoes target when safe. | Helps debugging without revealing other users' data. |
| `request_id` | Trace identifier. | Supports audit and incident response. |
| `retryable` | Indicates whether retry may succeed. | Prevents repeated unsafe or futile calls. |
| `details` | Optional structured diagnostics. | Must not include raw blocked evidence or lint internals. |

Ineligible pattern states should usually return `200` with an eligibility
payload, not an error. Errors are reserved for invalid, unauthorized, missing, or
operationally failed requests.

## 12. Internal Domain Consumption

The API consumes internal domain objects through read-model mapping only. It
does not define persistence entities or require a storage shape.

Expected internal inputs include existing pattern instance data, active evidence
chains, evidence items, review status, approved source metadata, and safety
filter outputs. The Backend Domain Model design decides how those inputs are
stored and related internally.

Mapping principles:

- Internal IDs may be transformed into opaque external UUIDs where needed.
- Internal enum names do not have to match wire enum names, but the mapper must
  emit the exact literals defined here.
- Internal confidence or strength scores may be used for audit and eligibility
  decisions owned elsewhere, but this contract must not expose persuasive score
  copy for Pattern Structure MVP.
- Generated module text must be discarded or hidden when it lacks evidence item
  citations.
- Safety-filtered evidence may contribute to counts only when the Safety/Lint
  design permits count-level disclosure.

## 13. Non-Goals

This API contract does not include:

- Java code;
- frontend code;
- regex definitions;
- Pattern Evolution;
- Understanding tab endpoints for MVP;
- graph-like or network-like neighbor payloads;
- review transition rules;
- safety lint implementation;
- internal persistence entities.

Understanding tab work is Phase 2 later, not Pattern Structure MVP.
