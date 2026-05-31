# Pattern Structure MVP Test Strategy and Acceptance Mapping

## 1. Purpose

This document maps the Phase 2 Pattern Structure MVP design contracts to a future test strategy for backend, API, frontend, fixtures, safety/lint acceptance, and CI. It is an implementation-readiness planning artifact only.

This document does not add Java tests, frontend tests, fixture files, build configuration, or implementation code.

## 2. Source Contracts Reviewed

Primary design inputs:

- `docs/product/phase-2-design-round-1/01-pattern-structure-api-contract.md`
- `docs/product/phase-2-design-round-1/02-pattern-structure-domain-model.md`
- `docs/product/phase-2-design-round-1/03-frontend-ia-component-contracts.md`
- `docs/product/phase-2-design-round-1/04-pattern-review-state-machine.md`
- `docs/product/phase-2-design-round-1/05-pattern-structure-safety-lint-eval.md`

Fixture dependency:

- `docs/product/phase-2-design-round-1/06-pattern-structure-fixtures-acceptance.md` is not present in this worktree. Fixture-specific IDs, payload names, and exact example data must be filled in by a later readiness pass after that document exists. This plan therefore maps fixture categories and expected assertions without inventing fixture contents.

Existing test/tooling observations:

- Backend tests already live under `src/test/java/com/ling/linginnerflow/` and use JUnit 5, AssertJ, and Spring Boot test support.
- Existing relevant backend test patterns include direct unit tests for validation and verification helpers, controller/API tests with Spring MVC patterns, and offline validation/eval runners under `pattern/validation` and `pattern/eval`.
- Existing backend Pattern Engine packages include `pattern/controller`, `pattern/domain`, `pattern/entity`, `pattern/repo`, `pattern/safety`, `pattern/service`, and `pattern/verify`.
- Frontend has a Vue/Vite app in `frontend/` with `vue-tsc` and build scripts, but no frontend test runner or component test files are currently configured.

## 3. Backend Unit Test Targets

Future unit tests should focus on pure contract invariants before repository, controller, or LLM-adjacent behavior.

| Target area | Future unit target | Contract assertions | Priority |
|---|---|---|---|
| Review eligibility mapping | Pattern Structure eligibility mapper/service | `confirmed` and permitted `partially_confirmed` map to `allowed`; `candidate`, `deferred`, `rejected`, `archived`, stale, unsupported, and crisis-blocked states map to the explicit API eligibility states and reason codes. | P0 |
| Domain invariants | Pattern Structure domain/service validators | No `PatternStructure` is user-facing without accepted evidence, eligible review status, safe output, and module-level status. | P0 |
| Evidence coverage | Evidence aggregation/coverage evaluator | Evidence count, source chain IDs, observation window, sparse windows, and missing direct references produce `available` or `insufficient_data` module statuses without guessed copy. | P0 |
| Module status normalization | Structure module assembler | Every module emits one of `available`, `insufficient_data`, `hidden_for_safety`, `not_applicable`, or `not_requested`; no ad hoc status literals leak. | P0 |
| API DTO serialization helpers | DTO mapper classes | UUIDs serialize as strings, timestamps as ISO 8601 with offsets, percentages as numbers from `0` to `1`, empty arrays as `[]`, and nullable scalars only where explicitly allowed. | P1 |
| Scene distribution | Scene module builder | Scene labels remain coarse context labels, percentages stay numeric, top categories require accepted evidence references, and sparse/mixed/unknown buckets are preferred over precision. | P1 |
| Relationship objects | Relationship module builder | Role/object labels are neutral evidence-present descriptions and avoid motive, blame, personality, attachment-style, or action-advice claims. | P1 |
| Temporal structure | Temporal module builder | Ordering and recurrence claims require direct evidence-chain support and do not imply cause, trigger, escalation, symptom progression, or severity. | P1 |
| Neighbor patterns | Neighbor module builder | Neighbor output remains a flat list, is based on allowed overlap signals, and never produces connected graph/topology semantics. | P1 |
| `partially_confirmed` caution | Review-summary selection helper | User-edited partial wording is preferred where present, system wording does not broaden it, and partial displays can be distinguished from fully confirmed displays. | P0 |
| Safety/lint result application | Safety result applier | Blockers remove the affected field, module, summary, or full structure; warnings only pass when represented as restrained states. | P0 |
| Error shape mapping | API error mapper | 400/401/403/404/409/429/500 responses use stable error fields and do not leak internal details. | P1 |

Unit tests should follow the existing style of focused tests under `src/test/java/com/ling/linginnerflow/pattern/validation`, `pattern/verify`, and `exception`: construct small inputs directly, assert explicit enum/status outputs, and avoid Spring context unless API wiring is under test.

## 4. Backend Integration and API Test Targets

API tests should verify the external contract and backend-owned eligibility decisions, not internal implementation details.

| Endpoint / flow | Future integration assertions | Priority |
|---|---|---|
| `GET /api/v1/pattern-instances/{pattern_instance_id}/structure` allowed | Returns aggregate structure with identity metadata, eligibility, evidence window, module sections, module statuses, and evidence references that conform to the V1 DTO contract. | P0 |
| `GET /api/v1/pattern-instances/{pattern_instance_id}/structure` ineligible | Returns structure-unavailable state with no generated module payload for unreviewed, deferred, rejected, archived, unsupported, stale, or safety-blocked inputs. | P0 |
| `GET /api/v1/pattern-instances/{pattern_instance_id}/structure/eligibility` | Returns `state`, `can_show_structure`, `pattern_status`, `trust_tier`, `reason_code`, `display_message`, `required_actions`, `evidence_summary`, `safety_summary`, and optional `cooldown_summary` consistently for every review status. | P0 |
| `GET /api/v1/pattern-instances/{pattern_instance_id}/structure/evidence` | Returns accepted evidence items only, with stable IDs, source chain references, timestamps, source types, safety visibility, and no hidden unsafe excerpts by default. | P1 |
| Rejected cooldown | Rejected/cooldown states include `cooldown_summary` only when relevant and do not allow structure display before reopen rules permit it. | P1 |
| Partial availability | Safety-blocked or evidence-insufficient modules remain distinguishable from available modules, and unaffected modules are returned only when their summaries do not depend on blocked content. | P0 |
| Stable enum contract | All review status, eligibility state, reason code, action, and module status literals match the design docs exactly. | P0 |
| Unsupported client requests | Unsupported pattern/source/module requests return `unsupported` or `not_requested` states instead of defaulting to speculative data. | P1 |
| Error response contract | Missing pattern, unauthorized access, forbidden access, validation failures, rate limiting, and internal errors produce stable V1 error shapes. | P1 |

Recommended future test placement:

- Controller/API tests: `src/test/java/com/ling/linginnerflow/pattern/controller/`
- Service/integration tests: `src/test/java/com/ling/linginnerflow/pattern/service/`
- Domain mapper tests: `src/test/java/com/ling/linginnerflow/pattern/domain/` or a future `pattern/structure/` package if implementation introduces one.

## 5. Frontend Component and State Test Targets

Frontend tests should be planned as contract tests for rendering state and user control. They should not be added until a frontend test runner is selected and configured in a separate implementation PR.

| Component/state target | Future assertions | Priority |
|---|---|---|
| `/insight` placement | Pattern Structure appears inside Pattern Card detail on the `/insight` surface, not as a new bottom navigation item or Understanding tab implementation. | P1 |
| `PatternCard` | Shows review status and opens detail without implying Pattern Structure is available for unreviewed or ineligible patterns. | P1 |
| `PatternDetail` | Hosts `StructurePanel`, preserves back/deep-link behavior, and keeps review actions available where the state machine permits them. | P1 |
| `StructurePanel` | Branches from backend `eligibility.state` and module statuses, not inferred partial field presence. | P0 |
| Loading state | Displays loading separately from empty, insufficient, rejected, deferred, and safety-blocked states. | P1 |
| Empty and insufficient states | Uses backend-provided neutral copy and `required_actions`; does not invent explanatory language. | P0 |
| Rejected/deferred/unreviewed states | Shows restrained unavailable states and does not render stale module data. | P0 |
| Crisis/safety-blocked state | Presents successful restraint, not an error/loading failure, and does not substitute speculative fallback copy or clinical visuals. | P0 |
| `SceneDistributionSection` | Renders coarse labels, numeric percentages formatted for display, accepted evidence references, sparse/unknown/mixed states, and no causal language. | P1 |
| `RelationshipObjectsSection` | Renders neutral roles/objects and avoids blame, motive, personality, attachment-style, or action-advice framing. | P1 |
| `TemporalStructureSection` | Renders observed order/recurrence only and avoids cause, trigger, escalation, symptom progression, or severity framing. | P1 |
| `NeighborPatternList` | Renders a flat list only, with no graph, connected map, topology, or network visualization behavior. | P0 |
| `EvidenceReferenceList` | Shows safe evidence references when available, handles hidden excerpts, and does not expose safety-hidden representative excerpts by default. | P1 |
| `ReviewActions` | Supports reject, edit, defer, and allowed review actions without redefining transition semantics client-side. | P0 |
| `SafetyNotice` | Distinguishes safety-blocked, policy-hidden, evidence-insufficient, and unavailable states using backend reason fields. | P0 |
| `partially_confirmed` display | Shows user-edited narrow wording where provided and visually/copy-wise distinguishes partial confirmation from full confirmation. | P0 |

CI implication for frontend: because `frontend/package.json` currently has build and type-check scripts but no test script or test runner, the minimal first frontend readiness step is a later tooling PR that chooses the runner and adds a small smoke/component harness. This acceptance mapping should not be blocked on that tooling decision.

## 6. Fixture-to-Test Mapping

The missing fixture acceptance document should eventually become the canonical source for exact fixture names and payloads. Until then, future tests should reserve fixture categories that cover each contract surface.

| Fixture category | Backend unit mapping | API mapping | Frontend mapping | Expected acceptance outcome |
|---|---|---|---|---|
| Confirmed eligible pattern with all modules | Eligibility mapper, module builders, evidence coverage | Structure endpoint returns `allowed` and available modules | `StructurePanel` renders all section components | Full structure visible with accepted evidence references. |
| Partially confirmed with user-edited wording | Review-summary selector, safety result applier | Structure/eligibility uses partial reason and safe edited wording | Partial state is visibly distinct and does not broaden user wording | Narrow user-confirmed claim is preserved. |
| Confirmed but sparse evidence | Coverage evaluator, module status normalizer | Eligibility or modules return `insufficient_evidence`/`insufficient_data` | Insufficient state renders without guessed copy | Sparse data is restrained. |
| Candidate/unreviewed | Review eligibility mapper | Eligibility returns `unreviewed`, `can_show_structure: false` | Unreviewed state renders review action | No modules shown before user review. |
| Deferred | Review eligibility mapper | Eligibility returns `deferred`, `can_show_structure: false` | Deferred state renders without stale modules | No user-facing structure until review resumes. |
| Rejected with cooldown | Cooldown mapper, review status mapper | Eligibility returns `rejected` or cooldown reason with `cooldown_summary` | Rejected/cooldown copy renders, structure hidden | Rejected patterns cannot leak structure. |
| Archived or stale evidence | Staleness/archival eligibility mapper | Eligibility returns `unsupported`, `archived`, or `stale_evidence` reason | Unavailable state renders | Old state is not presented as current structure. |
| Crisis/safety-blocked pattern | Safety gate, result applier | Eligibility returns `crisis_safety_blocked` and no generated modules | Safety-blocked state renders as restraint | No unsafe module or fallback copy appears. |
| Safety-blocked single module | Module safety applier, summary dependency checker | Affected module is `hidden_for_safety`; dependent summary is removed or neutralized | Section-level safety notice renders | Safe independent modules may remain if not dependent. |
| Hidden excerpts with allowed counts | Evidence visibility mapper | Evidence endpoint omits default hidden excerpts but may expose counts when policy allows | Evidence list shows hidden/aggregate state | Sensitive evidence is counted only when allowed. |
| Unsupported pattern/source type | Eligibility mapper, request validation | Eligibility returns `unsupported_*` or `structure_not_enabled` reason | Unsupported state renders from backend message | Unsupported inputs do not produce speculative structure. |
| Neighbor overlap only | Neighbor builder | Neighbor list response is flat with overlap/distinction labels | `NeighborPatternList` remains flat | No graph/network/topology UI. |

Fixture readiness rule: every acceptance fixture should declare expected review status, eligibility state, reason code, module statuses, safety/lint result, evidence visibility, and allowed frontend state. Tests should assert those explicit expectations rather than infer them from generated prose.

## 7. Negative Acceptance Tests

Negative tests should prove the product refuses unsafe or unsupported output rather than merely proving happy paths work.

| Negative case | Required assertion |
|---|---|
| Candidate/unreviewed pattern requests structure | API returns `can_show_structure: false`; frontend shows review/empty state; no modules render. |
| Rejected pattern still has old generated structure | API does not return stale module payload; frontend does not render cached structure. |
| Deferred pattern has enough evidence | Eligibility remains `deferred`; enough evidence does not override user deferral. |
| Archived or abstained pattern | No V1 user-facing structure display unless a future spec adds that surface. |
| Sparse evidence produces confident summary | Summary is blocked, downgraded, or replaced with `insufficient_data`; no confident copy appears. |
| Missing evidence reference for a displayed claim | Claim/module is blocked or marked insufficient; API does not emit unsupported displayed claims. |
| Scene label implies cause | Safety/lint blocks or downgrades claim even if the scene appears in evidence. |
| Relationship label implies motive/blame/personality | Safety/lint blocks label/module; frontend does not render softened blame copy. |
| Temporal label implies trigger/escalation/severity | Safety/lint blocks or downgrades temporal output. |
| Neighbor output asks for graph/topology | API remains flat; frontend has no graph/network rendering path for MVP. |
| Hidden evidence excerpt requested by default display | Evidence endpoint and frontend omit the excerpt while preserving allowed aggregate state. |
| `partially_confirmed` system summary broadens user edit | Broadened summary is blocked; user-edited wording is preferred where display is allowed. |
| Unsupported source type | Eligibility returns unsupported reason; no generated structure appears. |
| Unknown enum from backend in frontend | Frontend falls back to restrained unavailable/error-safe state, not speculative rendering. |

## 8. Safety and Lint Acceptance Tests

Safety/lint acceptance should run at three levels: module-level, summary-level, and API/frontend-state-level.

| Safety/lint area | Acceptance assertions |
|---|---|
| Forbidden language categories | Clinical, diagnostic, causal, treatment, personality, blame, motive, severity, prediction, or advice-like claims are blocked even when similar wording exists in source evidence. |
| Evidence coverage invariants | Every displayed structural claim has accepted evidence references; unsupported claims are removed, downgraded, or blocked. |
| Direct evidence requirements | Scene, relationship, temporal, neighbor, recurrence, distinction, and summary claims each require direct references as defined by the safety spec. |
| Claims blocked even with evidence | Crisis, diagnosis, treatment, identity, manipulation, intent, or blame framing stays blocked even when evidence appears to support it. |
| Blocker semantics | Critical/high blockers prevent affected field, module, summary, or whole structure display. |
| Warning semantics | Warnings only pass with explicit restrained state such as sparse evidence, aggregate-only, unknown/mixed bucket, or hidden excerpt display. |
| Combined-field meaning | Individually safe module fields are rechecked at summary/display level to prevent a combined sensitive implication. |
| Partial confirmation smoothing | System paraphrase does not smooth away user caveats, scope limits, or exceptions. |
| Safety-blocked API state | Backend distinguishes `hidden_for_safety`, `crisis_safety_blocked`, and `insufficient_data`; frontend does not collapse them into loading or generic failure. |
| Evidence privacy | Safety-hidden representative excerpts are not included in default display payloads; counts appear only when policy allows. |
| Visual safety | Frontend does not compensate for blocked text with clinical visuals, causal diagrams, connected maps, medical iconography, personality framing, or graph-like neighbor visuals. |

Safety acceptance should use deterministic fixtures first. Any LLM-assisted linting later needs a separate eval strategy with snapshot expectations, severity labels, and human-review paths for ambiguous high-severity cases.

## 9. CI Implications

Backend CI should eventually separate fast deterministic checks from slower acceptance/eval checks.

| CI lane | Future contents | Blocking policy |
|---|---|---|
| Backend unit | Eligibility mappers, domain invariants, module builders, safety result application, DTO mappers | Required on every PR. |
| Backend API contract | Spring MVC/API tests for structure, eligibility, evidence, and error response shapes | Required on Pattern Structure backend PRs; eventually every PR once stable. |
| Backend fixture acceptance | Deterministic acceptance fixtures derived from the missing fixture doc | Required before Pattern Structure MVP release; may start as targeted CI lane. |
| Safety/lint acceptance | Forbidden-language, evidence-coverage, blocker/warning, and hidden-excerpt checks | Required before any user-facing enablement. |
| Frontend type/build | Existing `frontend` type-check/build scripts | Required for frontend PRs; can remain build/type-only until test tooling exists. |
| Frontend component/state | Future component tests for `StructurePanel`, sections, evidence references, review actions, and safety states | Required after frontend test tooling is introduced. |
| Cross-contract drift | Enum/DTO literal checks shared between API docs, backend DTOs, and frontend types | Required once generated or shared contract artifacts exist. |

CI should fail on changed enum literals, missing evidence references for displayed claims, unsafe fallback copy, safety-hidden excerpt leakage, graph/topology neighbor UI, or frontend inference that bypasses backend `eligibility.state`.

## 10. Minimal First Test PR

The smallest valuable future test PR should be backend-only and deterministic, without frontend test tooling or fixture creation.

Recommended first PR scope:

1. Add unit tests for the Pattern Structure eligibility mapper once it exists.
2. Cover all review statuses: `candidate`, `confirmed`, `partially_confirmed`, `rejected`, `deferred`, and `archived`.
3. Cover non-review blockers: insufficient evidence, stale evidence, unsupported pattern/source, structure disabled, and crisis/safety-blocked.
4. Assert exact `EligibilityState`, `EligibilityReasonCode`, `can_show_structure`, `required_actions`, and `cooldown_summary` presence/absence.
5. Add one module-status normalization unit test that proves unsupported or unsafe module outputs become `insufficient_data`, `hidden_for_safety`, `not_applicable`, or `not_requested` rather than guessed content.
6. Do not add Java implementation solely for tests; wait until the mapper/service exists in the Pattern Structure implementation PR.

Why this first PR:

- It locks the highest-risk backend-owned boundary before UI rendering depends on it.
- It is fast and deterministic under existing JUnit/Spring Boot test dependencies.
- It does not require frontend test-runner decisions.
- It does not require the missing fixture acceptance document.
- It creates a stable base for later API contract, fixture acceptance, and frontend component tests.

## 11. Readiness Gaps

Open dependencies before full acceptance coverage:

- Add or locate `docs/product/phase-2-design-round-1/06-pattern-structure-fixtures-acceptance.md` so exact fixture IDs and expected payloads can replace the category-level mapping in this document.
- Decide whether Pattern Structure implementation will introduce a dedicated `pattern/structure` package or extend existing `pattern/service`, `pattern/domain`, and `pattern/safety` packages; test package placement should mirror that decision.
- Choose frontend test tooling for Vue component/state tests; current frontend tooling has build/type-check only.
- Define whether API contract assertions will use hand-written JSON expectations, shared DTO serialization tests, generated OpenAPI snapshots, or a hybrid approach.
- Decide how deterministic safety/lint fixtures will be separated from any later LLM-assisted lint evals.

