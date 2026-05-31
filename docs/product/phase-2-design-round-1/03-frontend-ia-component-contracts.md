# Frontend IA and Component Contracts — Pattern Structure MVP

## 0. Purpose

This document defines frontend information architecture and component contract proposals for the Pattern Structure MVP. It is a planning document for later frontend implementation, not a Vue implementation, route patch, visual mockup, or style guide.

The design keeps Pattern Structure inside `/insight` as an inspectable extension of trusted Pattern Cards. It separates frontend IA and component responsibilities from backend domain ownership, API field ownership, and review transition ownership.

## 1. Source Basis

This design leans most on these source sections:

- `docs/product/phase-2-pattern-understanding-plan.md`: Pattern Structure as an independent product layer, trust boundary, shape-not-origin boundary, four MVP dimensions, and guiding principle.
- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md`: eligibility gate, first open experience, source data mapping, module contracts, aggregate response shape, safety boundary, and MVP acceptance criteria.
- `docs/product/pattern-structure-ux-audit.md`: current frontend map, recommended IA, placement rules, reusable frontend pieces, API/store needs, and copy guardrails.

Sibling ownership notes:

- API DTO ownership belongs to `docs/product/phase-2-design-round-1/01-pattern-structure-api-contract.md`. If that file is absent during implementation planning, frontend work should treat it as the future source of truth and avoid freezing backend field names here.
- Review transition ownership belongs to the Review State Machine design doc. This document only defines UI triggers and display states.
- Safety/lint copy ownership belongs to the Safety/Lint design doc and existing UX audit. This document only defines placement and UI behavior.

## 2. IA Recommendation

### 2.1 `/insight` Surface

Pattern Discovery and Pattern Structure should live under a dedicated `/insight` product area.

Recommended route-level IA:

| Surface | MVP role | Notes |
| --- | --- | --- |
| `/insight` | Pattern Discovery list | Primary entry for trusted and reviewable Pattern Cards. |
| `/insight/pattern/:patternId` | Pattern Card detail | Opens a single Pattern Card with evidence and review controls. |
| Pattern Structure panel inside detail | MVP deep inspection | Shows four structure dimensions only when eligible and available. |
| Understanding tab | Phase 2 LATER | Reserved; not shipped in MVP navigation. |

The existing app has `/`, `/tap`, `/wall`, `/pet`, `/profile`, and `/doctor`. Pattern Structure must not become a primary surface in Chat, Profile, Wall, or Pet. Those areas may link to `/insight` later only through low-pressure discovery entry points, not through primary Pattern Structure content.

### 2.2 Pattern Discovery List

The `/insight` landing page should focus on discovery and triage:

- Shows a list of Pattern Cards ordered by backend-provided ranking or recency.
- Distinguishes trusted, unreviewed, rejected, deferred, insufficient-evidence, and safety-blocked states without inventing review transitions.
- Lets the user open Pattern Card detail.
- Keeps Pattern Structure hidden from cards that are not eligible.
- Avoids dense analytic views or connected-node relationship maps.

Pattern Card list rows should show enough information to choose whether to inspect, not enough to imply a final self-summary. Evidence access and structure belong in detail.

### 2.3 Pattern Card Detail

The detail page should be the single MVP home for Pattern Structure.

Recommended detail order:

1. Pattern header and short user-facing summary.
2. Review and state controls.
3. Evidence references for the surfaced card.
4. Pattern Structure extension, if eligible.
5. Neighbor Pattern list as flat cards or rows.
6. Safety notice and control affordances.

Pattern Structure is a detail extension, not a separate top-level product tab in MVP. The page may use anchored sections or internal tabs for readability, but the navigational owner remains Pattern Card detail.

### 2.4 Pattern Structure Placement

Pattern Structure should appear after the core Pattern Card evidence so users can inspect the basis before seeing aggregate structure. It should be visually framed as “what this looks like in your data,” not as a label that defines the user.

MVP structure sections:

- Scene Distribution.
- Relationship Objects.
- Temporal Structure.
- Neighbor Patterns.

Each section must be evidence-backed, inspectable, and eligible only for confirmed or high-trust patterns according to backend and API contract ownership.

### 2.5 Understanding Tab Is Later

The UX audit describes a broader Pattern Understanding layer. For MVP, that layer should remain Phase 2 LATER.

MVP should not ship:

- A top-level Understanding tab.
- Longitudinal Pattern Understanding dashboards.
- Change-over-time product layers.
- Cross-pattern connected-node or map visuals.
- Recommendations that expand beyond evidence inspection and user control.

A disabled or teaser tab is not recommended for MVP because it can create expectation without deliverable value.

## 3. Navigation Constraints

### 3.1 Bottom Navigation

The current app uses a bottom navigation pattern across core mobile-oriented views. The recommended MVP path is to add `/insight` as a first-class bottom-nav destination only if Pattern Discovery is ready as a stable product area.

Recommendation:

- Replace no existing destination unless product scope requires it.
- Use a neutral icon and label such as “Insight” rather than clinical or identity-heavy language.
- Keep `/insight` sibling to Chat, Tap, Wall, Pet, and Profile.
- Do not deep-link Pattern Structure from bottom nav; bottom nav should enter Pattern Discovery only.
- Preserve auth guard behavior used by existing protected routes.

If bottom-nav capacity is constrained, `/insight` may initially be accessible from Profile or Chat as a secondary link, but Pattern Structure itself must still remain under Pattern Card detail.

### 3.2 Back and Deep-Link Behavior

Pattern Card detail should support direct navigation by ID while preserving user control:

- Back returns to `/insight` when opened from list.
- Direct open shows a safe loading state, then the detail or unavailable state.
- If the pattern is no longer eligible, show an unavailable or insufficient-evidence state instead of stale structure.
- If safety-blocked, show only safety-safe context and next controls allowed by Safety/Lint ownership.

## 4. Component Contracts

The following contracts define frontend responsibilities and design-level props/state. They intentionally do not define backend field names as authoritative.

### 4.1 Shared State Model

Frontend containers should normalize API DTOs into UI view models while retaining raw DTO references only inside data adapters.

Common UI state dimensions:

| State | Meaning | UI behavior |
| --- | --- | --- |
| Loading | Request or section data pending | Show skeleton or calm progress copy. |
| Empty | No patterns or no section data for eligible scope | Show neutral empty copy and next action if available. |
| Insufficient evidence | Backend says structure should not be shown yet | Explain that there is not enough supporting material. |
| Rejected | User or review state marks pattern not useful | Keep detail minimal; hide structure unless review rules allow display. |
| Unreviewed | Pattern awaits user review | Show card and evidence; structure only if API marks eligible. |
| Deferred | User postponed review or inspection | Show resume affordance without pressure. |
| Crisis/safety-blocked | Safety gate blocks structure display | Hide structure and show SafetyNotice with allowed resources/actions. |
| Error | Fetch or rendering failure | Show retry and non-blaming copy. |

Design-level container state:

| Name | Owner | Notes |
| --- | --- | --- |
| `selectedPatternId` | Route/detail container | Derived from route or selected card. |
| `patternListState` | `/insight` container | Loading, data, empty, error, pagination if needed. |
| `patternDetailState` | Detail container | Header, evidence, review display, eligibility. |
| `structureState` | Detail container | Section-level availability and safety gating. |
| `reviewActionState` | Detail container | Pending UI trigger state only. |
| `safetyState` | Detail/container | Display flags from API/lint outputs. |

### 4.2 `PatternCard`

Purpose: compact Pattern Discovery item that opens detail.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `pattern` | UI card model adapted from Pattern Discovery DTO. |
| `stateLabel` | Trusted, unreviewed, rejected, deferred, insufficient evidence, or safety-blocked display label. |
| `evidencePreview` | Short excerpt list or count from the DTO, if allowed. |
| `structureAvailability` | Whether detail may offer Pattern Structure after open. |
| `isSelected` | Optional list selection state. |
| `actions` | Open, review trigger, defer trigger where allowed. |

Behavior:

- Opens Pattern Detail rather than expanding into a full structure view in the list.
- Shows no connected-node or map preview.
- Uses calm copy and avoids identity-defining language.
- Does not show Pattern Structure sections inline.

### 4.3 `PatternDetail`

Purpose: detail container for one Pattern Card and its MVP structure extension.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `patternId` | Route-selected identifier. |
| `detail` | UI detail model adapted from Pattern Detail DTO. |
| `structure` | UI structure model adapted from Pattern Structure DTO when available. |
| `reviewState` | Display state from review DTO/state machine. |
| `safetyState` | Safety-block display model. |
| `loadingState` | Page and section loading state. |
| `actions` | Review, retry, back, open evidence, open neighbor. |

Behavior:

- Owns page-level loading, unavailable, and safety-blocked states.
- Renders `ReviewActions`, `EvidenceReferenceList`, `StructurePanel`, and `SafetyNotice` in a stable order.
- Delegates review transition logic to action handlers supplied by the container.
- Does not infer transitions locally.

### 4.4 `StructurePanel`

Purpose: wrapper for the four MVP structure dimensions.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `availability` | Available, partial, insufficient evidence, deferred, or safety-blocked. |
| `sections` | Scene, relationship-object, temporal, and neighbor section models. |
| `evidencePolicy` | Whether inline excerpts, counts, or references can be shown. |
| `lastUpdatedLabel` | Optional display value supplied by DTO/adapter. |
| `actions` | Retry, collapse/expand, open evidence reference. |

Behavior:

- Renders section-level partial states instead of failing the whole panel.
- Clearly labels the panel as evidence-backed structure.
- Shows insufficient-evidence copy when the aggregate structure is not available.
- Never appears for safety-blocked content unless showing only the blocked state shell is explicitly allowed by Safety/Lint ownership.

### 4.5 `SceneDistributionSection`

Purpose: show where the pattern appears across coarse scenes.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `distributionItems` | UI list of scene labels, relative weights/counts, and allowed evidence references. |
| `unknownOrMixedItem` | Optional fallback bucket. |
| `summary` | Short safe summary supplied or adapted from API output. |
| `state` | Loading, empty, partial, insufficient evidence, or available. |
| `actions` | Open evidence reference, filter within section if later allowed. |

Behavior:

- Uses simple list, bar, or grouped-card presentation in later visual design.
- Emphasizes scope and limits to prevent overgeneralizing from narrow evidence.
- Keeps labels coarse and privacy-preserving.

### 4.6 `RelationshipObjectsSection`

Purpose: show recurring roles or objects involved in the pattern.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `objects` | UI list of role/object labels, counts/weights, confidence display if API-owned. |
| `privacyMode` | Whether labels are generalized, hidden, or directly displayable. |
| `evidenceRefs` | Allowed references per object. |
| `state` | Loading, empty, partial, insufficient evidence, or available. |
| `actions` | Open evidence reference, hide object label if user control is supported. |

Behavior:

- Prefer role-level language over unnecessary private names.
- Avoid blame, motive, or fixed identity framing.
- Make hidden/private labels explicit without exposing sensitive details.

### 4.7 `TemporalStructureSection`

Purpose: show when the pattern appears in event sequences.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `temporalItems` | UI list of sequence placements and recurrence signals owned by API DTO. |
| `evidenceChains` | Allowed chain references or excerpt handles. |
| `summary` | Short safe summary if available. |
| `state` | Loading, empty, partial, insufficient evidence, or available. |
| `actions` | Open chain evidence, collapse details. |

Behavior:

- Uses plain sequence language such as before, during, after, or repeated over time.
- Does not claim reasons, predictions, or personal traits.
- Avoids timeline designs that imply unsupported precision.

### 4.8 `NeighborPatternList`

Purpose: show nearby patterns for comparison and inspection.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `neighbors` | Flat list of neighboring Pattern Card models from DTO/adapter. |
| `comparisonNotes` | Evidence-difference notes if supplied by API. |
| `state` | Loading, empty, partial, insufficient evidence, or available. |
| `actions` | Open neighbor detail, open comparison evidence. |

Behavior:

- Must be flat cards/list only.
- Must never use connected-node, cluster, or map visuals.
- Helps distinguish nearby evidence without ranking the user.
- Opens another Pattern Detail page rather than embedding nested structure.

### 4.9 `EvidenceReferenceList`

Purpose: show inspectable evidence references used by card and structure sections.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `references` | UI evidence references adapted from Evidence DTOs. |
| `displayMode` | Preview, compact, or section-linked. |
| `redactionState` | Whether content is excerpted, hidden, or unavailable. |
| `state` | Loading, empty, unavailable, or available. |
| `actions` | Open full reference if allowed, copy reference label, report concern. |

Behavior:

- Keeps evidence close to every structure claim.
- Uses source labels and short excerpts only when allowed by API/safety policy.
- Supports keyboard navigation between section claim and evidence reference.
- Does not expose private details beyond the DTO and safety policy.

### 4.10 `ReviewActions`

Purpose: present user controls for pattern review without owning transitions.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `reviewState` | Current display state from review owner. |
| `allowedActions` | UI triggers supplied by Review State Machine owner. |
| `pendingAction` | Current in-flight action, if any. |
| `disabledReason` | Optional reason actions are disabled. |
| `actions` | Confirm, partially confirm, reject, defer, edit/correct, and restore/reopen where allowed. |

Behavior:

- Emits user intent only; does not compute next state.
- Keeps actions reversible or low-pressure where product rules allow.
- Explains disabled actions without forcing review.
- Hides structure after rejected state unless the owning review/API contracts allow otherwise.

### 4.11 `SafetyNotice`

Purpose: show safety-blocked or safety-limited states with aligned copy.

Inputs:

| Prop | Design meaning |
| --- | --- |
| `safetyState` | Blocked, limited, warning-only, or none as supplied by safety/API owner. |
| `message` | Copy supplied or approved by Safety/Lint owner. |
| `allowedActions` | Safe next actions such as back, retry later, or resource link if allowed. |
| `placement` | Page-level, panel-level, or section-level notice. |

Behavior:

- Takes priority over Pattern Structure rendering when blocked.
- Avoids alarming language beyond approved safety copy.
- Never tries to interpret user condition or intent.
- Keeps user in control with clear exits.

## 5. API DTO Consumption

Frontend should consume API DTOs through a small adapter layer owned by the `/insight` feature area. The adapter maps API-owned field names into UI models for the components above.

Rules:

- Use `01-pattern-structure-api-contract.md` as the authoritative field-name source when available.
- If the API contract is missing, reference it as the owning sibling doc and avoid adding frontend-only field names to backend tickets as if they are final API names.
- Preserve DTO state flags for eligibility, review display, safety gating, evidence availability, and partial section availability.
- Keep raw API responses out of leaf components except during early prototyping.
- Make partial section availability explicit so one missing dimension does not hide all available structure.
- Treat evidence references as handles to inspectable source material, not as free-form text the UI can reshape without policy.

Expected DTO groups, by ownership:

| DTO group | Frontend use |
| --- | --- |
| Pattern Discovery DTO | Drives `/insight` list and `PatternCard`. |
| Pattern Detail DTO | Drives header, evidence preview, detail state, and review display. |
| Pattern Structure DTO | Drives `StructurePanel` and four section models. |
| Evidence DTOs | Drive `EvidenceReferenceList` and section-linked references. |
| Review DTO/state | Drives `ReviewActions` display and allowed triggers. |
| Safety/lint DTO/state | Drives `SafetyNotice` and blocked/limited rendering. |

## 6. State Handling Requirements

### 6.1 Loading

- Page-level loading: show when Pattern Detail is not yet available.
- Section-level loading: show inside `StructurePanel` for delayed structure sections.
- Action loading: disable only the action in flight and keep the rest of the page readable.

### 6.2 Empty

- Empty `/insight`: show neutral copy that no trusted patterns are ready to inspect.
- Empty section: explain that this dimension has no displayable structure yet.
- Empty evidence: show unavailable evidence state and do not render unsupported section claims.

### 6.3 Insufficient Evidence

- Use when the API marks a pattern or section as not ready for structure.
- Do not ask the frontend to synthesize missing structure.
- Offer a calm return path to Pattern Discovery.

### 6.4 Rejected

- Keep the rejected state visible enough for orientation.
- Hide or collapse Pattern Structure unless API/review ownership explicitly permits display.
- Offer only allowed review triggers from the Review State Machine owner.

### 6.5 Unreviewed

- Show evidence first.
- Show Pattern Structure only when eligibility says it is safe and high-trust enough for MVP.
- Avoid nudging the user to confirm.

### 6.6 Deferred

- Keep deferred patterns in the list with a lightweight resume affordance.
- Do not surface Pattern Structure as a pressure mechanism.
- Allow opening detail if the review/API owner allows it.

### 6.7 Crisis/Safety-Blocked

- Safety notice takes precedence over structure, neighbors, and evidence excerpts.
- Render only approved copy and allowed actions.
- Do not show aggregate structure while blocked.

## 7. Existing Frontend Patterns to Reuse

The current frontend is a Vue 3 app with router guards, Pinia auth state, Axios request wrapper, glass-card visual language, mobile-oriented full-page views, and bottom navigation patterns.

Worth reusing in later implementation:

- Router auth guard from `frontend/src/router/index.ts` for `/insight` protection.
- Axios wrapper from `frontend/src/api/request.ts` for authenticated API calls and token handling.
- Pinia composition-store pattern from `frontend/src/stores/auth.ts` for any future insight store.
- Bottom navigation layout pattern from existing core views, while adding only a Pattern Discovery entry.
- Card-based content rhythm from Wall/Tap/Profile views for `PatternCard`, evidence cards, and flat neighbor lists.
- Loading and empty-state conventions already present in Wall and Chat views, rewritten with Pattern Structure-safe copy.

Implementation planning should extract reusable card, empty, loading, and bottom-nav primitives only if that reduces duplication. This design does not require a refactor before MVP.

## 8. Accessibility and User Control Guardrails

Accessibility requirements:

- All Pattern Cards, review triggers, section expanders, and evidence links must be keyboard reachable.
- Focus should move predictably from Pattern Card to detail header on navigation.
- Section headings must be semantic and readable by assistive tech.
- Loading states should expose non-visual status text.
- Color must not be the only indicator for review, safety, or eligibility states.
- Evidence references must have clear labels that identify their section relationship.

User control requirements:

- Users can leave Pattern Detail without completing review.
- Review actions are explicit and never hidden behind passive scrolling.
- Deferred state should feel like postponing, not failing.
- Rejected state should be respected across UI surfaces.
- Safety-blocked state should provide clear exits and avoid showing structure content.
- Copy should use observation-oriented language and avoid identity labels.

## 9. Non-Goals

This document does not define:

- Vue components, TypeScript interfaces, CSS, or route implementation.
- Visual mockups or final layout styling.
- Backend domain objects or authoritative API field names.
- Review state transitions.
- Safety/lint copy rules beyond placement and behavior.
- Change-over-time product layers.
- Connected-node or map visuals.
- Pattern Structure as a primary Chat, Profile, Wall, or Pet surface.
