# Pattern Structure API Implementation Plan

## 0. Purpose

This document is the implementation-readiness plan for the Pattern Structure V1 API endpoints. It translates the Phase 2 design contracts into a docs-only backend implementation sequence for the existing Spring/JPA Pattern Engine codebase.

It is not Java code, a DTO definition file, a database migration, or a frontend plan.

## 1. Inputs and Current Code Shape

Design inputs:

- `docs/product/phase-2-design-round-1/01-pattern-structure-api-contract.md`
- `docs/product/phase-2-design-round-1/02-pattern-structure-domain-model.md`
- `docs/product/phase-2-design-round-1/04-pattern-review-state-machine.md`
- `docs/product/phase-2-design-round-1/05-pattern-structure-safety-lint-eval.md`

Relevant existing backend shape:

- `PatternController` currently exposes legacy Pattern Engine paths under `/api/pattern`, including instance listing, evidence loading, review, edit, and dev refresh.
- `PatternInstanceRepository`, `EvidenceChainRepository`, and `EvidenceItemRepository` already provide the core read model for ownership, active evidence chains, and evidence items.
- `PatternReviewService` owns review/edit transitions today and should remain the write-side authority for review state changes.
- `PatternDiscoveryService` owns refresh-time evidence chain creation and confidence updates; the Structure API should consume active evidence rather than trigger discovery by default.
- `LanguageFirewall` is the existing hard language-safety utility, but Pattern Structure needs module-level and response-level lint decisions beyond the current summary firewall.

## 2. Endpoint Implementation Order

Implement endpoints in this order so each slice can be tested without widening MVP scope.

1. `GET /api/v1/pattern-instances/{pattern_instance_id}/structure/eligibility`
   - Start with the smallest endpoint because it has no module payload and exercises ownership, review status gating, evidence counts, and safety-blocked state selection.
   - This endpoint becomes the canonical backend-owned answer for whether the frontend may show the Structure entry point.
2. `GET /api/v1/pattern-instances/{pattern_instance_id}/structure/evidence`
   - Add citation expansion after eligibility so evidence references can be loaded safely and independently.
   - Keep it scoped to the current active evidence chain and optional `evidence_item_ids`; do not turn it into a search endpoint.
3. `GET /api/v1/pattern-instances/{pattern_instance_id}/structure?module=...&include_evidence_samples=false`
   - Add the aggregate endpoint with eligibility first, then module shells and unavailable states.
   - Return `not_requested` for modules outside the requested `module` filter.
4. Module payload population for `scene_distribution`, `relationship_objects`, `temporal_structure`, and `neighbor_patterns`
   - Populate each module only from evidence-backed, safety-checked data.
   - Ship deterministic or conservative module output first; do not add speculative generation to fill gaps.
5. Optional inline evidence samples via `include_evidence_samples=true`
   - Add only after the evidence endpoint and safety-hidden excerpt behavior are stable.
   - Default remains IDs-only to minimize raw text exposure.

## 3. Controller Responsibilities

Add a new V1 Pattern Structure controller instead of expanding the existing `/api/pattern` controller contract.

The controller should own:

- Path mapping for `/api/v1/pattern-instances/{pattern_instance_id}/structure`, `/structure/eligibility`, and `/structure/evidence`.
- Authentication principal extraction and passing the current `userId` into the service layer.
- Request parsing for `module`, `include_evidence_samples`, `evidence_item_ids`, and `include_hidden_counts`.
- Basic request validation such as unknown module values and malformed UUID lists.
- HTTP response status selection for missing/unauthorized pattern instances, invalid query values, and successful restrained states.

The controller should not own:

- Eligibility rules.
- Review transition semantics.
- Evidence-chain staleness checks.
- Safety/lint decisions.
- DTO assembly beyond delegating to the application service.
- Any Pattern Understanding tab behavior.

Implementation note: successful safety restraint should normally be represented as a valid API payload, not a transport failure. Reserve non-2xx responses for request, auth, ownership, or server errors.

## 4. Service Responsibilities

Introduce a read-side Pattern Structure application service that orchestrates existing repositories and new structure-specific helpers.

The service should own:

- Loading the owned `PatternInstance` by `userId` and `pattern_instance_id`.
- Delegating review-status interpretation to a small eligibility component aligned with the Review State Machine design.
- Loading the active `EvidenceChain` and its `EvidenceItem` rows.
- Calculating evidence sufficiency and evidence-window metadata.
- Running or consulting safety/lint checks before exposing summaries, module labels, excerpts, or aggregate text.
- Producing internal view models that map cleanly to the API DTO contract.
- Ensuring stale generated structure is never returned as fresh.

Suggested service split:

- `PatternStructureService`: endpoint-facing orchestration for eligibility, aggregate structure, and evidence reference payloads.
- `PatternStructureEligibilityService`: deterministic eligibility calculation from review status, evidence state, safety state, and feature availability.
- `PatternStructureEvidenceService`: active-chain evidence loading, filtering, ordering, hidden-count accounting, and evidence-reference selection.
- `PatternStructureSafetyService`: module-level and response-level safety/lint evaluation using existing firewall behavior plus new Pattern Structure checks.
- `PatternStructureAssembler`: converts safe internal module results into the aggregate response shape.

The service layer must not mutate review state, create new `PatternInstance` rows, or trigger Pattern Discovery refresh as a side effect of GET requests.

## 5. Repository Responsibilities

Use existing repositories first; add narrow read methods only where the endpoint contract needs them.

Existing repositories:

- `PatternInstanceRepository` should remain the source for ownership checks, review status, active `evidenceChainId`, timestamps, summary, user note, confidence, hidden flag, and schema version.
- `EvidenceChainRepository` should load the active evidence chain by `PatternInstance.evidenceChainId` and provide generated-at/generator-version metadata for stale checks and audit fields.
- `EvidenceItemRepository` should load evidence items by active chain ID and eventually by selected evidence IDs within that active chain.

Potential narrow additions:

- A method to load active-chain evidence items filtered by item IDs, if in-memory filtering is not adequate.
- A method to count active-chain evidence items without loading excerpts, if evidence chains become large.
- A method to load pattern instances by user plus ID directly, replacing controller/service post-filtering without changing semantics.

Repository methods should not encode product eligibility decisions. They should return facts; services decide availability.

## 6. DTO Mapping Plan

Create DTOs in implementation PRs that mirror the API contract exactly, but keep internal domain objects separate from wire objects.

Mapping rules:

- `PatternInstance.id` maps to `pattern_instance_id`.
- `PatternInstance.patternKey`, `domain`, `status`, `personalizedSummary`, `userNote`, `firstObservedAt`, `lastObservedAt`, and `lastReviewedAt` provide identity, review, and display context.
- `PatternInstance.evidenceChainId` plus `EvidenceChain.id` map to `source_chain_ids` and structure generation metadata.
- `EvidenceChain.generatedAt` and `generatorVersion` support generated-at, stale checks, and audit/debug metadata where the API contract allows it.
- `EvidenceItem.id`, `sourceType`, `sourceRef`, `occurredAt`, `excerpt`, `isVerbatim`, and `interpretation` map only through evidence-reference DTOs or allowed inline evidence samples.
- Enum values must serialize using contract literals, not Java enum renaming or display labels.
- Unknown scalar fields should remain `null` only where the contract allows null; empty collections should serialize as `[]`.

DTO assembly order:

1. Build `StructureEligibilityDto` for every response path.
2. If `can_show_structure=false`, return no generated module content; include only explicit unavailable or empty module state where the contract requires it.
3. If `can_show_structure=true`, build requested module envelopes with `available`, `insufficient_data`, `hidden_for_safety`, `not_applicable`, or `not_requested` status.
4. Attach evidence references by ID first; attach excerpts only when the endpoint and safety policy allow it.
5. Apply final response-level safety checks after module assembly so combined fields do not create a new unsafe implication.

Do not expose JPA entities directly from the new V1 endpoints.

## 7. Eligibility Calculation Plan

Eligibility should be deterministic, backend-owned, and shared by all three endpoints.

Calculation inputs:

- Pattern ownership and existence.
- `PatternInstance.hidden`.
- `PatternInstance.status`.
- Active `PatternInstance.evidenceChainId`.
- Active-chain evidence count.
- Evidence window dates from `EvidenceItem.occurredAt`, plus `PatternInstance.firstObservedAt` and `lastObservedAt` for summary metadata.
- Safety/lint block state for crisis, policy, hidden excerpts, and unavailable checks.
- Structure feature availability for the pattern/domain/source mix.

Status handling:

- `confirmed` may be eligible when evidence and safety checks pass.
- `partially_confirmed` may be eligible only with partial-state caution and must not broaden the user's confirmed wording; prefer user-authored edited wording when display text is allowed.
- `candidate` maps to unreviewed/awaiting-review and is not eligible for user-facing structure.
- `deferred`, if introduced by the review state implementation, is not eligible while pending.
- `rejected` is not eligible and must not serve cached structure.
- `archived` is not eligible for V1 MVP structure.
- `high_trust` remains a future extension only and must not become an MVP eligibility path.

Evidence handling:

- Use a minimum active-chain evidence threshold aligned with the API contract's `minimum_required_count` field and current engine invariant of at least three surfaced evidence items.
- If no active chain exists, return `insufficient_evidence` or `unsupported` depending on whether the pattern can ever support structure.
- If evidence is too sparse or outside the usable window, return `insufficient_evidence` with reason codes such as `too_few_evidence_items` or `evidence_window_too_sparse`.
- If the active chain changed after a structure artifact was generated, treat the artifact as stale and either regenerate safely or return a stale/unavailable reason rather than serving it.

Safety handling:

- If crisis or policy rules block the whole structure, return `crisis_safety_blocked` or the appropriate safety-blocked reason with `can_show_structure=false`.
- If only a module is blocked, keep pattern-level eligibility allowed only when unaffected modules do not depend on the blocked module.
- Safety-blocked states should produce neutral display messages from the backend; the frontend should not invent explanatory copy.

## 8. Evidence Reference Loading Plan

The evidence endpoint is a citation expansion endpoint for the current structure payload.

Loading flow:

1. Load the owned `PatternInstance`.
2. Calculate eligibility enough to know whether evidence references may be exposed.
3. Load the active `EvidenceChain` by `PatternInstance.evidenceChainId`; if missing, return an empty evidence list with eligibility metadata rather than falling back to historical chains.
4. Load only `EvidenceItem` rows for the active chain.
5. If `evidence_item_ids` is present, intersect the requested IDs with the active-chain item IDs.
6. Sort evidence by `occurredAt` using the API contract's expected order, with a deterministic tie-breaker such as evidence item ID.
7. Apply safety filtering before returning excerpt, interpretation, source reference, or hidden-count metadata.
8. Return hidden counts only when `include_hidden_counts=true` and policy allows aggregate disclosure.

Exposure rules:

- Never load unrelated evidence from other chains or other pattern instances.
- Never use rejected pattern evidence for structure display.
- Do not expose raw excerpts by default from the aggregate structure endpoint.
- Do not expose crisis-filtered or safety-hidden representative excerpts.
- Counts may remain visible only when policy allows counting without excerpt display.

## 9. Safety-Blocked Response Handling

Safety-blocked responses are successful restraint states, not generic failures.

Whole-structure block:

- Return an eligibility payload with `state=crisis_safety_blocked` or equivalent contract reason.
- Set `can_show_structure=false`.
- Do not include structural summaries, inferred labels, raw excerpts, or generated module payloads.
- Include a neutral backend-authored `display_message` and constrained `required_actions`.

Module-level block:

- Set the affected module status to `hidden_for_safety`.
- Include reason metadata allowed by the contract without exposing lint internals or unsafe text.
- Continue returning unaffected modules only when their summaries and aggregate text do not depend on blocked content.
- Do not replace blocked modules with speculative fallback copy.

Evidence-level block:

- Hide unsafe excerpts and unsafe interpretations.
- Preserve safe citation anchors only when policy allows them.
- Distinguish `hidden_for_safety` from `insufficient_data` so the frontend can render the correct restrained state.

`partially_confirmed` caution:

- Do not smooth, broaden, or paraphrase user-authored partial confirmations into a stronger system claim.
- If the safe display path cannot preserve the user's narrowed wording, block or downgrade the relevant summary/module.

## 10. Caching and Stale-Structure Considerations

The first implementation should prefer on-demand assembly from the active `EvidenceChain` over persisted generated structure artifacts unless performance proves otherwise.

Cache key inputs if caching is introduced:

- `pattern_instance_id`.
- Active `evidence_chain_id`.
- Requested module filter.
- `include_evidence_samples` value.
- Structure generator version.
- Safety/lint policy version.
- Review status and last-reviewed timestamp.
- Pattern instance schema version.

Invalidation triggers:

- Review status changes through confirm, partial, reject, defer, archive, or edit.
- `PatternInstance.evidenceChainId` changes after discovery refresh.
- Evidence chain regeneration.
- Safety/lint policy version changes.
- User edits to summary or note on partially confirmed patterns.
- Pattern hidden/visibility changes.

Stale response policy:

- Never serve cached structure when the cached chain ID differs from the active chain ID.
- Never serve cached structure for rejected, deferred, archived, hidden, or unreviewed pattern states.
- Never serve cached structure generated under an older safety policy as fresh.
- Prefer returning an explicit unavailable/stale eligibility reason over silently using old structure.
- Evidence endpoint should always resolve against the active chain, not a cached module's historical references.

## 11. Minimal First PR Slice

The minimal first PR should establish the V1 read-side skeleton without generating module content.

Recommended contents:

- New V1 controller routes for eligibility and, optionally, aggregate structure returning eligibility plus empty/unavailable module shells.
- `PatternStructureService` and `PatternStructureEligibilityService` with deterministic ownership, status, active-chain, evidence-count, and hidden checks.
- DTOs required only for the eligibility response and minimal aggregate envelope.
- Contract-literal enum mapping for eligibility state, reason code, actions, pattern status, trust tier, and module status.
- Tests for owned vs missing pattern, candidate/unreviewed, confirmed with sufficient evidence, confirmed with too few evidence items, rejected, archived, hidden, missing active chain, and `partially_confirmed` display caution.
- No module generation, no neighbor calculation, no inline evidence samples, no high-trust eligibility, and no Understanding tab work.

Acceptance for the first PR:

- The frontend can call the eligibility endpoint to decide whether to show the Structure entry point.
- Ineligible review states return no user-facing structure modules.
- `high_trust` is absent from MVP eligibility logic.
- Safety-blocked state shape is represented even if deeper module linting lands later.

## 12. Follow-Up PR Slices

Recommended follow-up sequence:

1. Evidence references PR
   - Implement `/structure/evidence`, active-chain filtering, selected evidence IDs, hidden-count behavior, and excerpt safety filtering.
2. Aggregate shell PR
   - Implement `/structure` with module filtering, `not_requested` handling, evidence window metadata, and consistent unavailable states.
3. Scene distribution PR
   - Populate scene distribution from safe, evidence-backed source/domain/context signals with sparse and unknown buckets.
4. Relationship objects PR
   - Populate neutral relationship/object labels with strict anti-blame and anti-motive safety checks.
5. Temporal structure PR
   - Populate timing/order/frequency shape without causal language, clinical framing, or unsupported sequence claims.
6. Neighbor patterns PR
   - Populate flat reviewed neighbor hints only; do not introduce graph, topology, influence, or cause/effect UI semantics.
7. Safety/lint hardening PR
   - Add module-level and combined-response fixtures for forbidden framing, unsupported claims, hidden excerpts, and partial-confirmation narrowing.
8. Cache/staleness PR, only if needed
   - Add cache keys, policy versions, invalidation hooks, and stale-response tests after correctness is established.

Each follow-up PR should keep `high_trust` as a future extension only and keep the Understanding tab in Phase 2 LATER.

## 13. Explicit Non-Goals

This plan does not include:

- Java implementation in this docs PR.
- DTO class creation in this docs PR.
- Database schema changes.
- Pattern Evolution.
- Pattern Understanding tab behavior.
- High-trust MVP eligibility.
- Graph, network, topology, influence, causality, medical, diagnostic, treatment, personality-type, attachment-style, trauma, symptom, severity, or root-cause modeling.
