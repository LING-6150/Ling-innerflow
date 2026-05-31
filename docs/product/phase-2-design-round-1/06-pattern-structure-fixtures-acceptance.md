# Pattern Structure MVP Fixture and Acceptance Scenarios

## 1. Purpose

This document defines sample fixture data and acceptance scenarios for Pattern Structure MVP. It is a Markdown/YAML-style design fixture spec for later implementation tests; it is not Java test code, frontend test code, or a JSON fixture file.

The fixtures are intentionally compact. They give implementers concrete examples for eligible, unavailable, safety-blocked, and boundary-failure states without becoming an authoritative API, domain, frontend, review, or safety contract.

## 2. Source Inputs and Contract Ownership

This document depends on the three Phase 2 input docs:

- `docs/product/phase-2-pattern-understanding-plan.md`
- `docs/superpowers/specs/2026-05-31-pattern-structure-mvp.md`
- `docs/product/pattern-structure-ux-audit.md`

It also depends on the five sibling Round 1 design docs. Contract ownership is:

| Concern | Authoritative sibling doc | How this fixture doc uses it |
|---|---|---|
| API fields, endpoint behavior, DTO names, enum literals, and empty/error states | `01-pattern-structure-api-contract.md` | Uses field names only as examples and does not redefine wire contracts. |
| Domain model, internal relationships, evidence chain concepts, and neighbor relationship constraints | `02-pattern-structure-domain-model.md` | Uses `PatternInstance`, `EvidenceChain`, `EvidenceItem`, `PatternStructure`, `StructureSection`, and `NeighborPattern` conceptually. |
| Frontend states, component placement, Pattern Detail behavior, and flat neighbor display | `03-frontend-ia-component-contracts.md` | Describes expected UI states without redefining component props or route implementation. |
| Review statuses, eligibility mapping, allowed transitions, and rejection cooldown behavior | `04-pattern-review-state-machine.md` | Uses review states as fixture inputs and defers all transition/cooldown rules to that doc. |
| Safety/lint checks, blocked language categories, module blocking, and display restraint | `05-pattern-structure-safety-lint-eval.md` | Marks expected blocked/downgraded outcomes without redefining lint rules. |

If any future implementation finds a conflict between this fixture doc and a sibling contract, the sibling contract wins.

## 3. Fixture Conventions

Fixtures use YAML-style blocks for readability only. They are not required to be stored as YAML by implementation.

Rules for all fixture blocks:

- IDs are stable fixture identifiers, not production IDs.
- Timestamps are ISO 8601 examples with timezone offsets.
- Evidence excerpts are deliberately neutral and non-clinical.
- Evidence examples avoid diagnosis, therapy, personality typing, attachment style, trauma, symptom, treatment, severity, root cause, and causal explanation as acceptable system output.
- `pattern_status`, module names, module statuses, empty-state codes, error codes, and evidence DTO field names are owned by the API Contract doc.
- Review state meaning and transitions are owned by the Review State Machine doc.
- Safety-blocked outcomes are owned by the Safety/Lint doc.
- Neighbor fixtures are flat list examples only; they must not be interpreted as a graph, network, topology, dependency, or causal edge.
- `trust_tier` may be `null` or `confirmed_by_user` where the API Contract allows it; `high_trust` must not be used for V1 MVP rendering.

## 4. Minimal Fixture Set

### 4.1 Shared Evidence Items

```yaml
evidence_items:
  - id: ev-standup-2026-05-01
    source_type: journal
    source_ref: journal:2026-05-01:morning
    occurred_at: "2026-05-01T09:10:00+08:00"
    excerpt_visibility: visible
    is_verbatim: true
    excerpt: "Before the standup, I rewrote my notes three times and kept the final update very short."
    interpretation: "Work meeting preparation and concise update."
    deep_link: app://journal/2026-05-01/morning

  - id: ev-standup-2026-05-08
    source_type: chat
    source_ref: chat:team:msg-8801
    occurred_at: "2026-05-08T09:24:00+08:00"
    excerpt_visibility: visible
    is_verbatim: true
    excerpt: "I had more context, but in the meeting I only shared the safe headline."
    interpretation: "Meeting update with omitted context."
    deep_link: app://chat/team/msg-8801

  - id: ev-retro-2026-05-15
    source_type: imported_note
    source_ref: note:retro:2026-05-15
    occurred_at: "2026-05-15T16:40:00+08:00"
    excerpt_visibility: visible
    is_verbatim: true
    excerpt: "During retro I waited until the end, then added one small process suggestion."
    interpretation: "Retrospective participation pattern."
    deep_link: app://notes/retro/2026-05-15

  - id: ev-family-2026-05-02
    source_type: journal
    source_ref: journal:2026-05-02:evening
    occurred_at: "2026-05-02T21:35:00+08:00"
    excerpt_visibility: visible
    is_verbatim: true
    excerpt: "At dinner I changed the topic to weekend logistics instead of explaining the whole disagreement."
    interpretation: "Family conversation topic shift."
    deep_link: app://journal/2026-05-02/evening

  - id: ev-checkin-2026-05-10
    source_type: checkin
    source_ref: checkin:2026-05-10:2045
    occurred_at: "2026-05-10T20:45:00+08:00"
    excerpt_visibility: visible
    is_verbatim: false
    excerpt: "Check-in noted low energy after a long conversation and choosing a shorter reply."
    interpretation: "Low-energy check-in after extended conversation."
    deep_link: app://checkins/2026-05-10/2045

  - id: ev-sparse-2026-05-12
    source_type: journal
    source_ref: journal:2026-05-12:short
    occurred_at: "2026-05-12T22:00:00+08:00"
    excerpt_visibility: visible
    is_verbatim: true
    excerpt: "I kept the update brief today."
    interpretation: "Single brief update mention."
    deep_link: app://journal/2026-05-12/short

  - id: ev-crisis-2026-05-20
    source_type: journal
    source_ref: journal:2026-05-20:private
    occurred_at: "2026-05-20T23:20:00+08:00"
    excerpt_visibility: hidden_for_safety
    is_verbatim: true
    excerpt: null
    interpretation: null
    deep_link: null

  - id: ev-neighbor-2026-05-08
    source_type: chat
    source_ref: chat:team:msg-8803
    occurred_at: "2026-05-08T09:31:00+08:00"
    excerpt_visibility: visible
    is_verbatim: true
    excerpt: "After the meeting I wrote a separate follow-up with the extra details."
    interpretation: "Post-meeting follow-up with more detail."
    deep_link: app://chat/team/msg-8803
```

### 4.2 Shared Evidence Chains

```yaml
evidence_chains:
  - id: chain-work-brief-updates-v1
    pattern_instance_id: pat-confirmed-brief-updates
    generator_version: pattern-structure-mvp-fixture-v1
    generated_at: "2026-05-16T10:00:00+08:00"
    evidence_item_ids:
      - ev-standup-2026-05-01
      - ev-standup-2026-05-08
      - ev-retro-2026-05-15

  - id: chain-family-topic-shift-v1
    pattern_instance_id: pat-partial-topic-shift
    generator_version: pattern-structure-mvp-fixture-v1
    generated_at: "2026-05-11T10:00:00+08:00"
    evidence_item_ids:
      - ev-family-2026-05-02
      - ev-checkin-2026-05-10

  - id: chain-candidate-v1
    pattern_instance_id: pat-candidate-brief-updates
    generator_version: pattern-structure-mvp-fixture-v1
    generated_at: "2026-05-13T10:00:00+08:00"
    evidence_item_ids:
      - ev-sparse-2026-05-12

  - id: chain-safety-blocked-v1
    pattern_instance_id: pat-safety-blocked
    generator_version: pattern-structure-mvp-fixture-v1
    generated_at: "2026-05-21T10:00:00+08:00"
    evidence_item_ids:
      - ev-crisis-2026-05-20

  - id: chain-neighbor-ambiguity-v1
    pattern_instance_id: pat-confirmed-brief-updates
    generator_version: pattern-structure-mvp-fixture-v1
    generated_at: "2026-05-16T10:05:00+08:00"
    evidence_item_ids:
      - ev-standup-2026-05-08
      - ev-neighbor-2026-05-08
```

### 4.3 Pattern Instance Fixtures

```yaml
pattern_instances:
  - id: pat-confirmed-brief-updates
    status: confirmed
    summary_fixture: "In some work meetings, updates are kept brief even when more context exists."
    active_evidence_chain_id: chain-work-brief-updates-v1
    expected_structure_availability: available

  - id: pat-partial-topic-shift
    status: partially_confirmed
    user_edited_summary_fixture: "This is only sometimes true in family conversations, especially when I want to keep logistics moving."
    active_evidence_chain_id: chain-family-topic-shift-v1
    expected_structure_availability: available_with_partial_scope

  - id: pat-candidate-brief-updates
    status: candidate
    summary_fixture: "Possibly brief updates."
    active_evidence_chain_id: chain-candidate-v1
    expected_structure_availability: unavailable_unreviewed

  - id: pat-rejected-topic-shift
    status: rejected
    summary_fixture: "Topic changes in personal conversations."
    active_evidence_chain_id: chain-family-topic-shift-v1
    rejected_at: "2026-05-12T12:00:00+08:00"
    expected_structure_availability: unavailable_rejected_or_cooldown

  - id: pat-deferred-topic-shift
    status: deferred
    summary_fixture: "Topic changes in personal conversations."
    active_evidence_chain_id: chain-family-topic-shift-v1
    deferred_until: "2026-06-01T09:00:00+08:00"
    expected_structure_availability: unavailable_deferred

  - id: pat-insufficient-evidence
    status: confirmed
    summary_fixture: "Brief update may appear in one note."
    active_evidence_chain_id: chain-candidate-v1
    expected_structure_availability: unavailable_insufficient_evidence

  - id: pat-safety-blocked
    status: confirmed
    summary_fixture: "Private note contains safety-sensitive material."
    active_evidence_chain_id: chain-safety-blocked-v1
    expected_structure_availability: unavailable_safety_blocked

  - id: pat-neighbor-follow-up
    status: confirmed
    summary_fixture: "Additional details are sometimes sent after meetings."
    active_evidence_chain_id: chain-neighbor-ambiguity-v1
    expected_structure_availability: available_as_neighbor_candidate
```

## 5. Expected High-Level Pattern Structure Outputs

These examples describe expected shape, not exact DTO payloads. Implementation must map to the API Contract doc when producing responses.

### 5.1 Confirmed Pattern: Available Structure

```yaml
fixture_id: confirmed_pattern_available_structure
pattern_instance_id: pat-confirmed-brief-updates
expected_eligibility:
  can_show_structure: true
  pattern_status: confirmed
  trust_tier: confirmed_by_user
  reason_code: confirmed_pattern
expected_modules:
  scene_distribution:
    status: available
    high_level_output:
      - label: work meetings
        evidence_item_ids:
          - ev-standup-2026-05-01
          - ev-standup-2026-05-08
          - ev-retro-2026-05-15
      - label: unknown_or_mixed
        evidence_item_ids: []
    acceptable_summary: "The current evidence is concentrated in work meeting contexts."
  relationship_objects:
    status: available
    high_level_output:
      - label: meeting group
        evidence_item_ids:
          - ev-standup-2026-05-01
          - ev-retro-2026-05-15
      - label: update content
        evidence_item_ids:
          - ev-standup-2026-05-08
    acceptable_summary: "The examples involve meeting updates and shared work discussions."
  temporal_structure:
    status: available
    high_level_output:
      - label: before or during meeting
        evidence_item_ids:
          - ev-standup-2026-05-01
          - ev-standup-2026-05-08
      - label: late in discussion
        evidence_item_ids:
          - ev-retro-2026-05-15
    acceptable_summary: "Examples appear before, during, or late in meeting conversations."
  neighbor_patterns:
    status: available
    flat_neighbors_only:
      - pattern_instance_id: pat-neighbor-follow-up
        relationship_label: nearby_in_same_meeting_window
        representative_pairs:
          - current_evidence_item_id: ev-standup-2026-05-08
            neighbor_evidence_item_id: ev-neighbor-2026-05-08
    acceptable_summary: "One reviewed nearby pattern appears in the same meeting window."
```

### 5.2 Partially Confirmed Pattern: Narrowed Structure

```yaml
fixture_id: partially_confirmed_available_structure
pattern_instance_id: pat-partial-topic-shift
expected_eligibility:
  can_show_structure: true
  pattern_status: partially_confirmed
  trust_tier: confirmed_by_user
  reason_code: partially_confirmed_pattern
expected_display_constraints:
  - Use the user's edited or narrowed summary when a summary is displayed.
  - Do not broaden "only sometimes" into a general personal pattern.
  - Keep scene and relationship labels scoped to the evidence chain.
expected_modules:
  scene_distribution:
    status: available
    high_level_output:
      - label: family conversation
        evidence_item_ids:
          - ev-family-2026-05-02
      - label: personal check-in
        evidence_item_ids:
          - ev-checkin-2026-05-10
  relationship_objects:
    status: available
    high_level_output:
      - label: conversation topic
        evidence_item_ids:
          - ev-family-2026-05-02
      - label: reply length
        evidence_item_ids:
          - ev-checkin-2026-05-10
  temporal_structure:
    status: insufficient_data
    empty_state_expectation: "Show sparse or insufficient evidence rather than inventing a sequence."
  neighbor_patterns:
    status: insufficient_data
    empty_state_expectation: "No confirmed nearby patterns found in the current evidence window."
```

### 5.3 Neighbor Pattern Ambiguity: Flat Co-Occurrence Only

```yaml
fixture_id: neighbor_pattern_ambiguity
pattern_instance_id: pat-confirmed-brief-updates
neighbor_pattern_id: pat-neighbor-follow-up
expected_behavior:
  - Include the neighbor only as a flat list item if review state and evidence rules allow it.
  - Show paired evidence references when representative examples are shown.
  - Do not describe the neighbor as a cause, result, dependency, sequence explanation, cluster, graph edge, or hidden connection.
  - Do not rank the neighbor as more important than the current pattern.
acceptable_neighbor_note: "Also appears near a reviewed follow-up pattern in one meeting window."
blocked_neighbor_notes:
  - "Brief updates cause later detailed follow-ups."
  - "This node connects to a follow-up behavior cluster."
  - "The root pattern is brief updates, which produces follow-up messages."
```

## 6. Expected Unavailable and Empty States

These fixtures should produce restrained unavailable states, not generated structure modules.

```yaml
unavailable_fixtures:
  - fixture_id: unreviewed_candidate_no_structure
    pattern_instance_id: pat-candidate-brief-updates
    status: candidate
    expected_eligibility:
      can_show_structure: false
      pattern_status: candidate
      reason_code: awaiting_user_review
    expected_modules: []
    expected_frontend_state: unreviewed
    expected_copy_intent: "Ask for review or show evidence, but do not show Structure."

  - fixture_id: rejected_pattern_no_structure
    pattern_instance_id: pat-rejected-topic-shift
    status: rejected
    expected_eligibility:
      can_show_structure: false
      pattern_status: rejected
      reason_code: rejection_cooldown_active
    expected_modules: []
    expected_frontend_state: rejected
    cooldown_rule_owner: 04-pattern-review-state-machine.md

  - fixture_id: deferred_pattern_no_structure
    pattern_instance_id: pat-deferred-topic-shift
    status: deferred
    expected_eligibility:
      can_show_structure: false
      pattern_status: deferred
      reason_code: user_deferred_review
    expected_modules: []
    expected_frontend_state: deferred

  - fixture_id: insufficient_evidence_no_structure
    pattern_instance_id: pat-insufficient-evidence
    status: confirmed
    expected_eligibility:
      can_show_structure: false
      pattern_status: confirmed
      reason_code: too_few_evidence_items
    expected_modules: []
    expected_frontend_state: insufficient_evidence
    expected_copy_intent: "Explain that there is not enough supporting material."

  - fixture_id: safety_blocked_crisis_evidence
    pattern_instance_id: pat-safety-blocked
    status: confirmed
    expected_eligibility:
      can_show_structure: false
      pattern_status: confirmed
      reason_code: safety_blocked_crisis
    expected_modules: []
    expected_frontend_state: crisis_or_safety_blocked
    expected_copy_intent: "Show approved SafetyNotice behavior without interpreting the user's condition or intent."
    hidden_evidence_item_ids:
      - ev-crisis-2026-05-20
```

Empty-state expectations:

- Unreviewed candidates may show card/evidence review affordances if other contracts allow them, but no Pattern Structure modules.
- Rejected patterns must not re-surface within cooldown unless the Review State Machine doc explicitly allows the path.
- Deferred patterns should show a low-pressure resume affordance only where the Frontend IA and Review State Machine docs allow it.
- Insufficient evidence should not become a guessed scene, relationship, temporal, or neighbor module.
- Safety-blocked evidence should not be exposed through excerpts, generated summaries, lint details, or fallback explanations.
- Empty neighbor results must remain an empty flat list state, not a map, graph, or topology placeholder.

## 7. Positive Acceptance Scenarios

### Scenario 1: Confirmed Pattern Shows Evidence-Backed Structure

Given `pat-confirmed-brief-updates` is `confirmed`  
And its active evidence chain is `chain-work-brief-updates-v1`  
And the evidence items are visible and pass safety checks  
When the Structure endpoint is requested for all modules  
Then eligibility indicates structure can be shown  
And scene, relationship, temporal, and neighbor modules are returned only when backed by evidence references  
And each visible aggregate cites one or more evidence item IDs  
And user-facing copy describes observable structure, not explanation, diagnosis, therapy, or root cause.

### Scenario 2: Partially Confirmed Pattern Preserves User Scope

Given `pat-partial-topic-shift` is `partially_confirmed`  
And the user edited the summary to include "only sometimes"  
When Pattern Structure is rendered  
Then the display preserves the narrowed user scope  
And no section broadens the pattern into a general trait or universal behavior  
And modules without enough evidence show insufficient or empty states instead of invented content.

### Scenario 3: Safe Evidence References Are Inspectable

Given a visible module cites `ev-standup-2026-05-08`  
When the user opens the evidence reference list or drawer  
Then the evidence endpoint may return the allowed source metadata and excerpt according to the API Contract  
And the frontend displays only fields allowed by the API and Safety/Lint contracts  
And hidden evidence remains hidden.

### Scenario 4: Neighbor Ambiguity Is Presented as a Flat List

Given `pat-confirmed-brief-updates` and `pat-neighbor-follow-up` have nearby evidence in the same meeting window  
And both patterns are in review states allowed by the Review State Machine doc  
When the neighbor module is rendered  
Then the neighbor appears, if at all, as one flat list item  
And representative pairs cite current and neighbor evidence item IDs  
And the copy describes nearby occurrence or distinction only  
And no graph, network, topology, rank, causal edge, or dependency is rendered.

### Scenario 5: Safety-Blocked Evidence Produces Restraint

Given `pat-safety-blocked` is otherwise review-eligible  
And its active evidence chain contains `ev-crisis-2026-05-20` with hidden safety-sensitive evidence  
When Pattern Structure is requested  
Then the response and UI state distinguish safety-blocked from insufficient evidence  
And no raw blocked excerpt, generated substitute, diagnostic framing, or therapeutic advice is shown  
And any available next action follows the Safety/Lint and Frontend IA docs.

## 8. Negative Acceptance Scenarios

### Scenario 6: No Structure for Unreviewed Candidate

Given `pat-candidate-brief-updates` is `candidate`  
When Structure is requested  
Then no scene, relationship, temporal, or neighbor module is rendered  
And the frontend does not treat `candidate` as eligible because it has an evidence chain  
And any prompt asks for review rather than presenting structure.

### Scenario 7: No Graph or Network Neighbor Output

Given the neighbor ambiguity fixture has two reviewed patterns with nearby evidence  
When the neighbor module is rendered  
Then the output must not include graph nodes, edges, adjacency lists, clusters, topology, network canvas data, or connected-map language  
And the UI must not show a graph-like preview or Understanding-style map.

### Scenario 8: No Causal or Root-Cause Explanation

Given any eligible fixture has temporal ordering or nearby neighbor evidence  
When summaries, labels, or comparison notes are generated  
Then they must not claim that one event, role, scene, or pattern causes, explains, produces, triggers, reveals, or is the root of another  
And temporal structure must remain ordering or recurrence only.

### Scenario 9: No Diagnostic, Therapeutic, or Typing Language

Given any fixture includes user-authored or generated text  
When Pattern Structure copy is displayed  
Then system output must not include diagnosis, therapy, personality typing, attachment style, trauma framing, symptom language, treatment guidance, severity labels, or clinical certainty  
And unsafe wording in evidence must not be laundered into system summaries.

### Scenario 10: No `high_trust` Inference in V1 MVP

Given a pattern has multiple visible evidence items and a `confirmed` review state  
When eligibility is evaluated for V1 MVP  
Then implementers must not infer or display `high_trust`  
And `high_trust` must not become an alternate eligibility path  
And any future high-trust behavior remains outside this MVP fixture set unless the API Contract is explicitly extended.

### Scenario 11: No Re-Surfacing Rejected Pattern Within Cooldown

Given `pat-rejected-topic-shift` is `rejected`  
And its rejection cooldown is still active under Review State Machine rules  
When pattern cards, detail views, or Structure availability are computed  
Then the rejected pattern must not re-surface as Structure content  
And it must not appear as a neighbor pattern unless the Review State Machine doc explicitly allows that future path  
And no implementation test should override cooldown behavior from this fixture doc.

### Scenario 12: No Fallback Content for Insufficient Evidence

Given `pat-insufficient-evidence` has only `ev-sparse-2026-05-12`  
When Structure is requested  
Then modules are unavailable or insufficient rather than guessed  
And implementation must not fill missing scene, relationship, temporal, or neighbor data with generic language.

## 9. Future Implementation Test Consumption

Future tests should consume this document as a product fixture oracle, not as executable code.

Recommended consumption pattern:

1. Build backend test fixtures using the IDs and evidence-chain relationships in Sections 4-6.
2. Map fixture fields into the internal domain model owned by `02-pattern-structure-domain-model.md`.
3. Assert API responses against field names, enum literals, empty states, and error behavior owned by `01-pattern-structure-api-contract.md`.
4. Assert review eligibility and cooldown behavior against `04-pattern-review-state-machine.md`.
5. Assert frontend state rendering against `03-frontend-ia-component-contracts.md`, using this doc only to choose scenarios.
6. Assert safety blocking, forbidden language, evidence visibility, and downgrade behavior against `05-pattern-structure-safety-lint-eval.md`.
7. Keep neighbor assertions limited to flat list output with representative evidence pairs.
8. Add implementation-specific fixtures in code only after confirming they do not redefine sibling contracts.

Tests derived from this doc should explicitly fail if they detect:

- structure modules for unreviewed candidates, rejected cooldown patterns, deferred unavailable patterns, insufficient evidence, or safety-blocked evidence;
- graph, network, topology, connected-map, or Understanding-tab output in MVP;
- causal, root-cause, diagnostic, therapeutic, typing, trauma, symptom, treatment, severity, or personality-style copy;
- `high_trust` eligibility or rendering in V1 MVP;
- neighbor output that omits evidence-pair references when representative examples are shown;
- partial confirmation copy that broadens the user's edited scope.

## 10. Out of Scope

This fixture doc does not define or include:

- Java tests;
- frontend tests;
- generated JSON files;
- backend implementation code;
- frontend implementation code;
- database migrations;
- Pattern Evolution;
- Understanding tab MVP behavior;
- graph, network, topology, cluster, or map views;
- diagnosis, therapy, personality typing, attachment style, root cause, trauma, symptom, treatment, severity, or causal explanation as acceptable output.
