# Architecture Review

## 1. Current System Architecture Understanding

InnerFlow is currently a full-stack emotional support and clinical-assistant prototype. The backend is a Spring Boot application that owns authentication, real-time chat, emotional routing, long-term memory, RAG, check-ins, doctor-facing summaries, MCP/FHIR tools, pet mechanics, and image/voice integrations. The frontend is a Vue 3 application with chat, profile/wiki, doctor dashboard, tap, wall, pet, and login routes.

The central runtime path is the WebSocket chat flow. A user message is analyzed for emotion, optionally fused with voice/image emotion signals, routed by a planner, answered through a streaming ReAct agent, persisted in `chat_message`, and appended to Redis short-term memory. When the WebSocket closes, short-term memory is compiled into `UserMemory`, reflection is regenerated, last active time is updated, and an emotional image may be generated.

The current system is still oriented around "AI emotional companion / clinical assistant." Pattern Discovery changes the center of gravity. The future system is not primarily real-time support; it is offline, longitudinal, evidence-grounded self-recognition. That means the existing chat, memory, and wiki systems are valuable data sources, but the future Pattern Engine should not be treated as another response node in the existing chat agent.

## 2. Existing Memory Architecture

The memory architecture already has three layers:

- Short-term memory in Redis under `memory:short:{userId}`, storing `ConversationMessage` objects with role, content, and timestamp.
- Async compression through `MemoryCompressionService`, which summarizes older short-term history and keeps recent raw turns.
- Long-term memory in MySQL through `UserMemory`, storing wiki-like fields such as `emotionPattern`, `coreStruggles`, `effectiveCoping`, `triggers`, `progressNotes`, `wikiChangeLog`, `userCorrections`, `languageStyle`, `conversationSummary`, `persona`, `reflection`, `conflicts`, `archivedTriggers`, and `lastActiveAt`.

The strongest reusable ideas are already in the memory layer: session-end compilation, structured wiki merge, trigger scoring, recency decay, semantic dedup with embeddings, user correction, changelog, and trigger archiving. These are close in spirit to Pattern Engine requirements.

The existing memory implementation is not yet evidence-chain-based. It stores summaries and observations, but it does not preserve immutable, citation-grade evidence items with source references. It can say what it inferred, but not rigorously show why from three or more traceable user-authored records.

## 3. Existing Reflection Flow

Reflection is currently a single LLM-generated clinical observation stored on `UserMemory.reflection`. It is generated after `updateLongMemory` saves wiki changes, and it can also be manually regenerated from the doctor dashboard.

The reflection prompt uses:

- `emotionPattern`
- `coreStruggles`
- `effectiveCoping`
- `conversationSummary`

It does not directly use triggers, progress notes, conflicts, user corrections, or source-level evidence. The result is overwritten in place and then reused in future prompt context as `Deep insight`.

This is architecturally different from Pattern Discovery. Reflection is a narrative synthesis. Pattern Engine requires closed-set classification, evidence retrieval, verification, confidence scoring, user review states, and immutable evidence chains. Reflection can remain a consumer of richer memory later, but it is not the same computational layer as Pattern Discovery.

## 4. User Wiki Design

The current User Wiki is implemented inside `UserMemory`, not as a separate wiki aggregate or versioned document model. It is compiled from session history using an LLM merge prompt. It supports user-visible correction through `/api/memory/wiki/correct`, and the Profile view lets users edit text fields, add/delete triggers, view progress notes, see session history, and clear memory.

The Wiki design has useful properties for Pattern Discovery:

- It treats user correction as high-authority feedback.
- It keeps change history through `wikiChangeLog`.
- It has a user-facing self-knowledge surface.
- It already distinguishes first extraction from later merge updates.
- It has a rough notion of observation recurrence through trigger count and score.

The design also has limitations relative to Pattern Engine:

- Multiple JSON strings live inside one entity, so validation, querying, and versioning are weak.
- Wiki facts are not linked to immutable source evidence.
- Current correction APIs are field-oriented, not pattern-instance-oriented.
- There is no first-class `PatternFact` structure yet.
- There is no separation between user-owned self-knowledge and clinician-facing clinical interpretation.

## 5. Modules Closest to Future Pattern Engine

The closest existing modules are:

- `memory.MemoryService`: closest conceptually, because it compiles long-term user understanding and already has merge, correction, scoring, dedup, and changelog logic.
- `memory.UserMemory`: closest storage integration point for confirmed or partially confirmed Pattern Facts, though it is not enough by itself for PatternInstance/EvidenceChain.
- `memory.MemoryCompressionService`: useful for summarization and async execution patterns, but not enough for evidence-grounded pattern detection.
- `websocket.ChatMessageRepository`: core source corpus for user-authored chat evidence.
- `checkin.CheckInRepository`: source corpus for user-authored check-ins.
- `rag.HybridSearchService`, `HyDEService`, `LLMRerankerService`: closest retrieval machinery, though currently aimed at CBT documents rather than per-user history.
- `EmotionTrendAnalyzer` and `HistoryContextRetriever`: lightweight examples of reading longitudinal user history, but not rigorous enough for Pattern Engine.
- `ProfileView.vue`: closest user-facing self-knowledge surface.
- `DoctorDashboard.vue`: useful as a contrast case, because Pattern V1 explicitly excludes doctor-facing pattern surfacing.

The most directly reusable technical ideas are embedding-based semantic dedup, recency scoring, user correction, history retrieval, and the existing hybrid retrieval stack.

## 6. Design Conflicts With Pattern Engine

The largest conflict is product orientation. The current architecture is optimized for real-time emotional response and clinical dashboarding. Pattern Engine V1 is offline, pull-only, user-owned, non-clinical, and evidence-first.

Specific conflicts:

- Real-time chat path vs offline batch: existing agent flow is synchronous/streaming around a live user message, while Pattern Engine must run outside the response path.
- Clinical language vs language firewall: current prompts and docs use terms like clinical observation, patient, PHQ-9, doctor dashboard, and therapy-adjacent language. Pattern Discovery forbids diagnostic or clinical framing in the user-facing surface.
- Summary-first memory vs evidence-first patterns: current wiki merge stores inferred fields, while Pattern Engine must surface only patterns with validated evidence chains.
- Mutable wiki fields vs immutable evidence chains: current wiki updates overwrite fields and append simple logs, while Pattern Engine requires immutable EvidenceItems and retained EvidenceChain history.
- Doctor visibility vs private pattern surface: current architecture exposes rich memory to the doctor dashboard; Pattern V1 explicitly says Pattern data is not doctor-facing.
- Broad public doctor endpoints: current security config permits `/api/doctor/**`, which conflicts with the privacy posture needed for user-owned pattern data.
- LLM trust model: current memory merge trusts LLM JSON extraction directly; Pattern Engine specs require retrieval, verification, calibration, baselines, ground truth tiers, and strict safety gates.
- Existing `UserMemory` shape: it can host confirmed pattern facts eventually, but it is not suitable as the source of truth for PatternInstance, EvidenceChain, eval metadata, cooldown, or review state.

## 7. Highest-Complexity Areas

The highest implementation complexity is not adding CRUD tables. It is making the detection pipeline trustworthy.

The hardest parts are:

- Building a per-user corpus layer that normalizes chat messages, check-ins, future journal entries, and wiki facts into stable source-addressable documents.
- Creating retrieval over user history without cross-user leakage.
- Producing EvidenceChains where each item is traceable, human-legible, non-crisis, and at least partly verbatim.
- Verifying evidence support without letting the LLM invent quotes or over-label ambiguous text.
- Implementing evaluation infrastructure with Tier A, Tier A-H, verifier labels, baselines, threshold calibration, and recall-retention reporting.
- Reconciling confirmed/partial/rejected/archive user review states with current wiki correction semantics.
- Keeping Pattern Discovery separate from clinical/doctor surfaces while the existing product still contains those surfaces.

## 8. Most Concerning Technical Risks

The biggest risk is false authority: surfacing a pattern that feels psychologically loaded without enough evidence. Pattern Engine tries to solve this with evidence floors, language firewall, user confirmation, and eval, but the current codebase does not yet have those guardrails.

Other major risks:

- Evidence leakage across users if retrieval filters or indexes are not strictly isolated.
- Crisis content appearing as evidence, which Pattern specs treat as a hard failure.
- LLM-generated summaries or interpretations drifting into diagnosis, identity labeling, or therapy claims.
- Losing sessions when wiki compilation fails after short-term memory is cleared.
- Fitting thresholds and evaluation metrics to synthetic data in ways that do not survive human-written prose.
- Overloading `UserMemory` with Pattern Engine state instead of giving patterns their own lifecycle.
- Public or weakly protected doctor/clinical endpoints creating privacy risk once deeper pattern data exists.
- Turning Pattern Discovery into another chat/reflection feature, which would violate the offline, pull-only, user-owned design.

Overall, the current system has unusually useful raw material for Pattern Engine: stored longitudinal user text, wiki compilation, embedding dedup, recency scoring, correction loops, and RAG infrastructure. But the future Pattern Engine is a new architectural subsystem, not a small extension of reflection or triggers. The main architectural work is creating a separate evidence-grounded pattern layer while carefully reusing memory and retrieval primitives without inheriting their current clinical framing or summary-first assumptions.
