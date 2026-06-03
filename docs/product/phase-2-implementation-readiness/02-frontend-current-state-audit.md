# Frontend Current State Audit — Pattern Structure MVP

> Date: 2026-05-31
> Project: InnerFlow Pattern Engine / Phase 2 Implementation Readiness
> Scope: docs-only frontend readiness audit. No Vue, TypeScript, route, package, or component implementation changes are included.

## 1. Purpose

This audit reviews the current frontend codebase for readiness to implement the Pattern Structure MVP later. It is grounded in:

- `docs/product/pattern-structure-ux-audit.md`
- `docs/product/phase-2-design-round-1/01-pattern-structure-api-contract.md`
- `docs/product/phase-2-design-round-1/03-frontend-ia-component-contracts.md`
- `docs/product/phase-2-design-round-1/04-pattern-review-state-machine.md`
- `docs/product/phase-2-design-round-1/05-pattern-structure-safety-lint-eval.md`

Inspected frontend paths:

- `frontend/src/`
- `frontend/src/views/`
- `frontend/src/api/`
- `frontend/src/stores/`
- `frontend/src/router/`

This document is intentionally limited to implementation readiness and does not propose any out-of-scope product surface.

## 2. Current Routes and Navigation Relevant to `/insight`

### 2.1 Router State

The current router defines seven routes in `frontend/src/router/index.ts`:

| Path | Name | View | Auth | Current role |
| --- | --- | --- | --- | --- |
| `/login` | `login` | `LoginView.vue` | No | Login and registration |
| `/` | `chat` | `ChatView.vue` | Yes | Main conversation surface |
| `/tap` | `tap` | `TapView.vue` | Yes | Tapping interaction / pet vitality input |
| `/wall` | `wall` | `WallView.vue` | Yes | Public/private check-in wall |
| `/profile` | `profile` | `ProfileView.vue` | Yes | Mood analytics, User Wiki, correction controls, emotional canvas |
| `/pet` | `pet` | `PetView.vue` | Yes | Companion growth visualization |
| `/doctor` | `doctor` | `DoctorDashboard.vue` | No | Clinical-adjacent dashboard |

There is currently no `/insight` route, no `InsightView.vue`, and no route-level place for Pattern Discovery or Pattern Structure. Any future `/insight` implementation will need a new authenticated route and a clear decision about how it appears in the existing navigation.

### 2.2 Current Navigation Pattern

Navigation is currently view-local rather than centralized:

- `ChatView.vue` uses a top-right icon cluster to navigate to `/tap`, `/wall`, `/pet`, and `/profile`.
- `WallView.vue`, `ProfileView.vue`, `PetView.vue`, and `TapView.vue` each carry their own bottom navigation markup and styling.
- `goTo(path)` helpers are duplicated across views and call `router.push(path)` directly.
- The current active navigation states are manually assigned per view rather than derived from route metadata.

Readiness implication: adding `/insight` later is feasible, but touching every duplicated navigation block creates a high risk of inconsistent placement, active state, and icon semantics. The current frontend does not have a single `BottomNav` or app-shell component where `/insight` can be added once.

### 2.3 `/insight` Placement Readiness

The design docs recommend `/insight` as the Pattern Discovery surface, with Pattern Structure inside the pattern detail area. The current code has no exact route precedent, but `ProfileView.vue` is the closest user-reflection surface because it already contains:

- emotional insight summary content;
- chart cards and aggregate summaries;
- User Wiki correction/deletion controls;
- empty states for missing chart data;
- a full-screen image preview overlay.

However, `/profile` should not become the Pattern Structure home. The UX audit warns that placing Pattern Structure under a profile-style “what I know about you” frame can feel too authoritative. The later implementation should use `/insight` for the Pattern Discovery list and pattern detail flow, while borrowing only neutral layout primitives from `/profile`.

## 3. Existing Reusable Visual and Layout Patterns

### 3.1 Global Visual Primitives

`frontend/src/assets/main.css` defines reusable visual language that can support a restrained Pattern Structure MVP:

- design tokens for gradients, surfaces, text colors, shadows, radii, and motion;
- `.glass-card` and `.glass-card-strong` card containers;
- `.btn-primary` gradient button styling;
- mood-specific background variables;
- soft background treatment aligned with the current product feel.

These primitives are usable later for Pattern Cards, detail panels, section cards, empty states, and review actions if the implementation avoids adding clinical or personality-test framing.

### 3.2 View-Level Layout Patterns

Useful view-level precedents include:

- `ProfileView.vue`: stacked cards, overview metric cards, SVG chart area, distribution bars, chip lists, inline edit controls, change timeline, menu list, and image overlay.
- `WallView.vue`: tab bar, scrollable card list, loading text, empty text, input card, toast-like success state, and card footer actions.
- `ChatView.vue`: top navigation bar, persona menu, message list, assistant typing state, latest image card, and fixed input area.
- `PetView.vue`: top status card, progress/cohesion display, metric cards, and bottom navigation.
- `DoctorDashboard.vue`: richer loading/error/empty and modal patterns, but it is clinical-adjacent and should not be used as the visual model for user-facing Pattern Structure.

### 3.3 Extraction Potential

The current visual system is mostly implemented as repeated scoped CSS inside large single-file views. This gives the Pattern Structure work many examples to reuse, but little actual reuse at the component level. The most implementation-ready primitives are CSS conventions rather than components.

## 4. Existing API Client and Store Patterns

### 4.1 API Client

The frontend has a shared Axios wrapper in `frontend/src/api/request.ts`:

- `baseURL` is empty so Vite proxying can forward API calls.
- A request interceptor attaches `Authorization: Bearer <token>` from the auth store.
- A response interceptor unwraps `response.data`.
- `401` and `403` responses trigger logout and redirect to `/login`.

This is sufficient for future Pattern Structure API calls, including the planned endpoints:

- `GET /api/v1/pattern-instances/{pattern_instance_id}/structure`
- `GET /api/v1/pattern-instances/{pattern_instance_id}/structure/eligibility`
- `GET /api/v1/pattern-instances/{pattern_instance_id}/structure/evidence`

Readiness gap: only auth has a typed feature API module. Most feature calls are made directly inside views using `request.get`, `request.post`, or `request.delete`. A later Pattern Structure implementation should add a dedicated API module rather than embedding endpoint strings inside `InsightView.vue` or future Pattern components.

### 4.2 Existing Feature API Usage

Current feature endpoints are concentrated inside views:

| Area | Endpoint examples | Current location |
| --- | --- | --- |
| Auth | `/api/auth/login`, `/api/auth/register` | `frontend/src/api/auth.ts` |
| Chat history | `/api/chat/history` | `ChatView.vue` |
| Persona | `/api/emotion/persona` | `ChatView.vue` |
| Emotion image | `/api/emotion-image/latest`, `/api/emotion-image/recent` | `ChatView.vue`, `ProfileView.vue` |
| Mood analytics | `/api/emotion-log/overview`, `/trend`, `/distribution`, `/insight` | `ProfileView.vue` |
| User Wiki | `/api/memory/wiki`, `/correct`, delete wiki | `ProfileView.vue` |
| Check-ins | `/api/checkin/wall`, `/history`, post check-in | `WallView.vue` |
| Pet | `/api/pet`, `/api/pet/tap` | `PetView.vue`, `TapView.vue` |
| Doctor dashboard | `/api/doctor/...`, `/mcp/tools/call` | `DoctorDashboard.vue` |

There is no existing `pattern.ts`, `insight.ts`, or structure-related API client.

### 4.3 Stores

The only current Pinia store is `frontend/src/stores/auth.ts`. It stores:

- token;
- user ID;
- username;
- computed login state;
- login persistence and logout cleanup.

There is no store for pattern instances, pattern review state, eligibility, evidence references, or structure modules. Current feature state is local to each view. That is workable for a minimal `/insight` first pass, but Pattern Structure has enough cross-component state that a dedicated store or composable should be planned before implementation.

## 5. Component Extraction Candidates

The Pattern Structure MVP should not start by adding logic into another large view. The current app already has large view files, including `ChatView.vue`, `ProfileView.vue`, `WallView.vue`, `PetView.vue`, and `DoctorDashboard.vue`. A later implementation should extract components around clear contracts from the frontend IA document.

Recommended extraction candidates, based on current frontend patterns:

| Candidate | Source precedent | Later purpose |
| --- | --- | --- |
| App shell / bottom navigation | repeated bottom nav in `WallView.vue`, `ProfileView.vue`, `PetView.vue`, `TapView.vue` | Add `/insight` once and keep route active states consistent |
| Top bar | `ChatView.vue`, `WallView.vue`, `PetView.vue` | Provide consistent page titles and navigation affordances |
| Card container | global `.glass-card`, `.glass-card-strong`, view-local card classes | Standardize Pattern Card and section card framing |
| Tab bar | `WallView.vue` | Support `/insight` list filtering or future local tabs if needed |
| Loading / empty / blocked state | `WallView.vue`, `ProfileView.vue`, `DoctorDashboard.vue` | Render eligibility, insufficient evidence, rejected, deferred, and safety-blocked states distinctly |
| Inline edit controls | `ProfileView.vue` User Wiki controls | Support review edit/correct actions without implying passive confirmation |
| Chip list | `ProfileView.vue` User Wiki triggers | Display compact evidence tags or safe categorical labels if supported by DTOs |
| Evidence list / timeline | `ProfileView.vue` progress notes and changelog timeline | Display safe evidence references and audit-friendly context |
| Modal / overlay | `ProfileView.vue` image preview, `DoctorDashboard.vue` modal | Support evidence expansion drawers or overlays if used later |
| Distribution bars / simple SVG chart | `ProfileView.vue` | Render scene distribution or temporal summaries as simple lists, bars, or text sections |

Extraction should happen only when implementing frontend code later. This audit does not create these components.

## 6. Later Placement of Pattern Components

### 6.1 Route and View Placement

Future implementation should place the user-facing Pattern Discovery and Pattern Structure entry under a new authenticated `/insight` route, likely backed by an `InsightView.vue` in `frontend/src/views/`.

This route should own:

- Pattern Discovery list state;
- selected pattern instance state;
- review action entry points;
- Pattern Detail panel or drawer visibility;
- eligibility-driven structure loading.

It should not be nested under `/profile`, `/doctor`, `/wall`, `/pet`, or `/tap`.

### 6.2 Component Placement

Later code should place reusable Pattern Structure components under `frontend/src/components/`, preferably in a dedicated pattern or insight subdirectory if the project adopts subfolders. The design contract names these future components:

- `PatternCard`
- `PatternDetail`
- `StructurePanel`
- `SceneDistributionSection`
- `RelationshipObjectsSection`
- `TemporalStructureSection`
- `NeighborPatternList`
- `EvidenceReferenceList`
- `ReviewActions`
- `SafetyNotice`

Recommended future ownership:

| Future item | Suggested location | Rationale |
| --- | --- | --- |
| `InsightView.vue` | `frontend/src/views/` | Route-level composition and data orchestration |
| `PatternCard` | `frontend/src/components/insight/` or `frontend/src/components/patterns/` | Reusable list card with review state and safe summary |
| `PatternDetail` | same component folder | Detail/drawer content for one pattern instance |
| `StructurePanel` | same component folder | Parent renderer for eligibility and module status states |
| Structure sections | same component folder | Keep scene, relationship object, temporal, and neighbor modules separately testable |
| `EvidenceReferenceList` | same component folder | Reusable citation expansion display |
| `ReviewActions` | same component folder | Encapsulate confirm, partially confirm, reject, defer, edit/correct action rendering |
| Pattern API client | `frontend/src/api/` | Keep `/api/v1/pattern-instances/...` endpoint strings out of views |
| Pattern store/composable | `frontend/src/stores/` or a future composables folder | Centralize selected pattern, eligibility, module, evidence, loading, and error state |

### 6.3 StructurePanel Placement Inside Detail

The frontend IA document places Pattern Structure inside `PatternDetail`, not as a standalone top-level page. Later implementation should follow this layering:

1. `/insight` renders the Pattern Discovery list.
2. Selecting a pattern opens or navigates to `PatternDetail`.
3. `PatternDetail` shows evidence-backed summary and review state.
4. `StructurePanel` appears inside the detail only when backend eligibility allows it or when a restrained unavailable state must be shown.
5. Structure sections render from module statuses rather than guessing missing content.

## 7. Risks From Current Frontend Organization

### 7.1 Large Views and Local State

Several views combine template, business logic, API calls, formatting helpers, and scoped styling in one file. Adding Pattern Structure directly into an `InsightView.vue` without early extraction would likely create another large, hard-to-review view.

Mitigation: define API, state, and component boundaries before adding view logic.

### 7.2 Duplicated Navigation

The repeated bottom navigation makes `/insight` rollout error-prone. Without an app-shell extraction, future work must update several views consistently.

Mitigation: when implementation begins, centralize navigation before or during `/insight` route work.

### 7.3 View-Embedded API Calls

Direct endpoint calls inside views make it harder to enforce Pattern Structure safety semantics consistently. The structure API requires exact handling for eligibility, module statuses, safety-blocked modules, and evidence expansion.

Mitigation: add a dedicated pattern API client and keep DTO handling centralized.

### 7.4 No Pattern State Owner

Pattern Structure requires selected pattern state, eligibility state, module loading state, review action results, and evidence expansion state. The current app has no pattern store and no precedent beyond auth.

Mitigation: introduce a narrow pattern store or composable when implementing `/insight`, rather than scattering refs across nested components.

### 7.5 Empty, Error, and Safety-Blocked States Are Not Standardized

The current app has multiple loading and empty-state styles, but no shared state component. Pattern Structure must distinguish:

- loading;
- empty list;
- insufficient evidence;
- unreviewed candidate;
- rejected;
- deferred;
- archived;
- safety-blocked;
- module-level hidden-for-safety.

Mitigation: implement restrained state components or shared state copy rules before wiring structure content.

### 7.6 Clinical-Adjacent Visual Leakage

`DoctorDashboard.vue` contains polished dashboards, charts, modals, and evidence panels, but it is clinical-adjacent and includes crisis/CBT/FHIR/EHR concepts. Reusing its visual language for Pattern Structure risks violating the safety and UX constraints.

Mitigation: borrow only generic implementation ideas such as loading/error handling or modal mechanics. Do not reuse clinical framing, medical icons, diagnostic copy, or dashboard posture for `/insight`.

### 7.7 Existing “What I Know About You” Framing

`ProfileView.vue` has a User Wiki surface named “What I Know About You”. It is useful as a correction-control precedent, but Pattern Structure should not inherit that authority posture.

Mitigation: use evidence-backed, pattern-specific, user-reviewable language in `/insight`, and keep partial confirmations visibly scoped.

### 7.8 Starter Components Remain in `frontend/src/components/`

The components directory still contains starter Vue components such as `HelloWorld.vue`, `TheWelcome.vue`, and icon examples. The production UI currently lives mostly in views.

Mitigation: do not model Pattern Structure around starter components. Add future Pattern components deliberately under a dedicated namespace.

## 8. Suggested Implementation Sequence

This sequence is for later implementation planning only. It does not include code in this audit.

1. Add a centralized app navigation plan for `/insight`, including route auth, active state, and whether bottom navigation should be extracted first.
2. Add a typed Pattern API client for listing/reviewing pattern instances and consuming the structure, eligibility, and evidence endpoints.
3. Define a narrow pattern state owner for selected pattern, loading, eligibility, module status, evidence expansion, and review action state.
4. Create `/insight` with a Pattern Discovery list and evidence-backed `PatternCard` states before rendering Pattern Structure modules.
5. Add `PatternDetail` with review actions aligned to `candidate`, `confirmed`, `partially_confirmed`, `rejected`, `deferred`, and `archived` semantics.
6. Add `StructurePanel` inside `PatternDetail`, gated by backend `can_show_structure` and `EligibilityState`, with no client-side inference of eligibility.
7. Add structure sections one at a time: scene distribution, relationship objects, temporal structure, and neighbor pattern list, each honoring module-level statuses.
8. Add `EvidenceReferenceList` expansion using safe evidence references and defaulting away from raw excerpts unless explicitly allowed by the API.
9. Add shared restrained state rendering for insufficient evidence, deferred, rejected, unreviewed, archived, and safety-blocked states.
10. Validate copy and visual states against the safety/lint requirements before enabling the Structure entry point broadly.

## 9. Readiness Summary

The frontend is partially ready for Pattern Structure MVP implementation:

- The shared Axios wrapper and auth guard are enough to support new protected Pattern APIs.
- The existing glass-card visual language can support Pattern Cards and detail sections.
- `ProfileView.vue` provides useful precedents for reflection cards, correction controls, charts, chips, timelines, and overlays.
- The current app lacks `/insight`, a pattern API client, a pattern state owner, shared navigation, and reusable Pattern components.
- The main readiness risk is organizational rather than visual: current features are view-local, duplicated, and loosely typed.

The recommended next frontend step is not to build Structure modules first. The safer sequence is to create the `/insight` route shell, centralize pattern API/state handling, render evidence-backed Pattern Cards and review flows, and only then place `StructurePanel` inside `PatternDetail` under backend eligibility and safety controls.
