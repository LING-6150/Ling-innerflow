# PATTERN_DISCOVERY_V1.md

> InnerFlow Pivot — From "AI Companion / Clinical Assistant" to "A Mirror that Helps Users See Their Own Patterns."
>
> Status: **V1 Design (frozen for implementation planning)**
> Date: 2026-05-29
> Scope: This is the V1 spec for a portfolio-grade AI Engineering project, not a startup PRD. Anything that smells like therapy, diagnosis, or AI companionship is explicitly out.

---

## 1. Product Vision

A personal mirror that helps users **see the patterns they keep repeating** — not because an AI told them what's wrong, but because the system showed them their own evidence and asked, "is this you?"

The moment we're optimizing for is not "I feel better after talking to the AI."
It is: **"Oh. I've been doing this for years."**

## 2. Product Mission

Build a Pattern Discovery system that:

1. Reads a user's accumulated conversations, journal entries, and check-ins.
2. Identifies recurring **psychological patterns** (`Pattern Taxonomy`), grounded in concrete, citeable evidence from the user's own history (`Evidence Chain`).
3. Presents those patterns to the user inside the **life domains** they live in (family, intimate, work, social, self, body), not as clinical labels.
4. Treats the user as the **final authority** — every pattern is a hypothesis the user confirms, partially accepts, rejects, or refines.
5. Feeds confirmed patterns back into long-term memory (`User Wiki`) so future reflections get sharper, not flatter.

## 3. Core User Problem

People can describe their feelings in the moment.
They cannot easily see the **shape of their life across months**.

Things they miss:
- "Every time my mother calls, I cancel plans the next day."
- "I only feel worthy when I outperform someone."
- "I apologize before I disagree, every single time."

These are not single events. They are **patterns**.
They are invisible from inside, and they are exactly what users want to understand about themselves.

## 4. Why not ChatGPT?

ChatGPT is **stateless empathy**. It is brilliant at the current message and amnesiac about the user's life. It cannot say "this is the seventh time in eight weeks you've described feeling invisible at family dinners," because it does not remember the previous six.

InnerFlow is **stateful self-observation**. The whole product is the long horizon.

## 5. Why not a Journal?

A journal is a **write-only mirror**. Users dump words in; they rarely come back and re-read 80 entries to find a thread. Journals require the user to already be a pattern-detector — which is exactly the skill the user is missing.

InnerFlow is a **read-back mirror**. The system does the longitudinal reading, surfaces candidate patterns with evidence, and hands the interpretation back to the user.

---

## 6. Domain Design

Domains are the **user-facing surface**. They map to "the corners of my life," not to psychology textbooks. Every Pattern instance must be attached to one Domain (its primary domain) and may be cross-referenced from others.

V1 ships with **six domains**:

| Domain key | Display (zh) | Display (en) | Scope |
|------------|--------------|--------------|-------|
| `self`     | 对自己       | Self         | Self-worth, self-criticism, identity, internal narrative |
| `family`   | 家庭         | Family       | Parents, siblings, family-of-origin dynamics |
| `intimate` | 亲密关系     | Intimate     | Romantic partners, dating, close emotional bonds |
| `work`     | 工作 / 学业  | Work         | Career, study, achievement, authority figures |
| `social`   | 社交         | Social       | Friends, peers, public-facing self |
| `body`     | 身体 / 状态  | Body         | Sleep, eating, energy, somatic responses tied to emotion |

A user's Insight Panel is browsed by Domain by default — "show me what's happening with family" — even though the underlying engine reasons in Patterns.

## 7. Pattern Taxonomy

V1 ships with **12 patterns**. Each is a stable, system-internal `pattern_key` with a curated short description, a default primary domain, and a list of "where this typically shows up." LLM never invents new keys in V1; it can only **instantiate** these with personalized evidence and description.

| `pattern_key`              | Short name (zh)        | Primary domain | Also appears in        |
|----------------------------|------------------------|----------------|------------------------|
| `comparison_loop`          | 比较循环               | self           | work, social           |
| `perfectionism`            | 完美主义               | work           | self                   |
| `people_pleasing`          | 讨好倾向               | social         | family, intimate, work |
| `boundary_difficulty`      | 边界困难               | family         | intimate, work         |
| `self_criticism`           | 自我批评               | self           | work                   |
| `rumination`               | 反刍思维               | self           | intimate, work         |
| `avoidance`                | 回避型应对             | self           | intimate, work         |
| `emotional_suppression`    | 情绪压抑               | self           | family, intimate       |
| `family_pressure`          | 家庭压力内化           | family         | self, work             |
| `worth_through_achievement`| 通过成就证明价值       | work           | self                   |
| `conflict_aversion`        | 冲突回避               | intimate       | family, social, work   |
| `over-responsibility`      | 过度负责               | family         | intimate, work         |

**Taxonomy rules (V1):**
- Closed set. No LLM-invented keys in V1.
- Each pattern has a single canonical primary domain for default grouping.
- Cross-domain visibility is allowed: a confirmed `people_pleasing` instance whose evidence centers on the user's father is *primarily* under `family` for that user, even though the pattern's default primary domain is `social`. The instance carries its own `domain` field that overrides the taxonomy default.
- Every pattern definition file contains: a neutral one-line description, **what it is not** (to prevent labeling drift), three example evidence shapes, and three reflective prompts.

## 8. Pattern Schema

Two layers: **PatternDefinition** (static, repo-tracked) and **PatternInstance** (dynamic, per-user).

### 8.1 PatternDefinition (static, version-controlled YAML)

Lives in `src/main/resources/patterns/<pattern_key>.yaml`. Loaded at app startup. Never user-specific.

```yaml
pattern_key: people_pleasing
display_name_zh: 讨好倾向
display_name_en: People-Pleasing
primary_domain: social
also_in: [family, intimate, work]

neutral_description: >
  Repeatedly prioritizing others' comfort over one's own needs,
  followed by internal cost (resentment, exhaustion, self-criticism).

what_it_is_not:
  - A personality flaw or diagnosis.
  - The same thing as being kind or generous.
  - A label to wear permanently.

evidence_shapes:
  - "Says yes, then privately regrets it."
  - "Apologizes before disagreeing."
  - "Tracks others' moods preemptively and adjusts behavior."

reflective_prompts:
  - "When you said yes this time, what were you afraid would happen if you said no?"
  - "Who in your life have you never said no to?"
  - "If this person didn't need you to be okay, what would change?"

version: 1
```

### 8.2 PatternInstance (per-user, in MySQL)

```
PatternInstance {
  id:                    uuid
  user_id:               fk
  pattern_key:           string            // FK to PatternDefinition
  domain:                enum(Domain)      // user-specific primary domain for THIS instance
  status:                enum              // candidate | confirmed | partially_confirmed | rejected | archived
  confidence:            float [0,1]       // model confidence at last refresh
  personalized_summary:  text              // "在和母亲的电话里你反复…"  (LLM-written, user-editable)
  user_note:             text nullable     // user's own words after confirming/refining
  evidence_chain_id:     fk EvidenceChain  // see §9
  first_observed_at:     timestamp
  last_observed_at:      timestamp
  last_reviewed_at:      timestamp nullable
  refresh_count:         int               // how many times the engine re-evaluated this instance
  schema_version:        int
}
```

Invariants:
- Per `(user_id, pattern_key, domain)`, **at most one active instance**. If the engine wants to surface "people_pleasing in family" while there's already an active one, it updates the existing instance instead of creating a duplicate.
- An instance in status `rejected` is sticky for 90 days: the engine will not re-propose the same `(pattern_key, domain)` during that window unless new evidence is substantially different (see §10).

## 9. Evidence Chain Schema

The Evidence Chain is the part the user **clicks on** when they want to know "why does the system think this about me?" It must be human-legible, citation-grade, and always traceable back to the user's own words.

```
EvidenceChain {
  id:                uuid
  pattern_instance_id: fk
  items:             [EvidenceItem]   // 3-7 items in V1
  generated_at:      timestamp
  generator_version: string           // model + prompt version, for evaluation
}

EvidenceItem {
  id:                uuid
  source_type:       enum(chat_message, journal_entry, checkin, wiki_fact)
  source_ref:        string          // e.g. message_id, journal_id, wiki_change_id
  occurred_at:       timestamp
  excerpt:           text            // short verbatim quote OR concise paraphrase, ≤ 280 chars
  is_verbatim:       boolean         // true = direct quote, false = paraphrase
  interpretation:    text            // one neutral sentence linking the excerpt to the pattern
}
```

**Rules:**
- Every PatternInstance MUST have at least 3 EvidenceItems before it can be surfaced to the user. No evidence → no pattern.
- At least one EvidenceItem must be `is_verbatim = true`. Users need to recognize their own voice.
- EvidenceItems are immutable once written; refreshes generate a new EvidenceChain and link it to the instance (history retained).
- Excerpts from messages containing crisis-language or highly sensitive content are excluded from evidence display (handled in §13).

## 10. User Confirmation Flow

When a PatternInstance becomes `candidate` (engine surfaced it, confidence ≥ threshold, ≥3 evidence items), it appears in the Insight Panel for review.

Four user actions:

1. **认同 / Confirm**
   - Instance → `confirmed`.
   - `personalized_summary` is promoted into User Wiki as a `pattern_fact` (see §12).
   - Reflective prompt is offered, optionally captured as `user_note`.

2. **部分认同 / Partial**
   - Instance → `partially_confirmed`.
   - User can edit `personalized_summary` directly (e.g., "和母亲是的，和父亲不是").
   - Edited summary is what propagates into User Wiki, not the LLM's original.

3. **不是这样 / Reject**
   - Instance → `rejected`.
   - 90-day cooldown for this `(pattern_key, domain)`.
   - Negative signal is logged for the evaluation set (see §16).

4. **再看看 / Defer**
   - Status unchanged, `last_reviewed_at` updated.
   - Instance remains in panel but is de-prioritized in surfacing order.

User can also **retire** a previously `confirmed` instance ("this isn't me anymore") → status `archived`, with `last_observed_at` frozen. Archived instances remain visible in a "Past Patterns" view to show growth over time.

## 11. Insight Panel Design

A new top-level frontend route: `/insight` (Vue view: `InsightView.vue`).

Layout:

```
+----------------------------------------------------+
|  Insight                                            |
|  [Self] [Family] [Intimate] [Work] [Social] [Body]  |  ← domain tabs
+----------------------------------------------------+
|  [Active]  [Partial]  [Past]                        |  ← status filter
+----------------------------------------------------+
|  ┌──────────────────────────────────────────────┐  |
|  │ 讨好倾向 · in 家庭                            │  |
|  │ "在和母亲的电话里你反复压下自己的想法…"        │  |
|  │ 5 段证据 · 最近一次: 3 天前                    │  |
|  │ [看证据]  [认同]  [部分]  [不是]  [再看看]    │  |
|  └──────────────────────────────────────────────┘  |
|  ┌──────────────────────────────────────────────┐  |
|  │ 比较循环 · in 工作                             │  |
|  ...                                                |
+----------------------------------------------------+
```

Card states:
- **Candidate** (default for engine-surfaced): neutral card, four action buttons.
- **Confirmed**: green accent, shows user_note if any, reflective prompt button.
- **Partial**: amber accent, summary is editable inline.
- **Rejected / Archived**: hidden by default, accessible via "Past" tab.

Evidence drawer (slide-up on `[看证据]`):
- Vertical timeline of EvidenceItems, newest first.
- Each item: timestamp, source type icon, excerpt (verbatim shown in quote marks), one-line interpretation.
- "跳到原对话" link for each item, deep-links into ChatView at that message.

V1 deliberately ships **no charts** (no heatmaps, no frequency bars). Pattern Discovery is not analytics; it is recognition. Charts can come post-V1 if users ask for them.

## 12. Memory Integration Strategy

The existing `memory` module already has: `UserMemory`, `Persona`, `MemoryCompressionService`, `TriggerArchiveScheduler`, User Wiki with structured JSON + ChangeLog. We extend it; we do not replace it.

Two new connections:

### 12.1 User Wiki ← Pattern System (write path)

When a PatternInstance is `confirmed` or `partially_confirmed`, a `PatternFact` is written into User Wiki under a new top-level key `patterns`:

```json
{
  "patterns": [
    {
      "pattern_key": "people_pleasing",
      "domain": "family",
      "summary": "在和母亲的电话里反复压下自己的想法...",
      "user_note": "对父亲不太一样",
      "confirmed_at": "2026-05-29T...",
      "instance_id": "..."
    }
  ]
}
```

The existing structured-JSON + ChangeLog flow handles versioning. Pattern facts are first-class Wiki entries, so the existing `/feedback` correct/delete/add APIs (P3-12) automatically work on them — no new endpoint.

### 12.2 Pattern Engine ← User Wiki + Memory (read path)

The Pattern Engine is a scheduled job (initially: nightly per user; manual trigger endpoint for dev). For each user, it:

1. Loads recent N=200 conversation messages, journal entries, check-ins.
2. Loads current User Wiki (including any already-confirmed patterns — to avoid re-proposing them).
3. Loads all `rejected` PatternInstances in the 90-day cooldown window.
4. Calls an LLM with the Pattern Taxonomy + filtered user context.
5. For each candidate `(pattern_key, domain)` the LLM returns, requires ≥3 evidence items with verbatim/paraphrase + interpretation.
6. Deduplicates against existing active instances (reusing the embedding-dedup infrastructure from P3-11 on the `personalized_summary` field).
7. Writes/updates PatternInstance + EvidenceChain in a single transaction.

The engine **never deletes** a user-confirmed instance. It can only:
- Add new EvidenceChain refreshes to existing instances.
- Propose new instances.
- Lower confidence on an inactive instance (signals "I haven't seen this in 6 weeks").

## 13. Safety & Boundary Design

This is the section that keeps the project honest. Pattern Discovery is high-risk for sounding diagnostic. Hard rules for V1:

1. **Language firewall.** The system never uses: "你有...", "你是...型人格", "诊断", "病", "障碍", "症". Every surfaced pattern uses the canonical phrasing:
   > "我观察到在 X 场景里反复出现 Y。这个可能属于 `<pattern_display>` 类的 pattern，你怎么看？"
   This phrasing is enforced by a post-generation linter on `personalized_summary` (regex blacklist + LLM judge fallback). Rejected outputs are regenerated, not shipped.

2. **No clinical claims.** Pattern descriptions never reference DSM, ICD, MBTI, attachment theory taxonomy, etc. The 12 patterns are described in plain language only.

3. **Crisis path is preserved.** The existing crisis-response behavior in the agent layer is untouched. Pattern Discovery runs **offline / async**; it does not intercept real-time chat. The crisis-language filter in §9 also removes any EvidenceItem whose source message triggered a crisis flag.

4. **User-owned interpretation.** Status is `candidate` by default. The user must take an action to make any pattern "real." The system displays candidate count but does not act on candidates beyond surfacing.

5. **No outward export.** V1 has zero share/export buttons. Patterns are private. The Doctor Dashboard (existing) does not receive Pattern data in V1.

6. **No push in V1.** The system never proactively notifies the user about a new pattern. The Insight panel is pull-only. (Push is V2, post-evaluation.)

7. **Right to delete.** Any PatternInstance and its EvidenceChain can be deleted by the user; deletion is hard (no soft-delete), and a corresponding Wiki ChangeLog entry is created.

## 14. V1 Scope

In scope for V1 (the minimum that makes the pivot real):

- [ ] **Backend**
  - [ ] New package `com.ling.linginnerflow.pattern` with: `PatternDefinition` loader, `PatternInstance` JPA entity + repository, `EvidenceChain` + `EvidenceItem` entities, `PatternDiscoveryService`, `PatternReviewService` (user actions), `PatternController`.
  - [ ] 12 `PatternDefinition` YAML files under `src/main/resources/patterns/`.
  - [ ] Scheduled job: nightly per-user Pattern refresh + manual `POST /api/pattern/refresh` (dev only, gated).
  - [ ] Endpoints: `GET /api/pattern/instances?domain=&status=`, `GET /api/pattern/instances/{id}/evidence`, `POST /api/pattern/instances/{id}/review` (action ∈ confirm/partial/reject/defer/archive), `PATCH /api/pattern/instances/{id}` (edit summary/note).
  - [ ] Memory integration: `PatternFact` writer into existing User Wiki; reuse existing feedback endpoints for correct/delete.
  - [ ] Language firewall linter on `personalized_summary`.
- [ ] **Frontend**
  - [ ] `InsightView.vue` route at `/insight` with domain tabs, status filters, pattern cards, evidence drawer, action buttons.
  - [ ] Pinia store `pattern.ts`.
  - [ ] API client in `frontend/src/api/pattern.ts`.
  - [ ] Empty states ("还没有足够的对话来形成模式 — 再聊一会儿吧") and rejection-cooldown explainer.
- [ ] **Docs & ADR**
  - [ ] ADR: "InnerFlow pivots to Pattern Discovery as the core surface."
  - [ ] API contract doc under `docs/api-contracts/pattern.md`.
- [ ] **Tests**
  - [ ] Unit: PatternDefinition loader, evidence-count invariant, dedup, 90-day cooldown.
  - [ ] Service: `PatternDiscoveryService` end-to-end on a seeded fixture user.
  - [ ] Controller: review action transitions.
  - [ ] Frontend: `npm run type-check` and `npm run build` clean.
- [ ] **Removals / Sunsets (this is the pivot)**
  - [ ] Doctor Dashboard / FHIR / Clinical Evidence modal: feature-flag off in default config (not deleted in V1, dormant).
  - [ ] Pet / Tap / Wall: keep code, remove from default nav; not on the V1 critical path.
  - [ ] Marketing/UX copy (README, login, chat) updated from "AI emotional support / clinical assistant" to "a mirror for self-recognition."

## 15. Out of Scope (V1)

Explicitly **not** in V1, to keep the surface honest:

- Proactive push of pattern observations (deferred to V2).
- Any therapy, treatment, intervention, exercise, or prescription content.
- Diagnostic labels, MBTI / Big Five / attachment-style assessments.
- Frequency charts, heatmaps, dashboards, "pattern strength over time" visualizations.
- Sharing / exporting patterns; multi-user social features.
- New LLM-invented `pattern_key`s.
- Doctor-facing pattern view; FHIR export of patterns.
- Multimodal (image / audio) pattern detection — text only in V1.
- Real-time pattern surfacing during chat — async batch only.

## 16. Success Metrics

This is a portfolio project. Success is measured by **engineering rigor + user-meaningful behavior**, not DAUs.

Quantitative (engineering):
- **Evidence-grounding rate**: ≥ 100% of surfaced PatternInstances have ≥3 EvidenceItems, ≥1 verbatim. (Hard invariant; CI test.)
- **Language firewall pass rate**: ≥ 99% of generated `personalized_summary` pass the linter on first generation; 100% post-regeneration.
- **Dedup effectiveness**: < 5% duplicate active instances per `(user, pattern_key, domain)` in a 30-day window on the seed dataset.
- **Refresh determinism**: re-running the engine on identical inputs yields ≥ 90% identical EvidenceChain composition (Jaccard on source_refs).
- **Test coverage**: pattern package ≥ 80% line coverage.

Qualitative (user-meaningful, evaluated on a seeded eval set of synthetic + 3-5 real user histories):
- **Recognition rate**: ≥ 60% of surfaced candidates are confirmed or partially confirmed by the target user.
- **"This is me" moments**: at least one instance per evaluated user generates an unprompted "yes, exactly" response in user testing.

## 17. Failure Metrics

We will consider V1 to have failed if any of the following are true after the eval pass:

- **Diagnostic drift**: any generated summary uses prohibited language (诊断 / 障碍 / 你是X型) and slips past the linter into the UI.
- **Label without evidence**: any PatternInstance is shown to a user with fewer than 3 EvidenceItems or zero verbatim quotes.
- **Crisis leakage**: any EvidenceItem references content from a message that was flagged as crisis-language.
- **Recognition rate < 30%**: candidates are wrong more than they are right → the taxonomy or the prompt is broken.
- **Rejection sticking is broken**: a rejected `(pattern_key, domain)` is re-surfaced inside its 90-day cooldown with non-substantially-new evidence.
- **Wiki corruption**: confirmed PatternFacts written into User Wiki break the existing structured-JSON schema or ChangeLog timeline.
- **Reviewer can't tell why**: in code review, a reader cannot answer "why does the system think this user has pattern X?" from the EvidenceChain alone, without reading LLM prompts.

Any one of these = V1 ships disabled behind a feature flag until fixed. No exceptions.

---

*End of PATTERN_DISCOVERY_V1.md.*
