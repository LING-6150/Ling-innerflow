# PatternInstance Review State Machine

Project: InnerFlow Pattern Engine / Phase 2 Product Design Round 1  
Status: Design owner for PatternInstance review state semantics and transitions  
Date: 2026-05-31

## 1. Purpose

This document defines the V1 MVP review state machine for `PatternInstance` and the rules that control when Pattern Structure may be shown.

Pattern Discovery may identify a possible repeated pattern. Pattern Structure is a deeper product layer and must not amplify low-trust or unreviewed candidates. In V1 MVP, Pattern Structure is available only after explicit user review produces `confirmed` or `partially_confirmed` status, and only when safety checks allow display.

This document owns:

- Review status meanings.
- Allowed and prohibited transitions.
- Trigger sources for each transition.
- Cooldown semantics for rejected `(pattern_key, domain)` pairs.
- How review state feeds API eligibility and frontend states.

This document does not define database schema, API DTO shape, frontend components, Safety/Lint output policy, or backend implementation.

## 2. Source Inputs Used

This design leans most on these source sections:

- `docs/product/phase-2-pattern-understanding-plan.md` §§4-5 and §9 for the product boundary that Pattern Structure is a separate layer gated behind trusted patterns.
- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md` §§2, 4, 6, 8, and 10 for eligibility, source data, aggregate API behavior, safety boundaries, and MVP non-goals.
- `docs/product/pattern-structure-ux-audit.md` §§5, 7, and 8 for placement, status exposure, review actions, frontend store/API expectations, and interaction guardrails.
- V1.2 product/engine alignment from `docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md` §10 and the V1.2 handoff/spec family for the 90-day rejection cooldown and Jaccard `< 0.5` substantial-difference rule over `source_refs`.

## 3. Design Principles

- **User review is authoritative for confirmation.** The system may propose, refresh, suppress, or reopen candidates, but it must not automatically promote a pattern into `confirmed` or a future `high_trust` state.
- **Structure follows review, not discovery confidence.** Pattern Structure availability is a product decision based on review state and safety, not a reward for higher engine score.
- **Rejected means hidden by default.** Rejection is sticky for 90 days unless substantially different evidence arrives.
- **Deferred means not now.** Deferral suppresses prompts without treating the pattern as false.
- **Safety can only narrow display.** Safety blocks may prevent surfacing or structure display; they do not confirm, reject, or otherwise reinterpret the user's review decision.
- **No V1 `high_trust` state.** `high_trust` remains a future extension concept and is not part of this V1 MVP review state machine.

## 4. Trigger Source Vocabulary

Every transition must have one of these sources.

| Source | Meaning |
|---|---|
| `user_action` | The user explicitly acts on a surfaced pattern, review prompt, or reviewed pattern record. |
| `system_event` | The discovery, evidence, safety, or lint pipeline observes a change and updates review state or display eligibility. |
| `time_based` | A transition occurs because a configured time window expires. |

## 5. Status Definitions

### 5.1 `candidate`

A proposed PatternInstance that has not been accepted, partially accepted, rejected, or deferred by the user.

Rules:

- Created by discovery when a `(pattern_key, domain)` has enough eligible evidence to ask for user review.
- May be shown as a lightweight review card if safety allows.
- Must not expose Pattern Structure.
- Must not be aggregated into Pattern Understanding.
- Must present review actions: confirm, partially confirm, reject, defer, and edit/correct where available.

### 5.2 `confirmed`

A PatternInstance the user explicitly accepts as accurate enough to keep.

Rules:

- Entered only by `user_action`.
- Eligible for Pattern Structure when evidence and safety checks pass.
- May receive future evidence refreshes without losing confirmation.
- Must preserve audit history of the confirming action and evidence version shown at confirmation time.
- Must not be automatically downgraded because evidence becomes stale; stale evidence affects display eligibility, not the user's review decision.

### 5.3 `partially_confirmed`

A PatternInstance the user accepts in part while correcting, narrowing, qualifying, or excluding part of the proposed summary/evidence.

Rules:

- Entered only by `user_action`.
- Eligible for Pattern Structure in V1 MVP, but only for the user-accepted/corrected portion allowed by Safety/Lint.
- Requires an explicit user note, correction payload, scoped acceptance, or equivalent review metadata.
- Future structure generation must use the corrected or accepted scope rather than the original unqualified candidate summary.
- Transition semantics are owned by this document; which summary/version may be shown is jointly governed by the Safety/Lint design.

### 5.4 `rejected`

A PatternInstance the user explicitly says should not be kept or re-shown in its current evidence form.

Rules:

- Entered only by `user_action`.
- Not eligible for Pattern Structure.
- Hidden from normal review and Understanding surfaces.
- Sticky for 90 days for the same `(pattern_key, domain)` unless new evidence is substantially different.
- Substantial difference is defined as Jaccard `< 0.5` between new `source_refs` and rejected `source_refs`.
- Rejection does not mean the user is making a permanent global statement about all future evidence for that key/domain.

### 5.5 `deferred`

A PatternInstance the user postpones without accepting or rejecting.

Rules:

- Entered only by `user_action`.
- Not eligible for Pattern Structure.
- Suppressed from repeated prompting until a defer window expires, new materially relevant evidence arrives, or the user reopens it.
- May remain visible in a low-emphasis "saved for later" review queue if product UX supports that surface.
- Must not be interpreted as confirmation, rejection, or low confidence.

### 5.6 `archived`

A non-primary status for hiding a PatternInstance from active review because it is obsolete, superseded, duplicate, or no longer product-relevant.

Rules:

- Optional in V1 MVP; use only if the implementation needs an explicit inactive terminal state.
- Not eligible for Pattern Structure.
- Must preserve audit history and evidence references.
- Must not be used to bypass rejection cooldown.
- Must not replace `rejected` when the user explicitly rejects a pattern.

## 6. Pattern Structure Eligibility Mapping

| Review status | Pattern Structure availability | API eligibility reason if unavailable | Frontend state |
|---|---|---|---|
| `candidate` | No | `unreviewed_candidate` | Show review prompt/card only; hide Structure entry point. |
| `confirmed` | Yes, if safety and evidence checks pass | `safety_blocked`, `stale_evidence`, or `insufficient_evidence` when blocked | Show Structure entry point; show blocked/empty state if safety or evidence prevents display. |
| `partially_confirmed` | Yes, scoped to accepted/corrected content if Safety/Lint allows | `safety_blocked`, `stale_evidence`, or `insufficient_evidence` when blocked | Show scoped Structure with correction context; avoid unaccepted claims. |
| `rejected` | No | `rejected` or `rejection_cooldown_active` | Hide from normal views; optional review history only. |
| `deferred` | No | `deferred` | Show "not now" or saved-for-later state; hide Structure. |
| `archived` | No | `archived` | Hide from active views; optional history/admin state only. |

Additional rules:

- Safety-blocked evidence cannot be shown even when status is `confirmed` or `partially_confirmed`.
- Ambiguous, low-confidence, abstain-boundary, unreviewed, rejected, deferred, and archived instances cannot expose Pattern Structure in V1 MVP.
- Pattern Understanding may include only `confirmed` and eligible `partially_confirmed` instances.

## 7. User Action Definitions

| User action | Source | Allowed from | Target status | Meaning |
|---|---|---|---|---|
| `confirm` | `user_action` | `candidate`, `deferred`, `partially_confirmed`, reopened `rejected` | `confirmed` | User accepts the pattern as accurate enough to keep. |
| `partially confirm` | `user_action` | `candidate`, `deferred`, `confirmed`, reopened `rejected` | `partially_confirmed` | User accepts part of the pattern and supplies correction, qualification, or scope. |
| `reject` | `user_action` | `candidate`, `deferred`, `confirmed`, `partially_confirmed` | `rejected` | User says the pattern should not be kept or surfaced in its current evidence form. |
| `defer` | `user_action` | `candidate`, reopened `rejected` | `deferred` | User postpones review without accepting or rejecting. |
| `edit/correct` | `user_action` | `candidate`, `confirmed`, `partially_confirmed`, `deferred` | Usually `partially_confirmed`; may remain `confirmed` for non-substantive copy correction | User corrects summary, scope, label, or evidence inclusion. |
| `restore/reopen` | `user_action` | `rejected`, `deferred`, `archived` | `candidate` unless user immediately confirms/partially confirms | User intentionally brings a hidden or postponed pattern back for review. |

User action constraints:

- `confirm` and `partially confirm` must be explicit; they cannot be inferred from opening, reading, or not dismissing a card.
- `edit/correct` must preserve the user's correction as review metadata and must not silently rewrite history.
- `restore/reopen` is allowed only from a user-visible history or review management surface, not from automatic resurfacing.

## 8. System Event Definitions

| System event | Source | Meaning | State machine effect |
|---|---|---|---|
| `new evidence arrives` | `system_event` | Discovery finds additional eligible evidence for an existing `(pattern_key, domain)`. | May refresh evidence, reopen deferred/cooldown-eligible rejected candidates, or update display eligibility. Must not auto-confirm. |
| `evidence becomes stale` | `system_event` | Evidence no longer meets recency/freshness expectations for display. | Blocks or labels Structure eligibility but does not change `confirmed` or `partially_confirmed` review status. |
| `safety block` | `system_event` | Safety/Lint determines that current evidence or copy cannot be shown. | Blocks surfacing and Structure display; may archive only if a separate product rule says the instance is obsolete/unsafe to retain in active review. |
| `rejection cooldown expires` | `time_based` | 90 days have elapsed since rejection for the `(pattern_key, domain)`. | Makes the pair eligible for engine re-evaluation; does not itself surface or confirm the old instance. |

## 9. Complete Transition Table

| From | Trigger | Source | To | Allowed? | Rationale |
|---|---|---|---|---:|---|
| None | discovery creates eligible proposal | `system_event` | `candidate` | Yes | Discovery may propose reviewable candidates when evidence and safety gates pass. |
| None | discovery creates safety-blocked proposal | `system_event` | None | Yes | Unsafe or blocked material should not create a user-facing review state. |
| `candidate` | confirm | `user_action` | `confirmed` | Yes | User explicitly accepts the pattern. |
| `candidate` | partially confirm | `user_action` | `partially_confirmed` | Yes | User accepts a scoped or corrected version. |
| `candidate` | reject | `user_action` | `rejected` | Yes | User explicitly rejects the current evidence form. |
| `candidate` | defer | `user_action` | `deferred` | Yes | User postpones review without deciding. |
| `candidate` | edit/correct | `user_action` | `partially_confirmed` | Yes | Correction creates a scoped accepted version unless the user only edits non-substantive wording. |
| `candidate` | edit/correct non-substantive copy | `user_action` | `candidate` | Yes | User can clarify wording before deciding. |
| `candidate` | safety block | `system_event` | `archived` or hidden `candidate` | Conditional | Allowed only to prevent unsafe surfacing; preserve audit trail if archived. |
| `candidate` | new evidence arrives | `system_event` | `candidate` | Yes | Evidence may refresh the proposal, but cannot confirm it. |
| `candidate` | evidence becomes stale | `system_event` | `candidate` | Yes | Staleness affects display eligibility, not review status. |
| `confirmed` | reject | `user_action` | `rejected` | Yes | User can reverse prior acceptance. |
| `confirmed` | partially confirm / narrow | `user_action` | `partially_confirmed` | Yes | User can revise acceptance scope. |
| `confirmed` | edit/correct substantive scope | `user_action` | `partially_confirmed` | Yes | Substantive corrections require scoped confirmation semantics. |
| `confirmed` | edit/correct non-substantive copy | `user_action` | `confirmed` | Yes | Copy correction does not change review acceptance. |
| `confirmed` | new evidence arrives | `system_event` | `confirmed` | Yes | Evidence refresh may update source chains, but confirmation persists. |
| `confirmed` | evidence becomes stale | `system_event` | `confirmed` | Yes | Structure may be blocked/labeled stale; status remains user-confirmed. |
| `confirmed` | safety block | `system_event` | `confirmed` | Yes | Safety blocks display; it does not erase user review status. |
| `confirmed` | archive obsolete duplicate | `system_event` | `archived` | Conditional | Allowed only for dedup/supersession housekeeping with audit trail; not for user rejection. |
| `partially_confirmed` | confirm full corrected version | `user_action` | `confirmed` | Yes | User can promote the corrected/scoped pattern to full acceptance. |
| `partially_confirmed` | edit/correct | `user_action` | `partially_confirmed` | Yes | User may refine accepted scope over time. |
| `partially_confirmed` | reject | `user_action` | `rejected` | Yes | User can reject after partial acceptance. |
| `partially_confirmed` | new evidence arrives | `system_event` | `partially_confirmed` | Yes | Evidence may refresh but must respect accepted/corrected scope. |
| `partially_confirmed` | evidence becomes stale | `system_event` | `partially_confirmed` | Yes | Staleness affects Structure eligibility, not review status. |
| `partially_confirmed` | safety block | `system_event` | `partially_confirmed` | Yes | Safety blocks display or sections; it does not rewrite review state. |
| `partially_confirmed` | archive obsolete duplicate | `system_event` | `archived` | Conditional | Allowed only for supersession/dedup with audit trail. |
| `rejected` | restore/reopen | `user_action` | `candidate` | Yes | User can intentionally reopen a rejected pattern. |
| `rejected` | restore and confirm | `user_action` | `confirmed` | Yes | User can explicitly reverse rejection and accept. |
| `rejected` | restore and partially confirm | `user_action` | `partially_confirmed` | Yes | User can explicitly reverse rejection with scope/correction. |
| `rejected` | new substantially different evidence arrives inside 90 days | `system_event` | `candidate` | Conditional | Allowed only when Jaccard over `source_refs` is `< 0.5`; engine drives re-evaluation. |
| `rejected` | new non-substantially-different evidence arrives inside 90 days | `system_event` | `rejected` | Yes | Cooldown remains sticky; do not re-surface. |
| `rejected` | rejection cooldown expires | `time_based` | `rejected` | Yes | Expiry only permits engine re-evaluation; it does not automatically reopen. |
| `rejected` | post-cooldown engine finds eligible evidence | `system_event` | `candidate` | Conditional | Allowed if discovery creates a fresh reviewable proposal after cooldown. |
| `rejected` | safety block | `system_event` | `rejected` | Yes | Safety can keep it hidden; rejection remains. |
| `deferred` | restore/reopen | `user_action` | `candidate` | Yes | User can resume review. |
| `deferred` | confirm | `user_action` | `confirmed` | Yes | User can accept from deferred review. |
| `deferred` | partially confirm | `user_action` | `partially_confirmed` | Yes | User can accept a corrected/scope-limited version. |
| `deferred` | reject | `user_action` | `rejected` | Yes | User can reject after postponing. |
| `deferred` | defer window expires | `time_based` | `candidate` | Conditional | Allowed if product defines a defer window and safety still permits review. |
| `deferred` | new evidence arrives | `system_event` | `candidate` or `deferred` | Conditional | Reopen only if evidence materially changes the review prompt; otherwise keep deferred. |
| `deferred` | safety block | `system_event` | `deferred` | Yes | Safety blocks display; deferral remains. |
| `deferred` | archive obsolete duplicate | `system_event` | `archived` | Conditional | Allowed only for housekeeping with audit trail. |
| `archived` | restore/reopen | `user_action` | `candidate` | Conditional | Allowed only if product exposes history restore and safety permits. |
| `archived` | new evidence arrives | `system_event` | `archived` | Yes | Archived instances are not automatically revived. |
| `archived` | safety block | `system_event` | `archived` | Yes | Remains hidden. |

## 10. Prohibited Transitions

| From | Trigger | To | Source | Rationale |
|---|---|---|---|---|
| `candidate` | confidence increases | `confirmed` | `system_event` | Automatic promotion into confirmation is forbidden. |
| `candidate` | confidence increases | `high_trust` | `system_event` | `high_trust` is not a V1 MVP state. |
| `deferred` | defer window expires | `confirmed` | `time_based` | Time passing cannot imply user acceptance. |
| `rejected` | cooldown expires | `candidate` | `time_based` alone | Expiry permits engine re-evaluation; it does not itself re-surface the pattern. |
| `rejected` | similar evidence arrives inside 90 days | `candidate` | `system_event` | V1.2 cooldown forbids re-surfacing without substantially different evidence. |
| `rejected` | similar evidence arrives inside 90 days | `confirmed` | `system_event` | Rejection cannot be overridden by the system. |
| `confirmed` | evidence becomes stale | `candidate` | `system_event` | Staleness affects eligibility/display, not historical user review. |
| `confirmed` | safety block | `rejected` | `system_event` | Safety cannot impersonate a user rejection. |
| `partially_confirmed` | new evidence arrives | `confirmed` | `system_event` | New evidence cannot remove the need for explicit user confirmation. |
| Any | model/linter rewrite | `confirmed` | `system_event` | Generated text changes are not user acceptance. |
| Any | opening Structure or evidence view | `confirmed` | `user_action` inferred | Passive viewing is not an explicit review action. |
| Any | aggregate Understanding inclusion | Any status change | `system_event` | Inclusion consumes review state; it must not mutate it. |

## 11. V1.2 Rejection Cooldown Alignment

V1.2 requires that a rejected `(pattern_key, domain)` cannot be re-surfaced within 90 days unless evidence is substantially different. Substantial difference is defined as Jaccard `< 0.5` over the new `source_refs` compared with the rejected evidence `source_refs`.

Ownership split:

- **State machine:** owns `rejected` semantics, cooldown stickiness, allowed transitions out of `rejected`, and audit requirements.
- **Discovery engine:** enforces candidate suppression and determines whether new evidence is substantially different using Jaccard `< 0.5` over `source_refs`.
- **Both:** must prevent user-facing re-surfacing inside 90 days when evidence is not substantially different.

Cooldown rules:

1. On `candidate` → `rejected`, store the rejection timestamp, rejected `pattern_key`, rejected `domain`, rejected evidence chain/version, and rejected `source_refs` set.
2. For 90 days after rejection, the same `(pattern_key, domain)` remains `rejected` and hidden unless one of two things happens:
   - The user explicitly uses `restore/reopen`.
   - The engine finds new evidence whose `source_refs` Jaccard against the rejected `source_refs` is `< 0.5`.
3. If new evidence inside the 90-day window has Jaccard `>= 0.5`, the instance remains `rejected`; the engine may record a suppressed re-evaluation event for audit, but it must not re-surface the candidate.
4. If new evidence inside the 90-day window has Jaccard `< 0.5`, the engine may create a `rejected` → `candidate` transition with trigger `new evidence arrives` and source `system_event`.
5. When the 90-day cooldown expires, the transition is `rejected` → `rejected` with trigger `rejection cooldown expires` and source `time_based`; this marks the pair eligible for future engine re-evaluation but does not itself show anything to the user.
6. After cooldown expiry, the engine may transition `rejected` → `candidate` only if it produces a fresh eligible proposal with current evidence and safety checks.
7. `archived` must not be used to erase a rejected record or avoid the cooldown.

## 12. Future Evidence Interactions

### 12.1 Future Evidence for `rejected`

- Similar evidence during cooldown keeps the instance `rejected` and hidden.
- Substantially different evidence during cooldown may reopen as `candidate`, not `confirmed`.
- Post-cooldown evidence may create a new `candidate` review opportunity, but the prior rejection must remain auditable.
- Reopened rejected patterns should explain that the prior rejection is being revisited because the evidence changed materially or because the user reopened it.

### 12.2 Future Evidence for `deferred`

- New evidence may keep the instance `deferred` when it does not materially change the review prompt.
- New materially relevant evidence may reopen the instance as `candidate` so the user can review the updated evidence.
- A time-based defer window may return the instance to `candidate` only if the product defines the window and safety still permits surfacing.
- Deferral must not feed Pattern Structure or Understanding while active.

### 12.3 Future Evidence for `confirmed` and `partially_confirmed`

- New evidence may refresh evidence chains and Structure inputs.
- New evidence must preserve user corrections and accepted scope.
- Evidence that conflicts with the accepted scope should be presented as a review/update opportunity, not as an automatic status change.
- Stale evidence may block or label Structure display until refreshed, but does not erase review status.

## 13. API Eligibility Requirements

API surfaces may expose review statuses but must not redefine transitions.

Minimum API behavior:

- Pattern listing APIs may return `candidate`, `confirmed`, `partially_confirmed`, `rejected`, `deferred`, and `archived` if needed for review/history surfaces.
- Structure APIs must return `canShow: true` only for `confirmed` and eligible `partially_confirmed` instances.
- Structure APIs must return `canShow: false` for `candidate`, `rejected`, `deferred`, and `archived`.
- Structure APIs must return a clear reason when blocked, such as `unreviewed_candidate`, `rejected`, `rejection_cooldown_active`, `deferred`, `archived`, `safety_blocked`, `stale_evidence`, or `insufficient_evidence`.
- Understanding APIs may include only `confirmed` and eligible `partially_confirmed` instances.
- Review mutation APIs should accept explicit actions, not raw arbitrary status writes, unless used by internal audited admin tooling.

API contract docs may expose statuses and reasons, but transition authority remains here.

## 14. Frontend State Requirements

Frontend docs may define UI actions and components, but must align with this state machine.

Frontend behavior:

- `candidate`: show evidence-backed review card with confirm, partially confirm, reject, defer, and edit/correct actions.
- `confirmed`: show Pattern Structure entry point when API eligibility allows; otherwise show the API-provided blocked reason.
- `partially_confirmed`: show scoped Structure only for accepted/corrected content; make the correction context visible enough to avoid overstatement.
- `rejected`: hide from normal discovery, Structure, and Understanding surfaces; optional history surface may allow restore/reopen.
- `deferred`: hide Structure and show a saved-for-later or not-now state if the product supports it.
- `archived`: hide from active user surfaces unless a review/history management surface exposes it.
- Safety-blocked: never show blocked evidence or Structure content; show restraint as an intentional state rather than an app error.

Frontend must not infer confirmation from passive actions such as opening a drawer, reading evidence, scrolling, or not dismissing a card.

## 15. Auditability Requirements

Every transition must produce an audit record sufficient to answer: who or what changed the state, when, from what, to what, based on which evidence, and why.

Audit record requirements:

- `pattern_instance_id`.
- Previous status and next status.
- Trigger name and trigger source: `user_action`, `system_event`, or `time_based`.
- Actor: user ID for user actions, system component name/version for system events, scheduler/job name for time-based transitions.
- Timestamp.
- `(pattern_key, domain)`.
- Evidence chain ID/version used at the time of transition.
- `source_refs` set or hash for rejection cooldown calculations.
- User correction or note for `partially_confirmed` and `edit/correct` actions.
- Cooldown metadata for `rejected`: rejection timestamp, cooldown expiry timestamp, Jaccard score for attempted re-surfacing, and whether re-surfacing was suppressed or allowed.
- Safety/Lint block reason when display eligibility changes because of safety.
- Immutable history; later corrections append new records rather than rewriting prior review decisions.

## 16. Cross-Line Consistency Rules

- This document owns review state semantics and transitions.
- API Contract may expose statuses and eligibility reasons but must not redefine transitions.
- Safety/Lint may consume statuses as inputs but must not redefine status semantics.
- Backend Domain Model may include review status as a domain concept but must defer transition rules to this document.
- Frontend may define UI actions and display states but must align with transitions here.
- `partially_confirmed` handling is jointly owned by this document for transition semantics and Safety/Lint for which summary/version may be shown.

## 17. V1 MVP Non-Goals

V1 MVP must not include:

- Java implementation.
- Frontend implementation.
- Database schema design.
- Automatic system promotion to `confirmed`.
- A `high_trust` review state.
- Later longitudinal pattern-change layers.
- Map-style relationship views beyond simple evidence-backed lists.
- Structure for ambiguous, low-confidence, unreviewed, rejected, deferred, archived, or safety-blocked instances.
- Clinical, identity-labeling, repair-plan, ranking, origin-story, or explanation-of-self framing.
