# Pattern Structure MVP Backend Domain Model

> Project: InnerFlow Pattern Engine / Phase 2 Product Design Round 1  
> Status: design draft  
> Scope: backend domain model and invariants only  
> Last updated: 2026-05-31

---

## 1. Purpose

This document defines the internal backend domain concepts, relationships, and invariants for the Pattern Structure MVP.

Pattern Structure is a read-only mirror layer over patterns the user has already accepted enough to inspect. It organizes the observable shape of a pattern across evidence: where it appears, which relationship objects appear around it, when it appears, and which other reviewed patterns occur nearby.

This document owns the internal object model. It does not define Java implementation, database migrations, repository classes, service classes, or external wire DTOs. The API Contract design owns external response shapes. The Review State Machine design owns review transitions. The Safety/Lint design owns safety checks and display lint mechanics.

---

## 2. Source Inputs Leaned On

This design leans most heavily on these source sections:

- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md`
  - Sections 0–2 for purpose, hard scope, eligibility, and the V1 `high_trust` boundary.
  - Sections 4–5 for source data mapping and four module concepts.
  - Sections 7–9 for evidence rules, safety rules, stale data handling, and API-facing behavior.
- `docs/product/phase-2-pattern-understanding-plan.md`
  - Sections 4–7 for Pattern Structure as a separate layer, trust boundary, shape-not-origin framing, and the four structure dimensions.
  - Sections 9–10 for MVP sequencing and the principle that confirmed patterns become more inspectable, not more authoritative.
- `docs/product/pattern-structure-ux-audit.md`
  - Sections 4, 7, 8, and 9 for reviewed-pattern constraints, Pattern Card detail placement, evidence-linked structure, safety copy, and the decision that Understanding is Phase 2 later.

---

## 3. Product Boundary

Pattern Structure describes observable evidence organization only. It must not infer psychological origin, hidden motives, diagnosis, treatment, personality type, attachment style, trauma origin, symptoms, severity, or root cause.

The backend model should preserve this boundary by keeping all structure objects evidence-linked and by representing neighbor patterns as local co-occurrence hints only. A neighbor is not a dependency, explanation, influence edge, graph node, topology element, or causal relation.

The MVP serves Pattern Structure only for user-confirmed or partially confirmed patterns. The broader Understanding tab is Phase 2 later and is not part of this MVP model except as a future consumer of reviewed PatternInstance records.

---

## 4. Domain Concepts

### 4.1 PatternInstance

`PatternInstance` is the central reviewed pattern record for one user. It represents a recurring pattern label and summary as recognized through the Pattern Engine and user review flow.

Core responsibilities:

- Identifies the user, pattern key, domain, and active evidence chain.
- Carries the review status used by the eligibility gate.
- Carries audit metadata such as confidence, first/last observed timestamps, and last reviewed timestamp.
- Provides the neutral user-facing summary that Structure may display as context, subject to safety/lint rules.

Structure-specific relationship:

- One `PatternInstance` has at most one active `EvidenceChain` for Structure generation at a time.
- One eligible `PatternInstance` may have a generated `PatternStructure` view derived from its active chain.
- One `PatternInstance` may have many historical evidence chains, but MVP availability is determined by the active chain.

The model should treat confidence as audit metadata, not as a persuasive user-facing reason to trust the structure.

### 4.2 EvidenceChain

`EvidenceChain` is the ordered evidence set that supports a PatternInstance at a specific generation point.

Core responsibilities:

- Records chain identity, generator version, generated timestamp, and ordered evidence item references.
- Anchors Structure generation to a stable input set.
- Allows stale-structure detection when the active chain changes after a structure was generated.

Structure-specific relationship:

- One active `EvidenceChain` belongs to one `PatternInstance`.
- One `EvidenceChain` contains many `EvidenceItem` references.
- One generated `PatternStructure` must declare which evidence chain it was derived from internally, even if the API exposes that only conceptually.

Chain-level strength may be retained for audit/debugging, but must not become user-facing copy that makes the pattern feel more certain.

### 4.3 EvidenceItem

`EvidenceItem` is the citation unit. It points to a source excerpt or source metadata used to support a PatternInstance.

Core responsibilities:

- Preserves source type, source reference, occurrence time, excerpt or redacted excerpt state, and already-approved metadata.
- Indicates whether text is verbatim or interpreted.
- Carries safety-relevant flags or references to safety decisions where available.

Structure-specific relationship:

- Every visible PatternStructure aggregate must trace back to one or more EvidenceItems.
- A StructureSection may reference multiple representative EvidenceItems.
- A NeighborPattern must reference paired evidence from the current pattern and the neighbor pattern when representative examples are shown.

If raw evidence cannot be shown because of safety filtering, the EvidenceItem still remains the internal citation unit, but the external view must use a safe hidden/redacted state rather than exposing unsafe text.

### 4.4 PatternStructure

`PatternStructure` is the generated internal read model for one eligible PatternInstance and one active EvidenceChain.

Core responsibilities:

- Groups the four MVP sections: Scene Distribution, Relationship Objects, Temporal Structure, and Neighbor Patterns.
- Records generation metadata such as generated time, generator version, and source evidence chain identity.
- Records availability status for the structure as a whole.
- Keeps a neutral disclaimer or disclaimer reference for API/view composition.

Structure-specific relationship:

- One PatternStructure belongs to one PatternInstance.
- One PatternStructure is generated from exactly one active EvidenceChain snapshot.
- One PatternStructure contains zero or more StructureSections, with at most one section per MVP section type.
- One PatternStructure may contain zero or more NeighborPattern entries as part of the Neighbor Patterns section.

PatternStructure should be modeled as a read artifact, not as a new source of truth about the user. If underlying evidence, review state, or safety status changes, the structure becomes unavailable or stale until regenerated or hidden according to availability invariants.

### 4.5 StructureSection

`StructureSection` is one module within PatternStructure.

MVP section types:

- Scene Distribution.
- Relationship Objects.
- Temporal Structure.
- Neighbor Patterns.

Core responsibilities:

- Holds module-level availability such as available, insufficient evidence, stale, or hidden for safety.
- Holds neutral aggregate rows or labels computed from evidence.
- Holds representative EvidenceItem references for each visible aggregate.
- Separates deterministic aggregates from compact LLM-assisted labels when labels are needed.

The section model should not encode external field names. It should represent concepts that can later be mapped into the API response by the API Contract design.

### 4.6 NeighborPattern

`NeighborPattern` represents another reviewed PatternInstance that appears near the current PatternInstance in local evidence windows.

Core responsibilities:

- References the neighboring PatternInstance.
- Stores deterministic co-occurrence facts such as count, shared source count, and representative evidence pairs.
- Optionally carries a short neutral connector label after deterministic co-occurrence has already been established.

Invariants:

- NeighborPattern is only allowed for neighbor instances whose status is confirmed or partially confirmed.
- NeighborPattern must not be modeled as a graph edge, causal edge, influence relation, or explanation.
- NeighborPattern must be omitted when the current pattern or neighbor pattern fails eligibility, evidence coverage, safety, or stale-chain checks.

### 4.7 UserReview and ReviewStatus

`UserReview` is the user's review record or latest review decision for a PatternInstance. `ReviewStatus` is the normalized status consumed by the Structure eligibility gate.

The domain model may reference statuses such as:

- candidate.
- confirmed.
- partially_confirmed.
- rejected.
- deferred.
- archived.
- abstained or abstain-boundary, if represented internally.

This document does not define every transition between statuses. Transition semantics belong to the Review State Machine design.

For Pattern Structure V1, only `confirmed` and `partially_confirmed` are eligible. `partially_confirmed` display semantics are jointly owned by the Review State Machine design, which defines how the status is reached, and the Safety/Lint design, which defines which summary/version may be shown.

---

## 5. Persistence, Generation, and Caching

### 5.1 Persisted Objects

These objects should be treated as durable domain state:

- PatternInstance.
- EvidenceChain identity and ordered EvidenceItem references.
- EvidenceItem records or references into the source evidence store.
- UserReview and normalized ReviewStatus.
- Safety flags, crisis flags, source redaction state, and review timestamps where already part of the source model.
- Rejection cooldown audit records keyed by rejected `(pattern_key, domain)`, including rejection timestamp, cooldown expiry timestamp, rejected `source_ref` set or hash, and Jaccard score / comparison metadata for suppressed re-surfacing attempts.

Persisted state should be sufficient to determine eligibility and regenerate PatternStructure without relying on a previous generated artifact.

Cooldown semantics are owned by the Review State Machine design. This domain model only requires durable audit state so rejected patterns do not quietly regain Structure availability through cached or repeated evidence.

### 5.2 Generated Objects

These objects are generated read artifacts:

- PatternStructure.
- StructureSection aggregates.
- Scene labels when metadata is too noisy and LLM assistance is allowed.
- Relationship object labels when structured metadata is unavailable and extraction is allowed.
- Neighbor connector labels after deterministic co-occurrence is established.

Generated objects must be reproducible from persisted inputs plus generator version. They should not become independent claims that survive rejection, safety blocking, or evidence-chain replacement.

### 5.3 Cached Objects

PatternStructure and StructureSections may be cached for performance if the cache key includes at least:

- PatternInstance identity.
- Active EvidenceChain identity or chain version.
- Pattern Structure generator version.
- Safety/lint policy version if safety decisions can alter visibility.
- ReviewStatus or review version.

Cache entries must be invalidated or hidden when the PatternInstance status changes, the active EvidenceChain changes, safety flags change, or the generator policy changes in a way that affects visible content.

---

## 6. Eligibility Invariants

Pattern Structure generation must start with an explicit eligibility check.

V1 MVP eligibility:

```text
eligible_for_structure =
  pattern_instance.review_status in [confirmed, partially_confirmed]
```

The source MVP spec documents `high_trust` as a future extension point. In this domain model, `high_trust` may exist as a reserved concept or audit field, but it must not make a PatternInstance eligible in V1 MVP runtime behavior. V1 must not infer `high_trust` automatically from a persuasive summary, high confidence score, repeated evidence, or LLM output.

Non-eligible statuses include candidate, rejected, deferred, archived, abstained, unknown, low-trust, and any status without a current reviewed acceptance signal. For these statuses, PatternStructure should not be generated. If a stale cached structure exists, it must not be served as available.

Eligibility is necessary but not sufficient. Evidence coverage, safety, and stale-chain checks may still make Structure unavailable.

---

## 7. Evidence Coverage Invariants

Pattern Structure is only inspectable when its visible claims are covered by accepted evidence.

Required invariants:

- Every visible aggregate must reference at least one EvidenceItem.
- Every visible section should have enough supporting EvidenceItems to avoid making sparse evidence look authoritative.
- Representative examples must come from the active EvidenceChain for the current PatternInstance, except neighbor representative pairs, which must also cite the neighbor's active eligible chain.
- Labels produced with LLM assistance must be grounded in the referenced EvidenceItems and must not introduce claims absent from evidence.
- Temporal aggregates must be derived from EvidenceItem occurrence timestamps or approved fallback timestamps.
- Relationship objects must come from source metadata, user labels, or conservative evidence-grounded extraction.
- NeighborPattern co-occurrence must be established deterministically before any connector label is generated.

If a section lacks enough accepted evidence, that section should be marked internally as insufficient rather than filled with inferred content. If too many sections are insufficient for a useful view, the whole PatternStructure may be unavailable with an insufficient-evidence reason, as defined by the API Contract design.

---

## 8. Safety and Crisis Handling

Safety and lint mechanics belong to the Safety/Lint design. The domain model must still enforce these safety invariants:

- Crisis-flagged evidence must not be shown inside Pattern Structure.
- Safety-blocked evidence must not be exposed as raw excerpts.
- A StructureSection whose required citations are all blocked should be hidden for safety or marked unavailable, not summarized around the block.
- PatternStructure must not become a route around evidence redaction.
- Generated labels must pass safety/lint policy before becoming visible.
- Partially confirmed patterns must defer to Safety/Lint rules for which summary/version may be shown.

Safety flags apply at both evidence and generated-artifact levels. Evidence-level flags determine whether source material can be cited or excerpted. Generated-artifact flags determine whether labels, summaries, or section bodies can be shown. When either layer blocks required content, the external view should present a restrained unavailable or hidden state.

---

## 9. Stale, Rejected, and Deferred Availability

### 9.1 Stale Evidence

A PatternStructure is stale when it was generated from an EvidenceChain that is no longer the active chain for the PatternInstance, or when safety/lint policy changes make its generated content outdated.

Stale structures should not be served as fresh. The backend may return an unavailable/stale availability state or regenerate from the current active chain after all eligibility and safety checks pass.

### 9.2 Rejected Patterns

Rejected patterns are not eligible for Pattern Structure. Rejection should hide any generated structure and prevent cached artifacts from being served.

Rejected PatternInstances may remain persisted for audit, cooldown, or future substantially-new-evidence handling, but their Structure availability is false until review semantics explicitly produce a new eligible PatternInstance or status. The details of rejection cooldown and re-entry belong to the Review State Machine design.

### 9.3 Deferred Patterns

Deferred patterns are not eligible for Pattern Structure. Deferral means the user has not accepted enough authority for a structure view.

A deferred PatternInstance may retain its EvidenceChain and EvidenceItems, but PatternStructure should not be generated or shown. If the user later confirms or partially confirms it through valid review transitions, the structure should be generated from the then-active evidence chain, not resurrected blindly from an old cache.

### 9.4 Archived or Abstained Patterns

Archived, abstained, abstain-boundary, low-trust, and unknown-trust patterns are unavailable for Pattern Structure in V1 MVP. The absence of structure should be treated as product restraint, not as an error.

---

## 10. Internal-to-API Mapping

This domain model maps to the API contract as an external view, not as a one-to-one serialization of internal objects.

Conceptual mapping:

- PatternInstance provides the selected pattern identity, display context, review status, neutral summary, evidence count, and observed date range.
- EvidenceChain provides the source chain identity/version used for generation and stale checks.
- EvidenceItem provides citation anchors, representative examples, redaction state, and source timing.
- PatternStructure provides the overall availability, generated metadata, section collection, and disclaimer context.
- StructureSection provides the conceptual modules rendered as cards or panels.
- NeighborPattern provides reviewed nearby-pattern hints with representative paired evidence.
- UserReview/ReviewStatus provides eligibility state and review labels.

The API Contract document owns external endpoint paths, response field names, error codes, and DTO shape. This document only requires that the external view preserve the internal invariants: no unavailable object is serialized as available, no visible aggregate lacks evidence coverage, and no neighbor relation is represented as causal or therapeutic interpretation.

---

## 11. Open Questions for Implementation Planning

1. What is the minimum evidence threshold for each section before it becomes available rather than insufficient?
2. Should PatternStructure be persisted as a generated artifact, cached only, or regenerated on demand for the first MVP implementation?
3. What exact cache invalidation event is emitted when ReviewStatus, active EvidenceChain, or safety policy changes?
4. How should the backend represent redacted EvidenceItems so the API can show evidence-linked restraint without exposing unsafe excerpts?
5. Which generator version metadata is required for deterministic audit and future replay?
6. Should partially confirmed patterns use the same active EvidenceChain as confirmed patterns, or a reviewed subset selected by the Review State Machine?
7. What internal availability reason taxonomy should be shared with the API Contract without coupling domain objects to wire names?
8. How should historical EvidenceChains be retained for audit while ensuring stale structures never become visible by accident?
9. What is the implementation boundary between deterministic extraction and allowed compact LLM labels for scene and relationship sections?
10. What test fixtures are needed to cover eligibility, stale-chain handling, safety-blocked evidence, crisis-flagged evidence, and neighbor co-occurrence without introducing graph semantics?

---

## 12. Non-Goals

This design does not include:

- Java classes or implementation code.
- Database migrations or schema DDL.
- Repository, service, controller, or package design.
- Pattern Evolution.
- Pattern Understanding tab behavior for MVP.
- Cross-pattern graphs, topology, network maps, influence maps, or causal chains.
- Psychological cause, diagnosis, treatment, personality typing, attachment style, symptoms, severity, trauma, or root-cause modeling.
