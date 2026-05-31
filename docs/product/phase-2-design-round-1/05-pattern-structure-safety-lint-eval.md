# Pattern Structure MVP Safety and Lint Evaluation Spec

## 1. Purpose

This spec defines the safety, lint, and evidence-coverage checks required for the Pattern Structure MVP. It describes what must be checked before Pattern Structure outputs can be shown, hidden, downgraded, or sent for human review.

This is an evaluation and product-safety spec, not an implementation spec.

Primary source sections:

- `docs/product/phase-2-pattern-understanding-plan.md` §5 Trust Boundary, §6 Product Boundary, and §7 Phase 2 MVP dimensions.
- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md` §1 Hard Scope, §5 Module Contracts, §7 Deterministic vs LLM Responsibilities, §8 Safety Boundary, and §9 MVP Acceptance Criteria.
- `docs/product/pattern-structure-ux-audit.md` §8 UX Copy and Safety Guardrails.

## 2. Safety Goals

Pattern Structure should help the user inspect the observable shape of an accepted pattern without making the pattern feel more authoritative than the review state supports.

Safety goals:

1. Keep Pattern Structure inside the accepted-evidence boundary.
2. Ensure every displayed structure claim can be traced to accepted evidence.
3. Prevent unsafe framing that turns observed shape into explanation, diagnosis, advice, or identity labeling.
4. Preserve the product stance: mirror, not therapist.
5. Ensure partial user recognition is reflected cautiously, especially for `partially_confirmed` patterns.
6. Hide or downgrade outputs when representative evidence is missing, unsafe to display, or insufficiently covered.
7. Make safety-blocked and evidence-insufficient states explicit to API and frontend consumers without redefining their DTOs or components.

## 3. Non-Goals

This spec does not:

1. Define review-state transition semantics. Those are owned by the Review State Machine doc.
2. Define API DTO fields. API specs should reference this doc for safety effects only.
3. Define frontend components. Frontend specs should reference this doc for safety state requirements only.
4. Define fixture cases. Fixture specs should use this doc to create acceptance scenarios.
5. Define prompt templates, regular expressions, Java code, tests, or implementation classes.
6. Introduce later-stage change-over-time features or any connected cross-pattern map beyond flat neighbor-pattern safety requirements.
7. Treat system-only trust shortcuts as part of the V1 MVP display path. V1 MVP eligibility is limited to review states permitted by the Pattern Structure MVP and Review State Machine docs.

## 4. Safety Model

The safety model has three layers:

1. **Eligibility safety**: structure is considered only for review states allowed by the Review State Machine doc and the Pattern Structure MVP.
2. **Evidence safety**: every module-level and summary-level claim must be covered by accepted evidence references, with sensitive evidence handled according to display constraints.
3. **Language safety**: generated or authored text must avoid forbidden framing even when the cited evidence contains similar wording.

A result can fail any layer independently. For example, a module can have enough evidence but still be blocked because the summary turns temporal ordering into explanation.

## 5. Forbidden Language Categories

The following categories are forbidden in user-facing Pattern Structure outputs, module labels, summaries, connector labels, and LLM-assisted fields. Evidence excerpts may contain user-authored wording from these categories, but the system must not endorse, summarize, normalize, amplify, or restate them as a product claim.

| Category | What is forbidden | Required safe boundary |
|---|---|---|
| Diagnostic | Naming or implying a condition, disorder, syndrome, or clinical assessment. | Describe only observed evidence grouping and review state. |
| Therapeutic | Framing Pattern Structure as treatment, therapy, healing, or clinical support. | Present Structure as inspectable organization of accepted entries. |
| Personality typing | Assigning identity traits, fixed character types, or stable personality classifications. | Describe repeated evidence contexts without identity claims. |
| Attachment style | Assigning attachment categories or relationship-style labels. | Describe relationship roles and evidence coverage only. |
| Root cause / origin story | Explaining where a pattern came from or what produced it. | Describe observable distribution, roles, timing, and neighbor evidence only. |
| Trauma inference | Inferring traumatic history, trauma response, or trauma-based explanation. | Do not infer lived history beyond the cited entry content. |
| Symptom/pathology | Treating repeated text as symptoms, impairment, pathology, or clinical presentation. | Use product-neutral evidence language. |
| Treatment/advice | Telling the user what to do to fix, treat, manage, confront, or change themselves or others. | Offer review actions such as confirm, edit, reject, defer, or inspect evidence. |
| Severity scoring | Ranking the user's state, pattern, or risk by intensity, seriousness, clinical level, or severity. | Use evidence counts, coverage, and availability states only. |
| Causal explanation | Claiming one event, person, role, context, or prior event explains or produces another. | Temporal ordering may be shown only as ordering, not cause. |

## 6. Evidence Coverage Invariants

Pattern Structure outputs must satisfy these invariants before display:

1. **Accepted evidence only**: displayed structure may use evidence attached to eligible `confirmed` or `partially_confirmed` pattern instances only, as defined by the Review State Machine doc.
2. **No rejected or unreviewed aggregation**: rejected, archived, abstained, ordinary candidate, deferred, or unreviewed pattern evidence must not contribute to displayed structure.
3. **Module traceability**: every available module must include direct evidence references for each displayed aggregate, label, representative example, distinction, or ordering claim.
4. **Summary traceability**: every user-facing summary sentence that states where, who or what, when, or nearby-pattern relationship must be traceable to one or more module evidence references.
5. **Representative coverage**: top scene categories, relationship roles, temporal buckets, and neighbor distinctions must each have representative references unless the module is shown as aggregate-only because excerpts are hidden.
6. **Sensitive excerpt handling**: sensitive excerpts may contribute to aggregate counts only when allowed by source policy, but must be hidden from default display when they contain crisis, self-harm, sexual, medical, legal, or highly sensitive content.
7. **No orphan text**: LLM-assisted labels, connector labels, captions, or summaries must not introduce concepts that are absent from the referenced evidence.
8. **Deletion respect**: deleted source material must not appear in future structure responses or representative evidence.
9. **Source privacy**: source references must never expose another user's private data.
10. **Partial-state caution**: for `partially_confirmed` patterns, displayable summaries must use the user's edited summary or a neutral structure-state label; system-generated summaries must not override the user's partial framing.

## 7. Claims Requiring Direct Evidence References

The following claim types require direct evidence references:

1. A scene category appears in the accepted evidence.
2. A scene category is one of the top categories.
3. A relationship role, object, entity group, or self-reference is associated with the pattern.
4. A temporal placement appears before, during, after, or across repeated entries.
5. A recurrence statement refers to days, weeks, source threads, repeated entries, or repeated timing.
6. A neighbor pattern appears near the current pattern in the same source, thread, time window, or context tag.
7. A neighbor distinction states that the current evidence differs from neighbor evidence.
8. A displayed summary states breadth, concentration, sparseness, uncertainty, or mixed evidence.
9. A module status is `insufficient_data` because evidence is missing, sparse, hidden, or not allowed for display.
10. A module status is safety-hidden because all representative evidence is sensitive or blocked.

If direct references are missing, the claim must be removed, downgraded to a no-claim empty state, or safety-blocked depending on severity.

## 8. Claims Blocked Even With Evidence

Some claims are unsafe even when the source evidence appears to support them. The system must not make these claims as product output:

1. Diagnostic, clinical, or pathology claims about the user or another person.
2. Therapeutic interpretation or treatment guidance.
3. Personality type or attachment-style claims.
4. Origin, root-cause, hidden-motive, or trauma-inference claims.
5. Claims that a person, role, context, or event explains the pattern.
6. Claims that temporal ordering proves cause.
7. Claims that assign blame, intent, manipulation, character, or moral judgment to relationship objects.
8. Claims that score severity or seriousness of the user's state.
9. Claims that tell the user what action to take outside review actions.
10. Claims that make the reviewed pattern broader than the evidence coverage supports.

When user-authored evidence contains unsafe wording, the product may show the exact excerpt only if the evidence-display policy allows it, but generated text around it must remain neutral and must not validate the unsafe framing.

## 9. Check Table

| Check name | What it protects against | Input artifact | Severity | blocker_or_warning | Expected remediation |
|---|---|---|---|---|---|
| Eligibility Gate Check | Structure for ineligible review states | Pattern review state | Critical | Blocker | Return structure unavailable state; do not generate or display modules. |
| Accepted Evidence Boundary Check | Aggregation from rejected, archived, abstained, deferred, candidate, or unreviewed evidence | Evidence references and review state | Critical | Blocker | Remove ineligible evidence; recompute or hide result. |
| Evidence Reference Coverage Check | Unsupported module claims | Module output | Critical | Blocker | Attach direct evidence references or remove the claim. |
| Summary Evidence Coverage Check | User-facing summary claims without evidence | User-facing summary | Critical | Blocker | Rewrite summary to include only referenced module claims or hide summary. |
| Forbidden Language Check | Unsafe diagnostic, therapeutic, identity, origin, trauma, symptom, treatment, severity, or causal framing | All user-facing text and LLM-assisted fields | Critical | Blocker | Replace with neutral observable-shape wording or hide field. |
| Blocked Claim Check | Unsafe claims that remain unsafe even with evidence | All generated claims | Critical | Blocker | Remove claim; do not remediate by adding evidence. |
| Sensitive Excerpt Display Check | Default display of crisis, self-harm, sexual, medical, legal, or highly sensitive text | Representative evidence excerpts | High | Blocker | Count evidence only if allowed; hide excerpt text and show aggregate-only state. |
| All Representatives Hidden Check | Empty-looking modules with no safe excerpts | Module output | High | Warning | Show aggregate-only state or mark module hidden for safety. |
| Overbreadth Check | Claiming a pattern is global from narrow evidence | Scene, relationship, temporal, and summary outputs | High | Blocker | Narrow the claim to covered contexts or show insufficient coverage. |
| Certainty Inflation Check | Making a reviewed pattern feel more certain than review state supports | Header, summary, module captions | High | Blocker | Use review-state-aware copy and avoid certainty language. |
| Partial Confirmation Summary Check | Overriding the user's edited partial framing | Pattern summary/version metadata | High | Blocker | Show the user's edited summary or neutral partial-state label. |
| Relationship Blame Check | Assigning fault, motive, or character to people or roles | Relationship object output | Critical | Blocker | Convert to neutral role/object presence or hide field. |
| Temporal Causality Check | Treating order as explanation | Temporal structure output and summary | Critical | Blocker | Describe ordering only; remove cause language. |
| Neighbor Explanation Check | Treating co-occurrence as explanation or connected-map logic | Neighbor pattern output | Critical | Blocker | Present flat co-occurrence/distinction only, with evidence references. |
| LLM Field Auditability Check | Untraceable LLM-assisted labels or captions | LLM-assisted module fields | High | Blocker | Mark LLM-assisted fields and attach source evidence; otherwise remove. |
| Deleted Source Check | Displaying removed source material | Evidence references | Critical | Blocker | Remove deleted references and recompute affected modules. |
| Cross-User Source Privacy Check | Exposing another user's private source references | Evidence references and source metadata | Critical | Blocker | Suppress unsafe references and hide affected excerpts. |
| Sparse Evidence State Check | Presenting weak coverage as meaningful structure | Module evidence counts | Medium | Warning | Show `insufficient_data` or a sparse-evidence empty state. |
| User Review Action Availability Check | Locking unsafe or unsupported interpretation | Frontend safety state requirements | Medium | Warning | Ensure reject, edit, and defer actions remain available where applicable. |
| Fixture Acceptance Trace Check | Ambiguous acceptance scenarios for future fixtures | Spec-derived acceptance scenario | Medium | Warning | Add fixture expectation that states blocked, warning, or available behavior. |

## 10. Application by Pattern Structure Area

### 10.1 Scene Distribution

Scene Distribution checks must ensure:

1. Scene labels are coarse context labels, not explanations of why the pattern appears.
2. Top categories have accepted evidence references.
3. Unknown, mixed, sparse, or hidden-evidence buckets are preferred over unsupported precision.
4. A narrow cluster is not summarized as broad across the user's life.
5. Scene copy never implies that a context causes the pattern.

### 10.2 Relationship Objects

Relationship Object checks must ensure:

1. Roles and objects are described as present in evidence, not as responsible for the pattern.
2. Entity grouping avoids unnecessary private details.
3. Relationship labels do not become personality, attachment-style, blame, or motive claims.
4. Each displayed role/object has representative accepted evidence unless shown aggregate-only.
5. Relationship structure does not advise the user how to act toward a person or role.

### 10.3 Temporal Structure

Temporal Structure checks must ensure:

1. Ordering is described only as observed sequence.
2. Before/during/after labels have direct evidence-chain support.
3. Recurrence claims are based on accepted evidence across the stated time span.
4. Temporal labels do not imply cause, trigger, root, escalation, symptom progression, or severity.
5. Sparse temporal data produces an insufficient-data state rather than a narrative.

### 10.4 Neighbor Patterns

Neighbor Pattern checks must ensure:

1. Neighbor outputs are flat lists only, not connected map views.
2. Neighbor inclusion is based on accepted evidence overlap in source, thread, time window, or context tag.
3. Connector labels describe co-occurrence or distinction, not explanation.
4. Distinctions are evidence-backed and do not make diagnostic or identity claims.
5. Neighbor patterns respect the same review-state eligibility as the current pattern.

### 10.5 User-Facing Summary

User-facing summary checks must ensure:

1. The summary is composed only from module claims that passed evidence and language checks.
2. The summary does not introduce new claims absent from module outputs.
3. The summary reflects `partially_confirmed` cautiously by using the user's edited summary when present.
4. The summary includes no forbidden category language.
5. If any critical module used by the summary is blocked, the summary is removed or replaced by a neutral safety state.

## 11. Review Status Consumption

Safety checks consume review status from the Review State Machine doc. This spec does not define or modify review transitions.

Safety interpretation:

1. `confirmed`: eligible for Pattern Structure if evidence coverage and language checks pass.
2. `partially_confirmed`: eligible for Pattern Structure only with partial-state caution; the user's edited summary/version is preferred for display, and system summary must not broaden the user's partial confirmation.
3. `candidate`: not eligible for user-facing Pattern Structure.
4. `rejected`: not eligible and must not contribute evidence to aggregate structure.
5. `archived`: not eligible for V1 MVP Pattern Structure display unless a future spec explicitly defines a past-pattern surface.
6. Deferred or unreviewed states, if present in the Review State Machine doc, are not eligible for user-facing Pattern Structure.
7. Abstained or low-trust candidates are not eligible for V1 MVP Pattern Structure.

`partially_confirmed` handling is jointly owned: the Review State Machine doc owns transition semantics, and this spec owns which summary/version may be shown after safety checks. API and Domain docs must reference both.

## 12. API and Frontend State Requirements

This section describes expected effects only. It does not define DTOs or frontend components.

API effects:

1. Ineligible patterns should return a structure-unavailable state with no generated module payload.
2. Safety-blocked modules should be distinguishable from evidence-insufficient modules.
3. If a module is blocked but the pattern remains eligible, unaffected modules may be returned only if their summaries do not depend on the blocked module.
4. Safety-hidden representative excerpts should not be included in default display payloads.
5. Aggregate counts may remain available when policy allows counting but not excerpt display.
6. Responses should preserve enough reason information for the frontend to render restrained empty states and review actions.

Frontend state requirements:

1. Safety-blocked structure must appear as successful restraint, not as a loading failure.
2. Users should be able to inspect safe evidence references when available.
3. Users should be able to reject, edit, or defer where the review flow supports those actions.
4. `partially_confirmed` displays must not look identical to fully confirmed displays when the user's partial wording narrows the claim.
5. Safety-blocked modules must not be replaced with speculative fallback copy.
6. The UI must not use clinical visuals, causal diagrams, connected map views, medical iconography, or personality-style framing to compensate for blocked text.

## 13. Blocker vs Warning Semantics

A **blocker** prevents a field, module, summary, or whole Pattern Structure result from being displayed in its current form.

A **warning** allows display only when the unsafe or weak condition is explicitly represented through a restrained state, such as sparse evidence, aggregate-only display, or hidden excerpts. Warnings must not silently pass into confident copy.

Severity guidance:

- **Critical**: unsafe user-facing claim, privacy risk, ineligible state, or unsupported generated claim. Must block.
- **High**: evidence display or certainty risk that can mislead the user. Usually blocks affected field or module.
- **Medium**: incomplete but recoverable product-state issue. Warn and downgrade display.
- **Low**: editorial consistency issue with no safety impact. Not expected in MVP safety acceptance fixtures unless paired with a higher-severity condition.

## 14. Known Limitations

| Limitation | Failure mode | Severity | Mitigation strategy | Requires human review |
|---|---|---|---|---|
| Metaphor implying explanation | A neutral-looking metaphor can imply that one context produces the pattern without using explicit causal wording. | High | Prefer literal evidence-shape language; flag metaphor-heavy summaries for review when they appear in temporal or relationship claims. | Yes, when used in user-facing summary. |
| User-authored diagnosis-like wording | Evidence may include the user's own diagnosis-like or treatment-like language, and exact excerpts can look endorsed by proximity to system text. | High | Separate excerpt from system claim, avoid restating it, and hide excerpts when policy marks the content sensitive. | Yes, when excerpt display is ambiguous. |
| User note smoothing | LLM/system summary rewrites a user's partially_confirmed note into smoother prose and loses explicit narrow scope or exception. | Critical | Block paraphrased summaries; display the user's original edited summary verbatim for partially_confirmed where policy allows. | Yes, on partially_confirmed display paths. |
| Subtle blame in relationship labels | A role label or connector can imply fault or motive through tone even without explicit blame terms. | High | Restrict relationship labels to neutral roles/objects and require evidence-only captions. | Yes, for new or LLM-assisted labels. |
| Overgeneralization from sparse but valid evidence | A small number of accepted examples can pass reference checks while still supporting only a narrow claim. | Medium | Use sparse-evidence warnings, unknown/mixed buckets, and narrow summary language. | No, unless summary language broadens the claim. |
| Sensitive meaning across combined fields | Individually safe scene, relationship, and temporal fields can combine into a more sensitive implication. | High | Run summary-level checks after module checks and block combined interpretations that introduce unsafe framing. | Yes, when combined display creates a new implication. |

## 15. Acceptance Expectations

A fixture or product acceptance suite derived from this spec should verify that:

1. Ineligible review states produce no user-facing Pattern Structure modules.
2. Unsupported claims are removed, downgraded, or blocked.
3. Forbidden framing is blocked even when evidence contains similar wording.
4. Sensitive evidence can be counted only when policy allows it and is hidden from default excerpt display.
5. `partially_confirmed` display uses the user's edited summary/version when present and does not broaden it.
6. Scene, relationship, temporal, neighbor, and summary outputs each apply their specific safety checks.
7. API and frontend states distinguish safety-blocked, evidence-insufficient, and available outputs without redefining DTOs or components.
