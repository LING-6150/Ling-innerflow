# Pattern Structure UX Audit

> Date: 2026-05-31
> Scope: read-only frontend / UX audit. This document gives design recommendations only and does not implement frontend code.

## 1. Executive Summary

Pattern Structure should land as a **two-layer experience**:

1. **Pattern Card detail extension** for a single accepted/candidate pattern: "what parts make up this pattern?" grounded in the same evidence chain.
2. **Independent Pattern Understanding layer** for cross-pattern relationships: "how do my confirmed patterns relate across domains?" only after enough reviewed evidence exists.

Do **not** ship Pattern Structure as a standalone top-level feature before Pattern Discovery / abstain quality is reliable. `docs/STATE.md` explicitly says the current V1 validation failed on human prose and that Pattern Structure can become a noise amplifier if built on unreliable pattern instances. Therefore the UX should be designed now, but product activation should be gated behind stricter evidence and abstain rules.

Recommended placement:

- Short term: add Structure inside the future `/insight` Pattern detail / evidence drawer.
- Medium term: add a secondary "Understanding" tab inside `/insight`, not a global nav item.
- Avoid placing Structure in `/profile` as "what I know about you" because that framing can feel too authoritative and diagnostic.

## 2. Inputs Reviewed

- `docs/STATE.md`
- `docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md`
- `frontend/src/router/index.ts`
- `frontend/src/api/request.ts`
- `frontend/src/api/auth.ts`
- `frontend/src/stores/auth.ts`
- `frontend/src/views/ChatView.vue`
- `frontend/src/views/ProfileView.vue`
- `frontend/src/views/WallView.vue`
- `frontend/src/views/TapView.vue`
- `frontend/src/views/PetView.vue`
- `frontend/src/views/DoctorDashboard.vue`

No application code was changed.

## 3. Current Frontend Map

### 3.1 Routes

Current router entries:

| Route | View | Auth | Current role |
|---|---|---:|---|
| `/login` | `LoginView.vue` | no | Login / register |
| `/` | `ChatView.vue` | yes | Main conversation surface |
| `/tap` | `TapView.vue` | yes | Tapping interaction / pet vitality input |
| `/wall` | `WallView.vue` | yes | Public/private check-in wall |
| `/profile` | `ProfileView.vue` | yes | Mood analytics, User Wiki, memory corrections |
| `/pet` | `PetView.vue` | yes | Growth / companion visualization |
| `/doctor` | `DoctorDashboard.vue` | no | Doctor dashboard / clinical-adjacent surface |

There is currently **no frontend route for Pattern Discovery** and no `/insight` route in code, even though the product spec calls for `InsightView.vue` at `/insight`.

### 3.2 API Client Shape

Current API setup is minimal:

- `frontend/src/api/request.ts` creates a shared Axios instance.
- `frontend/src/api/auth.ts` is the only typed feature API client.
- Most feature endpoints are called directly inside Vue views.

Current pattern/insight-adjacent endpoints found in views:

| Area | Endpoint(s) | View |
|---|---|---|
| Chat history | `GET /api/chat/history` | `ChatView.vue` |
| Persona | `GET /api/emotion/persona`, `POST /api/emotion/persona` | `ChatView.vue` |
| Emotion image | `GET /api/emotion-image/latest`, `GET /api/emotion-image/recent` | `ChatView.vue`, `ProfileView.vue` |
| Check-ins | `GET /api/checkin/wall`, `GET /api/checkin/history`, `POST /api/checkin` | `WallView.vue` |
| Reactions | `GET /api/checkin/{id}/react`, `POST /api/checkin/{id}/react` | `WallView.vue` |
| User Wiki | `GET /api/memory/wiki`, `POST /api/memory/wiki/correct`, `DELETE /api/memory/wiki` | `ProfileView.vue` |
| Emotion analytics | `GET /api/emotion-log/overview`, `GET /api/emotion-log/trend?days=7`, `GET /api/emotion-log/distribution?days=30`, `GET /api/emotion-log/insight` | `ProfileView.vue` |
| Pet | `GET /api/pet`, `POST /api/pet/tap` | `PetView.vue`, `TapView.vue` |
| Doctor | `/api/doctor/...`, `/mcp/tools/call` | `DoctorDashboard.vue` |

There is currently **no** `frontend/src/api/pattern.ts` and no pattern-specific store.

### 3.3 Stores

Current Pinia usage:

- `frontend/src/stores/auth.ts` only stores `token`, `userId`, `username`, and `isLoggedIn`.
- There is no `pattern.ts`, `insight.ts`, or `memory.ts` store.
- Pattern state would currently have to live in a view unless a new store is added.

### 3.4 Pattern / Insight Related Pages

The closest current surfaces are:

1. **`ProfileView.vue`**
   - Shows an `Emotional Insight` card from `GET /api/emotion-log/insight`.
   - Shows mood trend and distribution charts.
   - Shows `User Wiki — What I Know About You` from `GET /api/memory/wiki`.
   - Allows direct correction/deletion through `POST /api/memory/wiki/correct` and `DELETE /api/memory/wiki`.
   - This is the strongest existing UI precedent for reflection, memory, and correction.

2. **`ChatView.vue`**
   - Main text source and conversational surface.
   - Could deep-link from evidence items back to source messages in the future.
   - Product spec explicitly says V1 should not do real-time pattern surfacing during chat.

3. **`WallView.vue`**
   - Check-in source surface.
   - Useful as evidence source, but not a good place to explain patterns because it mixes social/private posting and reactions.

4. **`DoctorDashboard.vue`**
   - Clinical-adjacent surface.
   - Should not host Pattern Structure. The Pattern Discovery spec says doctor-facing pattern view and clinical export are out of scope.

## 4. What Pattern Structure Should Be

### 4.1 Option A — Pattern Card Detail Extension

This means Structure appears inside a single PatternInstance detail view, close to the evidence chain.

Recommended for first implementation because it is:

- Evidence-local: every structural claim can point to the same quotes / check-ins.
- Less grandiose: it explains "this candidate pattern" rather than "your psychology".
- Easier to review: users can confirm, partially accept, reject, or edit in context.
- Safer after V1 failures: no cross-pattern graph is shown until the base pattern is trusted.

Suggested card sections:

- `What the system noticed` — existing personalized summary.
- `Evidence` — 3–7 source items, at least one verbatim quote.
- `Structure` — neutral decomposition, e.g. "situations", "repeated response", "what tends to happen next".
- `Your read` — confirm / partially true / not me / defer.
- `Language guard` — visible copy: "This is a hypothesis from your own notes, not a diagnosis."

### 4.2 Option B — Independent Pattern Understanding Layer

This means Structure becomes a separate understanding page that maps relationships across several reviewed patterns.

Understanding tab is Phase 2 LATER, not part of MVP. The MVP ships only Pattern Card detail extension.

Useful, but risky if launched too early:

- It can imply a hidden causal model of the user.
- It can make false positives feel more authoritative by connecting them.
- It may look like therapy, diagnosis, personality typing, or attachment analysis.
- It depends on calibrated abstain behavior and user-reviewed PatternInstances.

If used, this layer should only aggregate:

- Confirmed or partially confirmed patterns.
- Patterns with current evidence chains meeting the evidence invariant.
- Patterns outside rejection cooldown.
- Patterns that pass diagnostic-language and causal-language linting.

### 4.3 Recommended — Two Layers Combined

Use a two-layer IA:

1. **Detail layer:** Structure inside every eligible Pattern Card detail.
2. **Understanding layer:** A non-default tab under `/insight`, showing only reviewed / high-trust relationships.

This gives users local explanation first and broader synthesis later. It also protects the product from turning uncertain candidate labels into an authoritative map of the person.

## 5. Recommended IA / Navigation

### 5.1 Core IA

Add a dedicated Pattern surface rather than hiding it in Profile:

```text
/insight
  ├─ Patterns          candidate / confirmed / rejected filters
  ├─ Domains           family / intimate / work / social / self / body
  ├─ Pattern Detail    summary + evidence + structure + review actions
  └─ Understanding     optional tab for cross-pattern structure
```

Recommended route names:

- `/insight` — primary Pattern Discovery surface.
- `/insight/pattern/:id` — optional detail route if drawer state is not enough.
- `/insight/understanding` — optional subroute or tab, not a bottom-nav item.

### 5.2 Default Navigation

Current bottom nav is icon-only and has five items: chat, tap, wall, pet, profile. The Pattern Discovery spec says the product should pivot to Pattern Discovery as the core surface and remove Pet / Tap / Wall from default V1 critical path.

Recommended default nav for the Pattern product:

| Position | Destination | Label | Rationale |
|---:|---|---|---|
| 1 | `/` | Chat | Input/source surface |
| 2 | `/insight` | Insights or Patterns | Core read-back mirror |
| 3 | `/profile` | Me or Memory | User Wiki, corrections, settings |

If keeping five icons for continuity, use:

```text
Chat · Insights · Wall · Pet · Profile
```

But the recommended Pattern-first IA is:

```text
Chat · Insights · Profile
```

### 5.3 Placement Rules

- Do place Pattern Structure in `/insight`, not `/doctor`.
- Do allow Profile to show a small summary count such as "3 reviewed patterns", linking to `/insight`.
- Do not put Structure inside the current `Emotional Insight` card; that card is mood-log oriented and lacks evidence/review affordances.
- Do not place cross-pattern maps in the default landing state.
- Do not surface Structure in real time during chat.

## 6. Reusable Frontend Pieces

The frontend is mostly view-local rather than componentized, but these patterns can be reused or extracted.

### 6.1 Visual / Layout Patterns

- `glass-card` / `glass-card-strong` styles across views.
- Background orb treatment from `ChatView.vue`, `ProfileView.vue`, and `PetView.vue`.
- Bottom navigation structure from `WallView.vue`, `ProfileView.vue`, `PetView.vue`.
- Top bar pattern from `ChatView.vue`, `WallView.vue`, `PetView.vue`.
- Empty-state copy style from `ProfileView.vue` and `WallView.vue`.

### 6.2 Interaction Patterns

- Profile inline editing pattern for Wiki fields.
- Trigger chip add/delete UI for lightweight user correction.
- Confirmation dialogs from memory clearing.
- Loading and empty states from chart/wiki sections.
- Deep-link navigation through `router.push(path)` helpers.

### 6.3 Data Patterns

- Existing shared Axios client in `frontend/src/api/request.ts`.
- Existing auth token interceptor behavior.
- Existing direct view endpoint calls as a short-term pattern, though a typed `pattern.ts` API client is preferred.
- Existing User Wiki correction endpoint shape can inspire pattern review/edit interactions, but should not be reused blindly because PatternInstance review has a richer state machine.

### 6.4 Components Worth Extracting

If implementing later, extract these instead of duplicating inside a large view:

- `BottomNav.vue`
- `TopBar.vue`
- `PatternCard.vue`
- `EvidenceDrawer.vue`
- `DomainTabs.vue`
- `StatusFilter.vue`
- `ReviewActions.vue`
- `StructurePanel.vue`
- `SafetyNotice.vue`

## 7. New Frontend API Contract Needed

The Pattern Discovery spec already proposes core endpoints. Pattern Structure needs a small extension around those endpoints rather than an unrelated API family.

### 7.1 Pattern Discovery Baseline

Create `frontend/src/api/pattern.ts` with typed wrappers for:

```ts
GET /api/pattern/instances?domain=&status=
GET /api/pattern/instances/{id}/evidence
POST /api/pattern/instances/{id}/review
PATCH /api/pattern/instances/{id}
POST /api/pattern/refresh
```

Minimum frontend DTOs:

```ts
type PatternStatus = 'candidate' | 'confirmed' | 'partially_confirmed' | 'rejected' | 'archived'
type Domain = 'family' | 'intimate' | 'work' | 'social' | 'self' | 'body'

type PatternInstanceDto = {
  id: string
  patternKey: string
  shortName: string
  domain: Domain
  status: PatternStatus
  personalizedSummary: string
  confidence: number
  evidenceCount: number
  lastUpdatedAt: string
  userNote?: string
  reviewCooldownUntil?: string
  safetyFlags?: string[]
}

type EvidenceItemDto = {
  id: string
  sourceType: 'chat' | 'journal' | 'checkin'
  sourceRef: string
  occurredAt: string
  excerpt: string
  isVerbatim: boolean
  interpretation: string
  deepLink?: string
}
```

### 7.2 Pattern Structure Extension

Add one of these API shapes:

Option 1, embedded in detail response:

```ts
GET /api/pattern/instances/{id}/structure
```

Option 2, returned with evidence:

```ts
GET /api/pattern/instances/{id}/evidence?includeStructure=true
```

Recommended DTO:

```ts
type PatternStructureDto = {
  patternInstanceId: string
  generatedAt: string
  modelVersion: string
  sourceEvidenceChainId: string
  eligibility: {
    canShow: boolean
    reason?: 'insufficient_evidence' | 'unreviewed_candidate' | 'low_confidence' | 'abstained' | 'safety_blocked'
  }
  sections: Array<{
    key: 'situations' | 'response' | 'sequence' | 'impact' | 'exceptions'
    title: string
    body: string
    evidenceItemIds: string[]
  }>
  userFacingDisclaimer: string
}
```

Important constraints:

- Structure sections must reference evidence item IDs.
- Structure must not introduce new claims absent from evidence.
- Structure must not use diagnostic, causal, or treatment language.
- Structure should be unavailable when the engine abstains or evidence is insufficient.

### 7.3 Understanding Layer Contract

Only needed for the second layer:

```ts
GET /api/pattern/understanding?domain=&status=confirmed,partially_confirmed
```

Recommended DTO:

```ts
type PatternUnderstandingDto = {
  generatedAt: string
  eligibility: {
    canShow: boolean
    reason?: 'not_enough_reviewed_patterns' | 'safety_blocked' | 'stale_evidence'
  }
  nodes: Array<{
    patternInstanceId: string
    label: string
    domain: Domain
    status: 'confirmed' | 'partially_confirmed'
  }>
  neighbors: Array<{
    neighborPatternInstanceId: string
    neighborPatternKey: string
    displayLabel: string
    connectorLabel: string
    cooccurrenceCount: number
    sharedSourceCount: number
    representativePairs: Array<{
      sourceEvidenceItemId: string
      neighborEvidenceItemId: string
      sharedContextLabel: string
    }>
  }>
  plainLanguageSummary: string
  userFacingDisclaimer: string
}
```

Avoid contract fields like `rootCause`, `diagnosis`, `severity`, `symptom`, `treatment`, `trauma`, `attachmentStyle`, or `personalityType`.

### 7.4 Store Contract

Add `frontend/src/stores/pattern.ts` only when implementation starts. Suggested state:

- `instances`
- `selectedDomain`
- `selectedStatus`
- `selectedInstanceId`
- `evidenceByInstanceId`
- `structureByInstanceId`
- `understanding`
- `loading`
- `error`

Suggested actions:

- `loadInstances(filters)`
- `loadEvidence(instanceId)`
- `loadStructure(instanceId)`
- `reviewInstance(instanceId, action, payload)`
- `updateInstance(instanceId, payload)`
- `loadUnderstanding(filters)`

## 8. UX Copy and Safety Guardrails

The main risk is that Structure can make uncertain labels look like clinical explanation. The UI must preserve the "mirror, not therapist" stance.

### 8.1 Recommended Copy

Use:

- "A possible pattern in your notes"
- "What your own entries seem to repeat"
- "Evidence the system used"
- "Does this feel accurate?"
- "This is not a diagnosis or explanation of why you are this way."
- "You can reject this; rejected patterns stay hidden unless substantially new evidence appears."

Avoid:

- "Your root cause"
- "Why you behave this way"
- "Your attachment style"
- "Symptoms"
- "Diagnosis"
- "Treatment plan"
- "Severity"
- "Disorder"
- "The AI understands you"

### 8.2 Visual Guardrails

- Prefer cards, quotes, and source links over graphs and causal maps.
- Do not show a brain/personality diagram.
- Do not use clinical colors or medical iconography.
- Keep confidence deemphasized; evidence and user review matter more.
- Show abstain/insufficient-evidence states as successful restraint, not empty failure.

### 8.3 Interaction Guardrails

- Always include reject / edit / defer.
- Never lock a Structure explanation without evidence links.
- Do not write Structure into User Wiki until the user confirms or partially confirms the underlying pattern.
- Do not aggregate rejected or unreviewed candidates into the Understanding layer.
- Do not show Structure for crisis-flagged evidence.

## 9. Recommendation Decision

Decision: **two layers combined**, with sequencing.

Understanding tab is Phase 2 LATER, not part of MVP. The MVP ships only Pattern Card detail extension.

| Layer | Ship priority | Location | Why |
|---|---:|---|---|
| Pattern Card detail extension | First | `/insight` detail drawer/page | Safest, evidence-grounded, reviewable |
| Pattern Understanding layer | Later | `/insight` secondary tab/subroute | Useful for synthesis, but only after reviewed evidence |
| Standalone global Structure app | Do not recommend | N/A | Too authoritative and diagnosis-adjacent |

## 10. Implementation Non-Goals

This audit intentionally does not implement code.

Do not do the following as part of this audit:

- Do not add Vue routes or components.
- Do not add `pattern.ts` API client or Pinia store.
- Do not modify navigation.
- Do not change backend contracts.
- Do not alter `ProfileView.vue`, `ChatView.vue`, or any frontend source.
- Do not add diagnostic or therapy-style UX copy.

## 11. Final Recommendation

Pattern Structure belongs **inside Pattern Discovery**, not inside Profile, Chat, Wall, Pet, or Doctor Dashboard.

The first product move should be a Pattern Card detail extension in the planned `/insight` route. The second move can be an `/insight` Understanding tab that only uses confirmed or partially confirmed patterns. This keeps the experience grounded in user-recognized evidence, preserves user authority, and reduces the risk that Structure reads like a diagnosis or therapeutic interpretation.
