# 🌊 InnerFlow — An Eval-First Clinical Mental-Health Agent Platform

A full-stack agentic platform for mental-health support and clinician hand-off — multimodal emotion sensing, LangGraph4j stateful routing, a ReAct agent over 6 clinical tools, three-layer memory, hybrid RAG, and HL7 FHIR / MCP interop.

What makes it different is **how it's built**: every core capability is backed by a **frozen-test-set evaluation, measured against baselines, and reported honestly** — including the negative results. The numbers below are real and reproducible from this repo; none are estimated.

[![Live Demo](https://img.shields.io/badge/Live%20Demo-online-blue?style=flat-square)](http://35.170.192.217)
[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=java)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green?style=flat-square&logo=springboot)](https://spring.io/)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D?style=flat-square&logo=vue.js)](https://vuejs.org/)
[![Tests](https://img.shields.io/badge/tests-260%2B-success?style=flat-square)](#-reliability--evaluation)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

---

## 📊 Results at a glance

> Methodology note: every result has a fixed/frozen test set declared *before* iterating, and is compared against baselines. "Deterministic" means no live LLM (reproducible in CI); "real-LLM" results are a small-sample live check. Caveats are stated, not hidden.

| Capability | Result | How it's measured |
|---|---|---|
| **Deterministic memory kernel** | **Only design strong on all 3 metrics** vs 4 baselines — **0 contradictions · 1.0 conflict-resolution · 1.0 historical recall**; prompt iteration lifted conflict-resolution **0.40 → 0.80, holding on a held-out split** | 22 pre-registered cases; baselines: full-history / RAG / last-write-wins / extract-only; dev/locked/challenge splits |
| **Result-driven ReAct loop** (reliability) | Correct failure-handling **20% / 60% → 100%**; **crashes → 0**, raw-error-as-data **→ 0**; retry turns failure prob `p` into ~`p²` (**tool success 69.7% → 91.2%**, run-to-run variance 0.016 → 0.012) | Deterministic A/B harness with fault injection ([`LoopAbEvalTest`](src/test/java/com/ling/linginnerflow/agent/LoopAbEvalTest.java)), 2 model profiles |
| ↳ validated on **real LLMs** | `gpt-4o-mini` **100% / 100%** (no regression); `gpt-3.5-turbo` **80% → 100%** (weaker model, bigger gain) | live small-sample check ([`eval/live-llm/`](eval/live-llm/RESULTS.md)) |
| **Companion-safety guard** (anti-dependency) | **0 red-team bypass** on a "mirror, not pacifier" invariant; honest finding: prompt was already stable, guard is defense-in-depth | red-team unit tests + a live adversarial eval |
| **Latency** | time-to-first-token **2.8s → ~0.9s** | speculative tool dispatch (parse-while-streaming → async kickoff) |
| **Engineering** | **260+ automated tests** (211 JUnit + 50 pytest), delivered across **50+ reviewed PRs** | CI on every PR |

---

## 🧭 How this project is built (the part I care about)

- **Eval-first, not demo-first.** A capability isn't "done" until there's a test set and a number. I freeze the test set *before* iterating to avoid overfitting to it.
- **Held-out & anti-gaming splits.** dev / locked / challenge splits; metrics designed so you can't game them by over-emitting.
- **Honest negative results.** When an LLM variant was *worse* than the deterministic one, that's reported, not buried. No fabricated or "reasonable-estimate" metrics anywhere.
- **Fail-safe by default.** In a clinical setting, an uncaught tool error or a crisis signal must never be silently treated as success — the system degrades safely, and a test pins that behavior.

---

## 🏗️ Architecture

```
User Browser
     │
  Nginx (:80 reverse proxy) ── /api/* → Spring Boot :8080
     │                         /      → Vue 3 static
Spring Boot (Java 21)
  ├── LangGraph4j emotion graph:  Analyzer → MultimodalFusion → Planner → [L1–L5 nodes]
  ├── ReAct agent (6 clinical tools) with a typed, result-driven loop
  ├── MCP server (6 endpoints) — tools callable by external agents
  ├── Hybrid RAG (BM25/Elasticsearch + vector/Pinecone + HyDE + LLM reranker)
  └── Three-layer memory (Redis short-term · MySQL long-term · LLM clinical reflection)
Infrastructure
  └── MySQL 8 · Redis · Kafka (3 partitions × 3 idempotent consumers, DLQ) · Elasticsearch · Prometheus/Grafana · Docker/AWS
```

---

## ⚙️ Core capabilities

**Planner + LangGraph4j emotion routing.** Each message runs `EmotionAnalyzer → MultimodalFusion → Planner → ReActAgent`. The Planner fuses the raw emotion level + session trajectory + long-term user memory into one of four strategies — `pure` / `escalate` / `de-escalate` / `blend` — then routes across **L1 companionship → L2 guided → L3 CBT → L4 referral → L5 crisis**. L5 bypasses the Planner for a zero-delay, fail-safe crisis response.

**Typed, result-driven ReAct loop.** The loop decides on a **typed** tool result (`SUCCESS / PARTIAL / FAILURE`) rather than feeding raw text back and assuming success: on failure the model gets a recovery instruction (never the raw error as data), with retry-once and fail-safe handling of exceptions. Six clinical tools: `PHQ9ScreeningTool`, `CBTSkillLibrary`, `EmotionTrendAnalyzer`, `HistoryContextRetriever`, `WellnessResourceSearch`, `FHIR Summary (MCP)`. See the [reliability section](#-reliability--evaluation).

**Three-layer memory.** Redis short-term context (TTL + auto-summarization past 10 turns) · MySQL long-term `UserMemory` (emotion pattern / core struggles / coping strategies / persona, etc.) · async LLM-generated clinical reflection.

**Hybrid RAG.** BM25 (Elasticsearch) + vector (Pinecone) + HyDE query expansion + LLM reranker.

**Companion-safety guard (anti-dependency).** A deterministic post-guard enforcing a "mirror, not pacifier" rule — rejects dependency-inducing output and falls back safely (EN/ZH), backed by red-team tests and a live adversarial eval.

**Clinician dashboard.** Stats cards, emotion-trend charts, a real-time Planner-routing panel, LLM-generated PHQ-9 reports, a crisis heatmap, a CBT-evidence panel, and one-click FHIR R4 round-trip to an EHR.

**Distributed reliability.** Three-tier Redis cache defense (penetration / avalanche / breakdown) + idempotent Kafka consumer (distributed lock + exponential backoff + dead-letter queue) + Bucket4j/Redisson rate limiting — all with concurrency tests.

---

## 🔬 Reliability & evaluation

The loop's reliability work is the clearest example of the eval-first approach, shipped across three reviewed PRs:

- **[#66](https://github.com/LING-6150/Ling-innerflow/pull/66)** — re-architected the tool loop to be typed-result-driven (`SUCCESS/PARTIAL/FAILURE`), with retry-once and fail-safe exception handling, **without changing any of the 6 existing tools** (default-method extension).
- **[#67](https://github.com/LING-6150/Ling-innerflow/pull/67)** — a **deterministic, no-live-LLM A/B harness with fault injection** ([`LoopAbEvalTest`](src/test/java/com/ling/linginnerflow/agent/LoopAbEvalTest.java)) comparing assume-success vs result-driven across two model profiles. Correct failure-handling **20%/60% → 100%**, zero crashes / error-as-data, retry turning `p` into ~`p²` (variance 0.016 → 0.012), happy-path byte-identical (no regression). All claims asserted in CI.
- **[#68](https://github.com/LING-6150/Ling-innerflow/pull/68)** — a **real-LLM** small-sample check ([`eval/live-llm/RESULTS.md`](eval/live-llm/RESULTS.md)): a capable model already behaves like the optimistic profile (100%/100%, no regression), while a weaker model degrades on the baseline (80%) but holds at 100% with the typed loop. **Conclusion: typed results buy robustness across model quality, not a smarter strong model.**

---

## 🛠️ Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 · TypeScript · Python |
| Framework | Spring Boot 3.2.5 · Vue 3 · LangGraph4j 1.6.0 |
| AI | Spring AI · OpenAI GPT-4o · Whisper · Embeddings |
| Retrieval | Pinecone (vector) · Elasticsearch 8.13 (BM25) · HyDE + reranker |
| Data | MySQL 8 · Redis |
| Messaging | Apache Kafka (3 partitions × 3 consumers, idempotent, DLQ) |
| Security | Spring Security · JWT · Bucket4j · Redisson |
| Standards | HL7 FHIR R4 · MCP · A2A |
| Eval/Test | JUnit · pytest · deterministic A/B + fault injection |
| Ops | Prometheus/Grafana · Docker Compose · Nginx · AWS EC2 |

---

## 🚀 Quick start

```bash
git clone https://github.com/LING-6150/Ling-innerflow.git
cd Ling-innerflow
cp .env.example .env          # fill in OPENAI_API_KEY, PINECONE_API_KEY, DB_PASSWORD
docker compose up -d          # start all services
docker compose ps
```

| Service | URL |
|---|---|
| Frontend | http://localhost |
| API | http://localhost:8080/api |
| WebSocket | ws://localhost:8080/ws |

**MCP endpoints** (callable by external agents): `POST /mcp/{phq9-screening, cbt-skill-library, emotion-trend-analyzer, history-context-retriever, wellness-resource-search, fhir-summary}`

---

## 🗺️ Where to look (for reviewers)

| Interest | Start here |
|---|---|
| The typed result-driven loop | [`agent/ReActAgent.java`](src/main/java/com/ling/linginnerflow/agent/ReActAgent.java) · [`agent/tool/ActionResult.java`](src/main/java/com/ling/linginnerflow/agent/tool/ActionResult.java) |
| Reliability A/B + fault injection | [`agent/LoopAbEvalTest.java`](src/test/java/com/ling/linginnerflow/agent/LoopAbEvalTest.java) · [`agent/LoopEvalHarness.java`](src/test/java/com/ling/linginnerflow/agent/LoopEvalHarness.java) |
| Real-LLM validation | [`eval/live-llm/`](eval/live-llm/RESULTS.md) |
| Emotion routing graph | `agent/` LangGraph4j nodes (Analyzer / Planner / L1–L5) |
| Safety routing & crisis fail-safe | L5 crisis short-circuit + companion-safety guard |

---

## 👩‍💻 Author

**Ling Duan** — Graduate Student, Northeastern University (Information Systems).
GitHub [@LING-6150](https://github.com/LING-6150)

<sub>InnerFlow began at the North America Healthcare AI Hackathon (Agents Assemble 2026) and has since been developed into the eval-first platform described above.</sub>
