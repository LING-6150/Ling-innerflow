# Phase 2 Pattern Structure MVP — Backend Current-State Audit

> Status: implementation readiness audit  
> Scope: backend inspection, docs-only  
> Date: 2026-05-31  
> Inputs reviewed:
> - `docs/product/phase-2-pattern-understanding-plan.md`
> - `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md`
> - `docs/product/phase-2-design-round-1/01-pattern-structure-api-contract.md`
> - `docs/product/phase-2-design-round-1/02-pattern-structure-domain-model.md`
> - `docs/product/phase-2-design-round-1/04-pattern-review-state-machine.md`
> - `docs/product/phase-2-design-round-1/05-pattern-structure-safety-lint-eval.md`
> - `src/main/java/com/ling/linginnerflow/`
> - `src/test/java/com/ling/linginnerflow/pattern/`

## 1. Executive Summary

The current backend is a good foundation for Pattern Structure MVP because it already has persisted `PatternInstance`, immutable `EvidenceChain`, persisted `EvidenceItem`, discovery orchestration, user review actions, confidence scoring, evidence verification, source provenance, and a non-clinical `LanguageFirewall`.

It is not implementation-ready as-is for the Phase 2 Pattern Structure contract. The main missing pieces are a dedicated structure read model/API, eligibility service, complete review-state semantics, module-specific structure generators, safety/lint result modeling, source metadata for scene/relationship extraction, neighbor-pattern co-occurrence reads, and explicit abstain/low-trust gating in production domain objects.

Recommended implementation style: add Pattern Structure as a read-only layer over existing persisted pattern/evidence data first, instead of changing the discovery pipeline. Preserve the design boundary that Pattern Structure describes observable shape for confirmed or eligible partially confirmed patterns only.

## 2. Existing Backend Surface

### 2.1 Pattern Packages

Current production packages under `src/main/java/com/ling/linginnerflow/pattern/`:

| Package | Current role | Readiness relevance |
| --- | --- | --- |
| `pattern.entity` | JPA entities for pattern instances and evidence. | Core persisted inputs for Pattern Structure. |
| `pattern.domain` | Enums for domain, source type, and pattern status. | Partially matches review and source vocabulary. |
| `pattern.repo` | Spring Data repositories for instances, chains, items. | Reusable read path, but needs richer queries. |
| `pattern.controller` | Current V1 pattern list, evidence, review, refresh endpoints. | Useful auth/ownership pattern, not the Phase 2 API contract. |
| `pattern.service` | Discovery orchestration and review mutation service. | Reusable state/data ownership; review service needs state-machine alignment. |
| `pattern.verify` | Evidence verifier and chain assembler. | Strong evidence-quality foundation. |
| `pattern.scoring` | Deterministic confidence scoring. | Can support high-trust/eligibility audit, not user-facing score copy. |
| `pattern.safety` | Non-clinical language firewall. | Reusable lint primitive, but too narrow for full Structure safety spec. |
| `pattern.retrieval` | Recall/retrieval services. | Useful for discovery; not directly enough for Structure modules. |
| `pattern.definition` | Static taxonomy loader. | Reusable for names, definitions, and future neighbor metadata. |
| `pattern.dedup` | Semantic duplicate suppression. | Useful precedent for confusable/nearby handling, not a structure neighbor model. |
| `pattern.corpus` | Normalized user-authored corpus docs. | Useful source normalization; not persisted enough for later structure extraction. |
| `pattern.schedule` | Scheduled discovery refresh. | Out of the direct Structure read path. |

### 2.2 Core Entities

#### `PatternInstance`

File: `src/main/java/com/ling/linginnerflow/pattern/entity/PatternInstance.java`

Current fields include:

- `id`, `userId`, `patternKey`, `domain`.
- `status` as `PatternStatus`.
- `confidence`.
- `personalizedSummary` and `userNote`.
- `evidenceChainId` pointing to the latest chain.
- `firstObservedAt`, `lastObservedAt`, `lastReviewedAt`.
- `refreshCount`, `schemaVersion`, `hidden`.

Readiness:

- Reusable as the owning aggregate for `PatternStructure` eligibility and module generation.
- Already stores user review state and accepted/editable summary text.
- Already supports latest-chain reads through `evidenceChainId`.
- Does not currently store enough audit metadata for review trigger source, transition history, partial-confirmation scope, stale evidence state, abstain/low-trust state, or structure cache versioning.

#### `EvidenceChain`

File: `src/main/java/com/ling/linginnerflow/pattern/entity/EvidenceChain.java`

Current fields include:

- `id`, `patternInstanceId`, `generatedAt`, `generatorVersion`.

Readiness:

- Reusable as immutable active evidence chain input.
- `generatorVersion` is useful for audit and stale-structure invalidation.
- Historical chains are retained by design, but repository has no query to list chains by `patternInstanceId` or latest/history order.
- Does not store chain-level accepted/rejected membership, safety summary, source metadata snapshot, or structure generation version.

#### `EvidenceItem`

File: `src/main/java/com/ling/linginnerflow/pattern/entity/EvidenceItem.java`

Current fields include:

- `id`, `evidenceChainId`, `sourceType`, `sourceRef`, `occurredAt`.
- `excerpt`, `isVerbatim`, `interpretation`.

Readiness:

- Reusable for evidence references, temporal sorting, source-ref matching, and representative evidence item IDs.
- Supports current evidence endpoint behavior and core citation requirements.
- Does not currently model excerpt visibility, hidden-for-safety reason, source deletion, thread/conversation ID, participant/contact metadata, user context tags, scene labels, relationship role tags, or sensitivity categories.

### 2.3 Review Status

File: `src/main/java/com/ling/linginnerflow/pattern/domain/PatternStatus.java`

Current statuses:

- `candidate`
- `confirmed`
- `partially_confirmed`
- `rejected`
- `archived`

Readiness:

- Mostly aligned with the Phase 2 review-state vocabulary.
- Missing `deferred` as a persisted status. Current `PatternReviewService.review(..., "defer", ...)` intentionally does nothing to the status while still setting `lastReviewedAt` and `hidden=false`, which does not match the Review State Machine design.
- No explicit production status or field for `abstained`, `low_trust`, `unreviewed`, `rejection_cooldown_active`, or stale evidence. Some are eligibility reasons rather than statuses, but they still need explicit representation in the eligibility result.

### 2.4 Current Controller/API

File: `src/main/java/com/ling/linginnerflow/pattern/controller/PatternController.java`

Current endpoints:

- `GET /api/pattern/instances`
- `GET /api/pattern/instances/{id}/evidence`
- `POST /api/pattern/instances/{id}/review`
- `PATCH /api/pattern/instances/{id}`
- `POST /api/pattern/refresh` behind a dev flag

Readiness:

- Ownership check pattern is reusable.
- Current endpoints expose JPA entities directly and do not match the Phase 2 API contract.
- No `/api/v1/pattern-structure/...` style endpoints exist for structure, eligibility, or structure evidence references.
- Current evidence endpoint returns raw `EvidenceItem` objects and cannot express `excerpt_visibility`, hidden counts, module status, or safety-blocked vs insufficient-data distinctions.

### 2.5 Discovery and Evidence Pipeline

Relevant files:

- `src/main/java/com/ling/linginnerflow/pattern/service/PatternDiscoveryService.java`
- `src/main/java/com/ling/linginnerflow/pattern/verify/EvidenceVerifier.java`
- `src/main/java/com/ling/linginnerflow/pattern/verify/EvidenceChainAssembler.java`
- `src/main/java/com/ling/linginnerflow/pattern/scoring/ConfidenceScorer.java`
- `src/main/java/com/ling/linginnerflow/pattern/corpus/CorpusAssemblyService.java`
- `src/main/java/com/ling/linginnerflow/pattern/corpus/CorpusDoc.java`

Current useful behavior:

- Corpus windowing and source normalization for user-authored chat/check-in content.
- Crisis-flag propagation from source records.
- LLM verification with code-side verbatim-span and language-firewall checks.
- Evidence chain invariants: at least 3 items, at least 1 verbatim item, evidence over at least 2 days, no crisis-flagged source docs.
- Confidence computed deterministically from evidence count, recurrence, and recency.
- Rejected cooldown suppression using source-ref Jaccard comparison.

Readiness:

- Strong base for accepted-evidence aggregation and temporal module MVP.
- Discovery currently creates `candidate` instances and hides low-surface-threshold instances with `hidden=true`; it does not persist an explicit abstain/low-trust decision for later eligibility reasoning.
- `CorpusDoc` has source fields and crisis flag, but those fields are not all persisted to `EvidenceItem` or a structure-specific source metadata model.
- Current source support is effectively `chat_message` and `checkin`; `journal_entry` and `wiki_fact` exist in enum vocabulary but are not assembled here.

### 2.6 Safety/Lint

File: `src/main/java/com/ling/linginnerflow/pattern/safety/LanguageFirewall.java`

Current capabilities:

- Regex/substr checks for diagnostic, clinical, DSM/ICD/MBTI, and direct “you have/you are” style label language.
- Optional LLM judge for secondary language verdict.
- `isClean` and `enforce` APIs used by discovery summaries, evidence interpretations, review partial edits, and general edit path.

Readiness:

- Reusable as one safety check inside a broader Pattern Structure safety/lint service.
- Insufficient alone for the Phase 2 Safety/Lint spec, which also requires blockers/warnings for eligibility, accepted-evidence boundary, evidence reference coverage, sensitive excerpt display, overbreadth, certainty inflation, partial confirmation summary handling, relationship blame, temporal causality, neighbor explanation, LLM auditability, deleted source, cross-user source privacy, and sparse evidence state.
- Current safety model produces a boolean/exception, not structured lint results with severity, blocker/warning semantics, affected field/module, and remediation code.

### 2.7 Tests and Eval Harness

Relevant test packages under `src/test/java/com/ling/linginnerflow/pattern/`:

| Area | Current coverage |
| --- | --- |
| `verify` | `EvidenceChainAssemblerTest` covers core evidence-chain invariants. |
| `scoring` | `ConfidenceScorerTest` covers deterministic confidence behavior. |
| `definition` | `PatternDefinitionLoaderTest` covers taxonomy loading. |
| `dedup` | `PatternDeduplicatorTest` covers semantic duplicate behavior. |
| `eval` and `eval/baseline` | Offline prediction metrics and baselines. |
| `validation` | Abstain gate, offline rule simulation, standalone runners, V1/V2 validation runners. |

Readiness:

- Good offline/eval scaffolding exists for discovery quality and abstain experiments.
- Abstain concepts currently appear in test/validation support, not production domain or services.
- No tests currently exist for Pattern Structure eligibility, module generation, safety/lint results, structure DTO mapping, or evidence-reference visibility. Per this audit's constraints, no tests were added.

## 3. Existing Code That Can Be Reused

### 3.1 Directly Reusable

- `PatternInstanceRepository.findById(...)` and ownership checks for loading target instances.
- `PatternInstance.evidenceChainId` for active-chain lookup.
- `EvidenceItemRepository.findByEvidenceChainId(...)` for module input and evidence reference lists.
- `EvidenceItem.sourceRef` for same-source neighbor matching.
- `EvidenceItem.occurredAt`, `PatternInstance.firstObservedAt`, and `PatternInstance.lastObservedAt` for temporal structure.
- `PatternInstance.status`, `lastReviewedAt`, `personalizedSummary`, and `userNote` for review-aware eligibility and partial-confirmation display.
- `PatternDefinitionLoader` for pattern display metadata and closed taxonomy validation.
- `LanguageFirewall` as the first pass for forbidden language checks.
- `ConfidenceScorer` and existing `confidence` value for internal eligibility/audit decisions, with the caveat that the API contract should not expose persuasive confidence copy.
- `EvidenceChainAssembler` invariants as precedent for minimum evidence coverage.

### 3.2 Reusable With Extension

- `PatternController` ownership/auth approach, but not direct entity-returning payload style.
- `PatternReviewService`, after aligning `defer` and transition validation with the Review State Machine.
- `EvidenceChainRepository`, after adding queries by `patternInstanceId` for historical chains and stale/temporal calculations.
- `PatternInstanceRepository`, after adding status/domain/user queries needed for neighbor discovery and eligibility lists.
- `EvidenceItem`, after adding or associating safety/source metadata needed by scene, relationship, and evidence visibility modules.
- `CorpusAssemblyService` source normalization, if future implementation preserves or backfills source metadata into evidence references.
- Test validation classes for abstain ideas, if promoted carefully into production eligibility without exposing low-trust MVP behavior.

### 3.3 Useful Architectural Precedents

- Existing discovery pipeline keeps generation and persistence in services rather than controllers.
- Evidence chain assembly is deterministic after verification and returns structured drop reasons internally.
- Language safety is centralized rather than scattered across prompts.
- Rejected cooldown logic already treats user rejection as product-significant, not just a UI flag.

## 4. Missing Concepts for Pattern Structure MVP

### 4.1 API and DTO Layer

Missing:

- Pattern Structure controller.
- Versioned structure endpoint.
- Eligibility endpoint.
- Structure evidence endpoint matching the Phase 2 contract.
- DTOs for aggregate response, module statuses, empty states, evidence references, and errors.
- Mapper layer from entities/internal domain to API literals.
- Request parameter parsing for `module=scene_distribution|relationship_objects|temporal_structure|neighbor_patterns|all`.

### 4.2 Eligibility Domain

Missing:

- Dedicated `PatternStructureEligibilityService`.
- Machine-readable eligibility reasons such as `unreviewed_candidate`, `rejected`, `deferred`, `archived`, `insufficient_evidence`, `safety_blocked`, `stale_evidence`, `abstain_boundary`, and `low_trust` where applicable.
- Explicit distinction between whole-structure unavailable and per-module unavailable.
- Handling for `partially_confirmed` that prefers user-edited scope and prevents broadening beyond accepted/corrected content.
- Production linkage to abstain/low-trust decisions. Current abstain logic lives in test/validation code.

### 4.3 Review State Machine Alignment

Missing or incomplete:

- Persisted `deferred` status.
- Transition validation table.
- Trigger source vocabulary and audit trail.
- Prohibited transition checks.
- Reopen behavior for rejected/deferred states.
- Structured review event history.
- Clear handling of `candidate -> deferred`; current `defer` action leaves status unchanged.

### 4.4 Pattern Structure Read Model

Missing:

- Internal `PatternStructure` aggregate/read model.
- `StructureSection`/module result objects.
- Module status objects: `available`, `insufficient_data`, `hidden_for_safety`, and not-requested state.
- Structure generation metadata, such as generated timestamp, source chain version, safety result version, and hidden evidence counts.
- Optional caching strategy, if future implementation chooses cached generated outputs instead of fully on-demand computation.

### 4.5 Scene Distribution

Currently available inputs:

- `PatternInstance.domain`.
- `EvidenceItem.sourceType`.
- `EvidenceItem.occurredAt`.

Missing:

- Context tags beyond the single instance domain.
- Conversation/channel type.
- Journal/check-in tags.
- User-provided context labels.
- Per-evidence domain/context labels.
- Representative selection policy with recency/diversity.
- Neutral scene label generation and auditability metadata.

Minimum viable implementation can start with deterministic counts by `sourceType` and `PatternInstance.domain`, but that will be thin and must be labeled conservatively.

### 4.6 Relationship Objects

Currently available inputs:

- Evidence text/excerpts.
- Source references.
- Source type.

Missing:

- Chat participant IDs on evidence items.
- Contact display names or aliases.
- Relationship role tags.
- User-created relationship labels.
- Privacy levels.
- Relationship extraction model and safety checks for blame/motive/private facts.

This is the least ready module. An MVP implementation should prefer `insufficient_data` or a tightly constrained LLM-assisted extractor over pretending the current schema has reliable relationship objects.

### 4.7 Temporal Structure

Currently available inputs:

- `EvidenceItem.occurredAt`.
- `PatternInstance.firstObservedAt` and `lastObservedAt`.
- Historical chains likely exist in DB by immutable design.

Missing:

- Repository query for historical chains by pattern instance.
- Bucket/concentration DTOs.
- Timeline item list.
- Stale evidence policy.
- Evidence-deletion handling.

This is the most implementation-ready module because it can be mostly deterministic from existing data.

### 4.8 Neighbor Patterns

Currently available inputs:

- Same-user pattern instances.
- Status.
- Active evidence chain IDs.
- Evidence source refs and timestamps.

Missing:

- Efficient repository queries for other eligible instances and their evidence chains.
- Same-thread/conversation IDs.
- Context tags.
- Neighbor result model with representative pairs and relation basis.
- Flat-list-only connector label policy.
- Guardrail to exclude rejected, archived, deferred, candidate, hidden, abstained, or low-trust patterns.

A first implementation can support same `sourceRef` and fixed timestamp-window matching only. Same-thread and context-tag matching require schema/source metadata work.

### 4.9 Safety/Lint Results

Missing:

- Structured lint result model with check name, severity, blocker/warning, target module/field, reason code, and remediation.
- Evidence visibility policy for excerpts.
- Sensitive excerpt classifier beyond crisis filtering at discovery time.
- Coverage validator requiring every module claim to cite evidence IDs.
- Overbreadth and certainty-inflation checks.
- Deleted-source and cross-user-source checks.
- Auditability check for any LLM-assisted labels.

### 4.10 Source Metadata and Evidence Privacy

Missing:

- `EvidenceItem` visibility state.
- Hidden evidence counts and reasons.
- Deep-link construction policy.
- Source deletion detection.
- Thread/conversation IDs.
- Participant/contact metadata.
- Event/context tags.
- Mapping from internal `SourceType.chat_message` to API `chat` and similar wire values.

## 5. Proposed Implementation Insertion Points

### 5.1 New Package Area

Add a dedicated Pattern Structure slice rather than overloading existing discovery classes:

```text
src/main/java/com/ling/linginnerflow/pattern/structure/
```

Suggested subpackages for future implementation:

```text
pattern/structure/controller
pattern/structure/service
pattern/structure/domain
pattern/structure/dto
pattern/structure/mapper
pattern/structure/safety
pattern/structure/module
```

Rationale:

- Keeps Phase 2 read-model concerns separate from V1 discovery mutation flow.
- Avoids returning JPA entities directly from new API endpoints.
- Makes it easier to enforce the Pattern Structure non-goals and review eligibility boundary.

### 5.2 Controller Insertion Point

Create a new controller rather than extending `PatternController` heavily.

Future likely file:

- `src/main/java/com/ling/linginnerflow/pattern/structure/controller/PatternStructureController.java`

Responsibilities:

- Authenticate and resolve current `userId` using the existing Spring Security pattern.
- Expose the versioned structure, eligibility, and evidence-reference endpoints.
- Delegate all eligibility, module computation, and safety decisions to services.
- Return DTOs, never entities.

### 5.3 Eligibility Service

Future likely file:

- `src/main/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureEligibilityService.java`

Responsibilities:

- Load owned `PatternInstance`.
- Enforce status mapping from the Review State Machine.
- Require active evidence chain and enough accepted evidence.
- Exclude hidden, low-trust, rejected, archived, deferred, candidate, stale, or safety-blocked inputs.
- Produce a structured eligibility DTO/reason, not an exception, for expected unavailable states.

### 5.4 Evidence Read Service

Future likely file:

- `src/main/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureEvidenceService.java`

Responsibilities:

- Load active chain and evidence items.
- Map evidence items to safe evidence references.
- Apply excerpt visibility policy.
- Compute hidden evidence counts.
- Preserve stable evidence item IDs for module citations.

### 5.5 Module Services

Future likely files:

- `src/main/java/com/ling/linginnerflow/pattern/structure/module/SceneDistributionService.java`
- `src/main/java/com/ling/linginnerflow/pattern/structure/module/RelationshipObjectsService.java`
- `src/main/java/com/ling/linginnerflow/pattern/structure/module/TemporalStructureService.java`
- `src/main/java/com/ling/linginnerflow/pattern/structure/module/NeighborPatternsService.java`

Suggested sequencing:

1. Temporal module first because current fields support it directly.
2. Neighbor module second with same-source and time-window matching only.
3. Scene module third with deterministic `sourceType`/domain grouping and conservative labels.
4. Relationship module last because it needs metadata and safety support.

### 5.6 Safety/Lint Service

Future likely file:

- `src/main/java/com/ling/linginnerflow/pattern/structure/safety/PatternStructureSafetyLintService.java`

Responsibilities:

- Wrap `LanguageFirewall` for generated text checks.
- Enforce eligibility and accepted-evidence boundaries.
- Validate evidence coverage for each module claim.
- Classify blockers vs warnings.
- Hide unsafe module fields or whole modules.
- Ensure `partially_confirmed` summaries do not broaden user-edited scope.

### 5.7 Repository Extensions

Likely future changes:

- Add `EvidenceChainRepository.findByPatternInstanceIdOrderByGeneratedAtDesc(...)`.
- Add `PatternInstanceRepository.findByUserIdAndStatusIn(...)` or equivalent for neighbor candidates.
- Add batch evidence item lookup by chain IDs for neighbor calculation.
- Consider explicit queries excluding hidden instances.

### 5.8 Review State Machine Service

Likely future changes:

- Extend `PatternStatus` with `deferred`.
- Add transition validation inside `PatternReviewService` or a new `PatternReviewStateMachine` helper.
- Persist review events if auditability is required for implementation readiness beyond MVP.
- Update `defer` behavior to persist `deferred` instead of leaving `candidate` unchanged.

## 6. Risks From Current Architecture

### 6.1 Entity Exposure Risk

Current pattern endpoints return JPA entities directly. Reusing that style for Pattern Structure would make it difficult to enforce API literals, hidden evidence policy, safety-blocked modules, and stable compatibility.

Mitigation: new DTO/mapper layer for all Phase 2 endpoints.

### 6.2 Review-State Drift

The code's `defer` behavior does not match the Review State Machine. If Pattern Structure eligibility relies only on current persisted status, deferred candidates may still look like candidates, with ambiguous UI behavior.

Mitigation: align review states before enabling structure entry points.

### 6.3 Hidden vs Ineligible Ambiguity

`hidden=true` currently means sub-threshold internal tracking, while the Phase 2 contract needs multiple unavailable states. Treating `hidden` as a general safety or eligibility flag would collapse important reasons.

Mitigation: model Pattern Structure eligibility reasons explicitly.

### 6.4 Safety Is Too Boolean

`LanguageFirewall` returns clean/dirty, but Phase 2 requires module/field-level blockers and warnings. A boolean check cannot represent aggregate-only display, hidden excerpts, sparse evidence, or partially confirmed caution.

Mitigation: build structured lint results and use `LanguageFirewall` only as one check.

### 6.5 Source Metadata Is Thin

Existing evidence records have `sourceType` and `sourceRef`, but scene and relationship modules need richer metadata. LLM extraction from excerpts alone risks unsupported or privacy-sensitive claims.

Mitigation: start with conservative deterministic modules and add metadata-backed extraction before richer relationship claims.

### 6.6 Abstain Boundary Is Not Production-Modeled

V2 abstain work exists in tests/validation, but production `PatternInstance` has no explicit abstain/low-trust fields. The Phase 2 specs require abstained and low-trust candidates to be ineligible.

Mitigation: either promote abstain result metadata into production discovery outputs or define eligibility using current `hidden`, confidence, and review status until production abstain state exists. Do not expose low-trust MVP behavior.

### 6.7 Neighbor Queries May Become N+1

Neighbor Pattern generation needs other eligible instances and their evidence items. Current repositories support only simple reads and active chain lookup through each instance.

Mitigation: add batch repository queries before implementing neighbor modules.

### 6.8 Historical Chains Are Under-Queryable

The entity design says chains are immutable and retained, but there is no repository query for chain history. Temporal structure can use active chain immediately, but historical recurrence over refreshes will be limited.

Mitigation: add chain-history queries only if MVP needs historical chains; otherwise explicitly scope temporal MVP to active accepted evidence.

### 6.9 Partial Confirmation Scope Is Underspecified in Data

`partially_confirmed` stores edited summary and user note, but not structured accepted/rejected subclaims. Structure generation could accidentally broaden the accepted scope.

Mitigation: for MVP, use the edited summary/user note as the display boundary and keep generated structure conservative. Longer term, store partial confirmation scope explicitly.

## 7. Suggested Implementation Sequence

1. **Add DTO/read-model layer**: define Pattern Structure response, eligibility, module, empty-state, evidence-reference, and error DTOs without changing discovery behavior.
2. **Implement eligibility service**: enforce confirmed/eligible partial-only access, active evidence chain presence, minimum accepted evidence, hidden handling, and structured unavailable reasons.
3. **Align review states**: add/persist `deferred`, validate transitions, and ensure `candidate`, `rejected`, `deferred`, and `archived` are ineligible.
4. **Implement safe evidence mapping**: convert active `EvidenceItem`s into evidence reference DTOs with visibility and hidden counts.
5. **Implement temporal module**: deterministic first/last observed, recent density, concentration buckets, and timeline IDs from active evidence.
6. **Implement neighbor module MVP**: same-user confirmed/partially confirmed neighbors by same `sourceRef` and timestamp window; return flat list only.
7. **Implement scene module MVP**: deterministic grouping by source type and current domain, with conservative insufficient-data thresholds.
8. **Defer or constrain relationship module**: return `insufficient_data` until participant/role metadata or a tightly audited extractor exists.
9. **Add structure safety/lint service**: layer structured blocker/warning checks around all generated labels and module outputs.
10. **Add repository batch queries and tests**: after DTO/services exist, add focused unit tests around eligibility, temporal, neighbor, evidence visibility, and safety/lint behavior.

## 8. Files Likely To Be Touched In Future Implementation

### 8.1 Likely New Files

```text
src/main/java/com/ling/linginnerflow/pattern/structure/controller/PatternStructureController.java
src/main/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureService.java
src/main/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureEligibilityService.java
src/main/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureEvidenceService.java
src/main/java/com/ling/linginnerflow/pattern/structure/module/SceneDistributionService.java
src/main/java/com/ling/linginnerflow/pattern/structure/module/RelationshipObjectsService.java
src/main/java/com/ling/linginnerflow/pattern/structure/module/TemporalStructureService.java
src/main/java/com/ling/linginnerflow/pattern/structure/module/NeighborPatternsService.java
src/main/java/com/ling/linginnerflow/pattern/structure/safety/PatternStructureSafetyLintService.java
src/main/java/com/ling/linginnerflow/pattern/structure/domain/PatternStructure.java
src/main/java/com/ling/linginnerflow/pattern/structure/domain/StructureSection.java
src/main/java/com/ling/linginnerflow/pattern/structure/domain/StructureModuleStatus.java
src/main/java/com/ling/linginnerflow/pattern/structure/domain/StructureEligibility.java
src/main/java/com/ling/linginnerflow/pattern/structure/domain/StructureEligibilityReason.java
src/main/java/com/ling/linginnerflow/pattern/structure/domain/StructureSafetyFinding.java
src/main/java/com/ling/linginnerflow/pattern/structure/dto/PatternStructureResponse.java
src/main/java/com/ling/linginnerflow/pattern/structure/dto/PatternStructureEligibilityResponse.java
src/main/java/com/ling/linginnerflow/pattern/structure/dto/PatternStructureEvidenceResponse.java
src/main/java/com/ling/linginnerflow/pattern/structure/dto/PatternStructureErrorResponse.java
src/main/java/com/ling/linginnerflow/pattern/structure/mapper/PatternStructureMapper.java
```

### 8.2 Likely Existing Production Files To Modify

```text
src/main/java/com/ling/linginnerflow/pattern/domain/PatternStatus.java
src/main/java/com/ling/linginnerflow/pattern/service/PatternReviewService.java
src/main/java/com/ling/linginnerflow/pattern/repo/PatternInstanceRepository.java
src/main/java/com/ling/linginnerflow/pattern/repo/EvidenceChainRepository.java
src/main/java/com/ling/linginnerflow/pattern/repo/EvidenceItemRepository.java
src/main/java/com/ling/linginnerflow/pattern/entity/EvidenceItem.java
src/main/java/com/ling/linginnerflow/pattern/entity/EvidenceChain.java
src/main/java/com/ling/linginnerflow/pattern/entity/PatternInstance.java
src/main/java/com/ling/linginnerflow/pattern/safety/LanguageFirewall.java
```

Notes:

- `EvidenceItem`, `EvidenceChain`, and `PatternInstance` should be modified only if implementation chooses persisted metadata/cache fields. A read-only MVP can start without entity changes except review-state alignment.
- `PatternController` does not need to be modified for MVP if a separate structure controller is introduced.
- `PatternDiscoveryService` should not be modified for initial Pattern Structure reads unless production abstain/low-trust metadata must be added.

### 8.3 Likely Future Test Files

```text
src/test/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureEligibilityServiceTest.java
src/test/java/com/ling/linginnerflow/pattern/structure/service/PatternStructureEvidenceServiceTest.java
src/test/java/com/ling/linginnerflow/pattern/structure/module/TemporalStructureServiceTest.java
src/test/java/com/ling/linginnerflow/pattern/structure/module/NeighborPatternsServiceTest.java
src/test/java/com/ling/linginnerflow/pattern/structure/module/SceneDistributionServiceTest.java
src/test/java/com/ling/linginnerflow/pattern/structure/safety/PatternStructureSafetyLintServiceTest.java
src/test/java/com/ling/linginnerflow/pattern/service/PatternReviewServiceStateMachineTest.java
```

No tests were created as part of this audit.

## 9. Readiness Verdict By Area

| Area | Readiness | Rationale |
| --- | --- | --- |
| PatternInstance base model | Medium | Core fields exist; partial/deferred/audit semantics need alignment. |
| Evidence chain/items | Medium-high | Strong active-chain and item data; source metadata and visibility missing. |
| Temporal module | High | Can be deterministic from existing timestamps with limited scope. |
| Scene module | Medium-low | Can start with source/domain counts; richer context labels missing. |
| Relationship module | Low | Required metadata and privacy model are missing. |
| Neighbor module | Medium | Same source/time-window possible; thread/context metadata and batch queries missing. |
| Eligibility | Medium-low | Status exists, but no dedicated reason model or production abstain linkage. |
| Review state machine | Medium-low | Most statuses exist, but `deferred` and transition audit are missing. |
| Safety/lint | Medium-low | Language firewall exists, but structured module-level safety is missing. |
| API contract | Low | Current endpoints do not match Phase 2 DTO/versioning/error contracts. |
| Tests/eval | Medium | Good V1 coverage and eval scaffolding; no Pattern Structure tests yet. |

## 10. Final Recommendation

The backend is ready for a Phase 2 implementation spike, not ready for direct feature wiring. Start by adding a separate read-only Pattern Structure API layer over existing `PatternInstance` and `EvidenceItem` data, with strict eligibility and safety/lint gates. Implement deterministic temporal output first, then neighbor matching by source/time, then conservative scene distribution. Keep relationship objects unavailable or highly constrained until source metadata and privacy checks are available.

Do not build Pattern Structure inside the discovery pipeline, do not expose it for candidates or low-trust/abstain-boundary instances, and do not rely on generated text unless every displayed claim has evidence references and passes structured safety checks.
