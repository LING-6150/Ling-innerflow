# InnerFlow Phase 2 — From Pattern Discovery to Pattern Understanding

> Status: product direction draft  
> Scope: docs-only planning  
> Last updated: 2026-05-31

---

## 1. Product Thesis

Pattern Discovery is the entry point to InnerFlow, not the destination.

Discovery answers the first mirror-product question:

> "Is there a recurring pattern in my own text?"

That question is necessary, but it is not sufficient. A discovered pattern is
only useful if the user can inspect it, recognize it, and decide whether it is
real in their lived data. Without that next layer, InnerFlow risks becoming a
labeling system: it names patterns, assigns confidence, and stops before the
user can understand what the label actually refers to.

Phase 2 moves InnerFlow from pattern naming toward pattern understanding.

The product goal is not to explain the user's psychology. The goal is to help
the user see the observable shape of a confirmed pattern in their own corpus:
where it appears, who it involves, how it unfolds over time, and which adjacent
patterns it may be confused with.

---

## 2. Why Discovery Is Not the Endpoint

A discovered pattern is an index, not an insight by itself.

Pattern Discovery can surface a candidate such as `conflict_aversion` or
`worth_through_achievement`, but the product still needs to answer follow-up
questions that make the pattern inspectable:

- Does this pattern appear mostly in work, family, romantic, or self-reflection
  contexts?
- Is it tied to specific relationship roles or recurring interaction partners?
- Does it show up before decisions, after conflict, during planning, or in
  repeated post-event reflection?
- Is the evidence specific to this pattern, or does it sit near confusable
  neighboring patterns?

Those questions are not secondary UI details. They determine whether the user
can evaluate the mirror. A label without structure asks the user to trust the
system. A structured view lets the user inspect the system's claim.

This distinction matters because V1 validation showed the dangerous failure
mode clearly: the system can produce plausible labels on data where it should
abstain. Phase 2 must therefore avoid building richer explanations on top of
untrusted candidates. Pattern Understanding is valuable only after the system
has earned enough trust to show structure rather than amplify noise.

---

## 3. Long-Term Product Value

Phase 2's long-term value is to make InnerFlow a navigable personal pattern
map, not just a list of detected pattern cards.

The durable product value is:

1. **Inspectability** — users can see what a confirmed pattern looks like in
   their own evidence, instead of accepting a label at face value.
2. **Specificity** — users can distinguish one pattern from nearby alternatives
   by seeing the contexts, actors, timing, and neighboring patterns around it.
3. **Continuity** — users can revisit the same pattern over time and understand
   how its observable footprint changes, without turning the product into a
   causal or therapeutic interpretation engine.
4. **Trust calibration** — the product reserves deeper structure for patterns
   with enough evidence and confirmation, reinforcing that InnerFlow is a
   mirror rather than a diagnosis machine.

In the long run, the user should be able to move from:

```text
"InnerFlow found people_pleasing."
```

to:

```text
"This people_pleasing pattern appears mostly in work and family contexts,
often around requests from authority figures, tends to emerge before explicit
boundary-setting moments, and sits near boundary_difficulty and
over_responsibility rather than being a generic social-anxiety label."
```

That is Pattern Understanding: not more certainty, not deeper interpretation,
but a clearer observable map.

---

## 4. Pattern Structure as an Independent Product Layer

Pattern Structure is not merely a richer Pattern Card detail view.

A Pattern Card can summarize a detected pattern: name, confidence, short
description, and supporting excerpts. Pattern Structure is a separate product
layer that organizes a confirmed pattern across dimensions that a single card
cannot represent well.

Pattern Structure should behave more like a pattern profile or pattern map:

- It aggregates evidence across multiple instances, not just one surfaced card.
- It describes the pattern's observable distribution and shape, not only its
  definition.
- It compares the pattern with neighbors to support user inspection and product
  abstention discipline.
- It can evolve independently from card UI, discovery ranking, or notification
  surfaces.

This separation prevents Phase 2 from becoming "add more fields to the card."
The card remains the entry point. Structure is the deeper layer the user opens
when a pattern is trusted enough to inspect.

---

## 5. Trust Boundary: Confirmed / High-Trust Patterns Only

Pattern Structure must only be generated for confirmed or high-trust patterns.

This is a hard product boundary.

The current project history makes the reason explicit: V1 could surface
confident false positives on full-decoy human prose, and the R1-R3 abstain
roadmap showed that post-hoc prompt gates can swing between being too loose and
too strict. Until a candidate has passed stronger trust criteria, structure is
not a feature; it is a noise amplifier.

Eligible inputs for Pattern Structure should be limited to patterns that meet
one of these conditions:

- The user has explicitly confirmed the pattern.
- The system has high-trust evidence under the current validated discovery and
  abstention pipeline.
- The pattern is part of an eval/demo flow where the trust status is clearly
  labeled and not presented as a user-facing claim.

If a pattern is low-confidence, ambiguous, or near the abstain boundary, the
product should not generate a structure map. It should either ask for more data,
show a lightweight candidate card, or abstain.

---

## 6. Product Boundary: Shape, Not Origin

Phase 2 does not explain psychological origins.

InnerFlow should not answer:

- "Why am I like this?"
- "What childhood cause produced this?"
- "What does this say about my attachment style, personality, or diagnosis?"
- "What should I do therapeutically to fix it?"

Phase 2 should answer:

- "What does this pattern look like in my data?"
- "Where does it appear most often?"
- "Who or what is usually involved?"
- "When does it tend to show up in the sequence of events?"
- "Which nearby patterns does it resemble, and how is this evidence different?"

The product stance remains: mirror, not therapist.

Pattern Understanding helps the user see the pattern's observable shape. It
does not infer root causes, diagnose the user, or claim to know why the user
behaves a certain way.

---

## 7. Phase 2 MVP: Four Structure Dimensions

The Phase 2 MVP should ship a focused Pattern Structure layer with four
dimensions. Each dimension should be evidence-backed, inspectable, and limited
to confirmed/high-trust patterns.

### 7.1 Scenario Distribution

Scenario Distribution answers:

> "In what kinds of situations does this pattern appear?"

MVP output:

- A distribution over coarse scenario categories, such as work, family,
  romantic relationship, friendship, self-reflection, health, study, planning,
  or conflict.
- Representative evidence excerpts for the top categories.
- A fallback bucket for unknown or mixed scenarios.

Product value:

- Helps users see whether a pattern is broad or context-specific.
- Prevents overgeneralizing a label from a narrow cluster of evidence.
- Supports future filtering by life domain without implying psychological
  cause.

### 7.2 Relationship Objects

Relationship Objects answers:

> "Who or what is usually involved when this pattern appears?"

MVP output:

- Recurring relationship roles, such as manager, parent, partner, friend,
  colleague, client, authority figure, peer, or self.
- Optional entity grouping when the data safely supports it, without exposing
  unnecessary private details.
- Evidence excerpts showing why each role/object is associated with the
  pattern.

Product value:

- Helps distinguish a global self-pattern from a role-specific interaction
  pattern.
- Makes the pattern more concrete without assigning blame or motive.
- Supports the mirror stance by showing observable relational context only.

### 7.3 Time Structure

Time Structure answers:

> "When does this pattern tend to appear in the sequence of events?"

MVP output:

- Simple temporal placement such as before conflict, during decision-making,
  after feedback, after social interaction, before asking for help, during
  planning, or in repeated post-event reflection.
- Recurrence over days or weeks when available.
- Evidence chains that show ordering without turning ordering into causation.

Product value:

- Helps users recognize the pattern as a sequence, not just isolated excerpts.
- Supports inspection of repeated timing cues.
- Avoids causal claims by describing temporal order only.

### 7.4 Neighbor Patterns

Neighbor Patterns answers:

> "Which nearby patterns could this evidence be confused with?"

MVP output:

- A small set of neighboring patterns from a predefined or computed confusable
  matrix.
- For each neighbor, a short evidence-backed distinction between the confirmed
  pattern and the neighbor.
- A specificity signal showing why this pattern remains the better match for
  the current evidence.

Product value:

- Directly extends the V2.1 contrastive retrieval direction into product UX.
- Makes pattern specificity visible rather than hiding it inside confidence.
- Helps the user understand that a pattern label is chosen against alternatives,
  not in isolation.

---

## 8. Later Layer: Pattern Evolution

Pattern Evolution is a future layer, not part of the Phase 2 MVP.

Evolution answers:

> "How does the observable footprint of this confirmed pattern change over
> time?"

Possible future capabilities:

- Compare scenario distribution across time windows.
- Show relationship-object shifts, such as a pattern moving from work contexts
  to family contexts or becoming narrower over time.
- Track whether the pattern appears earlier or later in event sequences.
- Show neighbor-pattern drift, where evidence becomes more specific to one
  pattern or more ambiguous between several patterns.

Evolution should wait because it depends on stable structure extraction,
sufficient longitudinal data, and stronger trust calibration. Shipping it in
the MVP would invite over-interpretation and create pressure to narrate personal
change before the product can safely support that claim.

When Pattern Evolution is eventually built, it must preserve the same product
boundary: describe observable change, not psychological healing, regression, or
root cause.

---

## 9. Roadmap

### Phase 2 MVP

Build Pattern Structure as a separate product layer for confirmed/high-trust
patterns only.

MVP scope:

- Create a Pattern Structure artifact separate from Pattern Card data.
- Generate scenario distribution with representative evidence.
- Generate relationship-object summaries with evidence-backed roles/objects.
- Generate time-structure summaries using ordering and recurrence, not
  causation.
- Generate neighbor-pattern comparisons using the V2.1 contrastive retrieval
  direction as the conceptual basis.
- Gate structure generation behind confirmation or high-trust eligibility.
- Make unsupported dimensions explicitly empty or unknown rather than guessed.

Success criteria:

- A user can inspect a confirmed pattern and understand its observable shape.
- The structure layer does not appear for low-trust or ambiguous candidates.
- Every structural claim can be traced back to evidence excerpts or aggregate
  counts.
- The UX reinforces "this is what the pattern looks like" rather than "this is
  why you are this way."

### Later

Add layers only after the MVP structure extraction is stable.

Later scope:

- Pattern Evolution across time windows.
- Richer visual maps for scenarios, relationship objects, and temporal
  sequences.
- More adaptive neighbor-pattern modeling beyond a manually maintained
  confusable matrix.
- User feedback loops that let confirmed structures be corrected without
  treating corrections as therapeutic judgments.
- Cross-pattern portfolio views for multiple confirmed patterns, while keeping
  each pattern's trust boundary explicit.

### Non-Goals

Phase 2 must not include:

- Psychological origin stories or childhood-cause explanations.
- Diagnostic claims, personality typing, attachment typing, or pathology labels.
- Advice that tells the user what to do to fix a pattern.
- Structure maps for low-confidence, unconfirmed, or abstain-boundary patterns.
- Treating Pattern Structure as extra fields inside Pattern Card detail UI.
- Using richer structure to rescue weak discovery quality.
- Moving Pattern Evolution into the MVP.

---

## 10. Guiding Principle

Phase 2 should make confirmed patterns more inspectable, not more authoritative.

The product should earn trust by showing less when evidence is weak and showing
clearer structure only when evidence is strong. Discovery says, "this may be a
pattern." Understanding says, "for a trusted pattern, here is what it looks like
in your data."

Phase 2 depends on V1 discovery surfacing reviewable candidates regardless of
the V2 abstain research outcome. If discovery itself becomes unusable, Pattern
Structure cannot start.
