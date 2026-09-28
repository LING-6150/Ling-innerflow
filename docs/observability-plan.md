# InnerFlow → AI Runtime Platform: Observability & Benchmark Plan

> **Status:** Audit + planning only. No code changed yet.
> **Implementation note:** File names and call-site names below are design anchors from the audited codebase, not hard-coded line-number contracts. Before implementing each issue, re-check the current GitHub `main` branch and adapt to the live method names and module shape.
> **Goal:** Reposition InnerFlow from "emotional-support app" to a **production-grade AI runtime platform**, with the resume narrative aimed at **Applied AI / AI Infra**.
> **Thesis:** The product already has a non-trivial, *dual* agent runtime (a custom streaming ReAct loop **and** a LangGraph4j state graph), a multi-stage hybrid RAG pipeline, a 3-tier memory system, and layered safety. What it lacks is the thing infra teams are actually graded on: **you cannot currently see, per step, how long it took, what it cost, which model/prompt ran, whether it retried, and why it failed.** This plan adds that, then turns it into a defensible benchmark.

---

## 0. TL;DR

| | Today | After Phase 1 | After Phase 2 |
|---|---|---|---|
| Per-step latency | ❌ ad-hoc `log.info` only | ✅ OTEL span per node/tool/RAG-stage | ✅ + p50/p95 dashboards |
| Token / cost | ❌ discarded (`.content()` drops usage) | ✅ captured or explicitly recorded from Spring AI observations | ✅ cost-per-conversation, per-runtime |
| Model / prompt version | ❌ not attributed | ✅ span attributes | ✅ compared across versions |
| Retry / failure reason | ❌ swallowed by fallbacks | ✅ span status + events | ✅ failure-rate SLOs |
| End-to-end trace | ❌ none | ✅ one trace covers WS→analyzer→planner→Lx / RAG / memory | ✅ |
| "My runtime vs LangGraph" | ❌ anecdotal | — | ✅ reproducible benchmark + report |

**Estimated effort:** Phase 1 ≈ **6–9 dev-days**, Phase 2 ≈ **5–8 dev-days**.

---

## 1. Current-State Inventory

### 1.1 Runtime topology — there are **two** agent runtimes

This is the single most important finding for the repositioning, and it is *free ammunition* for the Phase-2 benchmark.

**Path A — HTTP, blocking, LangGraph4j**
`POST /api/emotion/analyze` → [`EmotionController`](../src/main/java/com/ling/linginnerflow/controller/EmotionController.java:36) → [`EmotionGraph.buildGraph().invoke()`](../src/main/java/com/ling/linginnerflow/agent/EmotionGraph.java:33)
```
START → analyzer (LLM) → planner (LLM) → conditional route → {L1|L2|L3|L4|L5} → END
```
Each node is an LLM call; L3/L4 additionally run the full hybrid-RAG pipeline. Also driven asynchronously by the Kafka [`CheckInConsumer`](../src/main/java/com/ling/linginnerflow/checkin/CheckInConsumer.java).

**Path B — WebSocket, streaming, custom ReAct runtime**
WS → [`EmotionWebSocketHandler.handleTextMessage`](../src/main/java/com/ling/linginnerflow/websocket/EmotionWebSocketHandler.java:72) → [`EmotionAnalyzerNode.analyze`](../src/main/java/com/ling/linginnerflow/agent/node/EmotionAnalyzerNode.java:16) + `EmotionFusionService` + `PlannerNode` → [`ReActAgent.runStreaming`](../src/main/java/com/ling/linginnerflow/agent/ReActAgent.java:89)
```
Phase 1: stream tokens, incrementally parse "Action:/Action Input:",
         speculatively dispatch tool as a CompletableFuture (TTFT optimization)
Phase 2: stream the user-facing reply
```

> **Repositioning lever:** Path A (LangGraph4j orchestration) and Path B (hand-written streaming ReAct with speculative tool dispatch) solve the *same* routing problem two different ways, in one repo. Phase 2 measures them head-to-head — that comparison is the resume centerpiece.

### 1.2 Module maturity

| Module | Where | Maturity | Notes |
|---|---|---|---|
| Agent runtime (custom) | `agent/ReActAgent.java` (565 LOC) | **Mature / novel** | 2-phase streaming, speculative tool dispatch, L3 prefetch, crisis short-circuit. Reactive (`Flux` on `boundedElastic`). |
| Agent runtime (graph) | `agent/EmotionGraph.java` + `agent/node/*` | **Mature** | LangGraph4j state graph, planner-driven conditional routing. Blocking. |
| Planner / routing | `agent/node/PlannerNode.java` | **Mature** | LLM emits structured JSON routing (targetLevel/strategy/toneHint) with trajectory awareness + fallback. |
| RAG | `rag/HybridSearchService.java` + HyDE/Reranker/CBTKnowledge | **Mature** | HyDE → Pinecone (k=10) + ES BM25 (k=5) → RRF → LLM rerank → top-3. Pinecone warmup scheduler. Graceful degradation. Cache visibility should be instrumented only if the current implementation still has a cache layer. |
| Memory | `memory/MemoryService.java` (833 LOC) + Compression | **Mature** | 3-tier: Redis short-term, MySQL long-term wiki, async compression. Embedding-based trigger dedup (cos>0.88), score decay (90-day half-life), daily archive sweep, user-correction audit trail. |
| Safety / guardrails | `cache/RedisDefenseService`, `config/Sentinel*`, `exception/RateLimitInterceptor` | **Mature (HTTP) / partial (WS)** | Sentinel flow+degrade, Bucket4j per-IP, Redis cache-defense. L5 crisis short-circuit. **WS path has no rate limit / no stream timeout.** |
| Streaming | `websocket/EmotionWebSocketHandler` + `ReActAgent` | **Mature** | WS + Spring AI `Flux`, chunk protocol `{type:chunk|done}`. |
| Eval | `src/test/.../RAGQualityVerificationTest`, `ReActStreamingVerificationTest` | **Weak** | Unit/mocked verification only. **No golden dataset, no recall@k/NDCG, no A/B harness in the working tree** (eval lives only on unmerged `eval/*`, `feature/pe-*` branches). CI runs `mvn package -DskipTests` — tests don't even gate. |
| Telemetry | actuator + micrometer-prometheus on classpath | **Absent in practice** | See §1.4. |

### 1.3 LLM / embedding / vector call-site map (the span-boundary catalog)

Every place latency and cost are incurred — i.e. every span we will create.

| # | Class : method | Op | Notes |
|---|---|---|---|
| 1 | `EmotionAnalyzerNode.analyze` | LLM | level classification (1–5) |
| 2 | `PlannerNode.plan` :96 | LLM | JSON routing decision |
| 3 | `L1CompanionNode` / `L2GuidanceNode` | LLM | response gen |
| 4 | `L3CBTNode.process` :32,:76 | RAG + LLM | hybrid search then response |
| 5 | `L4ProfessionalNode.process` :32,:78 | RAG + LLM | hybrid search then response |
| 6 | `ReActAgent.streamAndParsePhase1` :173 | LLM (stream) | per round (≤2) |
| 7 | `ReActAgent.doStreamingRun` :142 | LLM (stream) | Phase-2 reply |
| 8 | `ReActAgent` tool dispatch :203 | tool | `CompletableFuture` (off-thread) |
| 9 | `HyDEService.generateHypotheticalDocument` :55 | LLM + embed | query expansion |
| 10 | `CBTKnowledgeService.retrieveIdsByVector` / `retrieveRelevantCBT` | embed + Pinecone | vector search |
| 11 | `HybridSearchService.esKeywordSearch` :132 | Elasticsearch | BM25 |
| 12 | `LLMRerankerService.rerank` :47 | LLM | single-call rerank |
| 13 | `HybridSearchService` cache path, if present in current `main` | Redis | Mark hit/miss only where a cache layer exists |
| 14 | `MemoryService.updateLongMemory` :172 | LLM | wiki merge (blocking) |
| 15 | `MemoryService.generateReflection` :780 | LLM | insight synthesis |
| 16 | `MemoryService.findSimilarTrigger` :502 | embed (batch) | dedup |
| 17 | `MemoryCompressionService.generateSummary` :138 | LLM (async) | sliding-window compress |
| 18 | `PHQ9ScreeningTool.execute` :92 | DB + LLM | clinical screen |
| 19 | `PetGreetingService` | LLM | greeting |
| 20 | tools: `EmotionTrendAnalyzer`, `HistoryContextRetriever`, `WellnessResourceSearch`, `CBTSkillLibrary` | mixed | per `AgentTool.execute` |

### 1.4 Telemetry today — the gap

**What exists**
- `spring-boot-starter-actuator` + `micrometer-registry-prometheus` on the classpath ([pom.xml:171-181](../pom.xml)).
- **prod-only** exposure: `application-prod.properties` enables `health,info,prometheus,metrics` and `http.server.requests` histograms. `application.properties` / `-local` enable **nothing** — no management config at all.
- [`prometheus.yml`](../prometheus.yml) scrapes `app:8080/actuator/prometheus`; a Grafana datasource is provisioned (`grafana/provisioning/datasources/prometheus.yml`) but **no dashboards are committed**.
- Logging: SLF4J with bracketed prefixes (`[ReAct]`, `[HybridSearch]`, `[Planner]`…). A few hand-rolled timings exist (`ReActAgent` logs `phase1Ms`, `toolFuture.join()` ms).

**What does NOT exist (the observability question, answered directly)**

| Can we observe, per step (memory / reasoning / tool / safety / response)…? | Today |
|---|---|
| **Latency** | ❌ Only two ad-hoc `log.info` timings in `ReActAgent`. No per-node/per-RAG-stage/per-memory-op timing. |
| **Cost / tokens** | ❌ Every call uses `.call().content()` / `.stream().content()`, which **discards `ChatResponse` usage metadata**. Token counts are never read. |
| **Model** | ❌ Single global `gpt-4o-mini` via property; no per-call attribution, no per-call override capture. |
| **Prompt version** | ❌ No concept of prompt versioning. Prompts are inline text blocks; changes are invisible to any metric. |
| **Retry** | ❌ Only `CheckInConsumer` (Kafka) retries. **Zero LLM/embedding/vector retry**, so nothing to observe. |
| **Failure reason** | ❌ Failures are swallowed by graceful fallbacks (`HybridSearchService` → plain Pinecone; `PlannerNode` → raw level) and logged, but **not measured** — a silent degradation looks identical to success on the dashboard. |
| **Custom Micrometer** | ❌ Grep confirms **zero** `MeterRegistry` / `Counter` / `Timer` / `@Timed` usage anywhere in `src/main`. |
| **Distributed trace** | ❌ **Zero** OpenTelemetry / `micrometer-tracing` dependency or config. No trace/span IDs, no MDC propagation. |

> **Bottom line:** at infra-interview altitude, InnerFlow today is effectively a black box. We have JVM/HTTP auto-metrics and good prose logs, but no causal, per-step, cost-aware trace of a single conversation.

---

## 2. OpenTelemetry Integration Assessment

### 2.1 Strategy — ride Spring AI 1.0's built-in Observation

The cheapest high-leverage move: this repo is **Spring Boot 3.3.6 + Spring AI 1.0.0**. Spring AI 1.0 instruments `ChatClient`, `ChatModel`, and `EmbeddingModel` against the **Micrometer Observation API** and emits OTEL `gen_ai.*` spans (model, input/output tokens, finish reason) **automatically** whenever a `Tracer` bean is present. So:

- Adding `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` should make **call-sites in §1.3 that go through Spring AI `ChatClient`/`EmbeddingModel` produce `gen_ai.*` observations/spans with model metadata and, where the provider exposes it, token usage.** Treat streaming token usage as a must-verify item; if it is missing on `.stream().content()`, record usage explicitly at the call boundary or defer exact cost for that path until a reliable response-usage source is available.
- We then add **manual parent/child spans** only at the orchestration boundaries that Spring can't see (graph nodes, RAG stages, tools, the WS handler), so the auto LLM spans nest correctly under a per-conversation root.

### 2.2 Span tree design

**Path A (HTTP graph)** — Spring MVC auto-creates the HTTP server root; we add node children:
```
HTTP POST /api/emotion/analyze            (auto, Spring MVC observation)
└─ emotion.graph.invoke                   (manual, EmotionGraph)
   ├─ node.analyzer        └─ gen_ai.chat (auto, Spring AI)
   ├─ node.planner         └─ gen_ai.chat (auto)
   └─ node.L3                              (manual)
      ├─ rag.hybrid_search                 (manual)
      │  ├─ rag.hyde   └─ gen_ai.chat + gen_ai.embed (auto)
      │  ├─ rag.pinecone_query  (manual, + gen_ai.embed auto)
      │  ├─ rag.es_bm25         (manual)
      │  ├─ rag.rrf_merge       (manual, fast)
      │  └─ rag.rerank └─ gen_ai.chat (auto)
      └─ gen_ai.chat (auto)
```
**Path B (WS streaming)** — WS lifecycle is **not** auto-instrumented, so the root is manual:
```
ws.message.handle                          (manual root, EmotionWebSocketHandler)
├─ node.analyzer        └─ gen_ai.chat (auto)
├─ emotion.fusion                          (manual)
├─ node.planner         └─ gen_ai.chat (auto)
└─ react.run                               (manual, ReActAgent)
   ├─ react.phase1[round]  └─ gen_ai.chat-stream (auto)
   ├─ tool.<name>                          (manual, spans the CompletableFuture)
   │  └─ (e.g. rag.hybrid_search… or gen_ai.chat for PHQ9)
   └─ react.phase2         └─ gen_ai.chat-stream (auto)
async (on connection close):
└─ memory.update_long      ├─ gen_ai.chat (auto, wiki merge)
                           ├─ gen_ai.chat (auto, reflection)
                           └─ embed (auto, trigger dedup)
```

### 2.3 Instrumentation inventory & intrusiveness

| Target | Change | Intrusiveness |
|---|---|---|
| `pom.xml` | add `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `io.micrometer:context-propagation` (reactor present already) | **Low** (deps) |
| `application*.properties` | `management.tracing.sampling.probability`, OTLP endpoint, enable management in local profile | **Low** (config) |
| new `config/ObservabilityConfig.java` | `ObservedAspect` bean, `Hooks.enableAutomaticContextPropagation()`, custom `ObservationHandler` to copy `gen_ai` token attrs → a cost `Counter` | **Low** (new file) |
| graph nodes (`EmotionAnalyzer`, `Planner`, `L1–L5`) | `@Observed(name=…)` or manual `Observation` wrap of `process()` | **Low–Med** (1 annotation + AOP) |
| `HybridSearchService.hybridSearch` (or the current equivalent entrypoint) + stages, `HyDEService`, `CBTKnowledgeService`, `LLMRerankerService` | manual span per stage | **Med** |
| `MemoryService` (#14/#15/#16), `MemoryCompressionService` (#17) | manual spans; async ops need context capture | **Med** |
| `ReActAgent` (#6/#7/#8) | manual spans **+ reactive context propagation** across `boundedElastic` and the tool `CompletableFuture` | **High** ← main risk |
| `EmotionWebSocketHandler.handleTextMessage` | manual **root** span (WS not auto-traced) + propagate into the `Flux` subscribe | **High** ← main risk |
| tools (`AgentTool.execute` impls, `PHQ9ScreeningTool`) | span per tool | **Low–Med** |
| `docker-compose.yml` | add OTLP collector + Jaeger/Tempo; reuse existing Grafana | **Low** (infra) |

### 2.4 Hard parts / risks

1. **Reactive context propagation (highest risk).** `ReActAgent.runStreaming` does `Flux.create` + `Schedulers.boundedElastic().schedule(...)`, then subscribes the Phase-2 `Flux` on yet another thread, and dispatches tools as `CompletableFuture.supplyAsync`. A span opened on the request thread will **not** be the current span inside those callbacks unless we (a) call `Hooks.enableAutomaticContextPropagation()` and (b) explicitly capture/restore the `Observation`/`Context` across the `boundedElastic` hop and the `CompletableFuture`. Get this wrong and Path-B spans become orphans. **Mitigation:** spike this on `ReActAgent` first, before instrumenting anything else; it is the canary.
2. **WS root span.** No servlet filter wraps a WebSocket text frame, so there is no ambient trace. We must open the root manually in `handleTextMessage` and close it in the stream's `doFinally`, not at method return (the method returns while the `Flux` is still streaming).
3. **Cost capture.** Spring AI may record `gen_ai.usage.input_tokens` / `output_tokens` on observations even though our code calls `.content()`. Derive $ from a static pricing map (`gpt-4o-mini`) only after verifying those attributes are present for blocking and streaming paths. If `.stream().content()` does not expose final usage reliably, keep model/prompt/latency spans intact and add an explicit usage-capture follow-up instead of guessing exact cost.
4. **Prompt version is a new convention.** There is no versioning today. Minimal approach: add a `prompt.version` + `prompt.id` constant next to each prompt builder and stamp it as a span attribute. Low effort but must be applied consistently or comparisons in Phase 2 are meaningless.
5. **Sampling vs completeness.** For a benchmark we want 100% sampling on the eval profile but 5–10% in prod. Drive via `management.tracing.sampling.probability` per profile.
6. **Failure visibility.** Graceful fallbacks must set span status = error + an event (e.g. `rag.fallback.reason`) *before* degrading, otherwise the trace still looks green. This is a behavior change inside existing catch blocks (additive, low risk).

---

## 3. Phased Plan

### Phase 1 — OTEL trace across the core chain  (≈ 6–9 dev-days)

**Objective:** one trace, opened at the WS/HTTP entry, that carries through analyzer → planner → response node → RAG stages → memory ops → every tool, with latency on every span and token/cost/model on every LLM span; failures and fallbacks visibly marked.

**Workstreams & files**
1. **Deps + collector** — `pom.xml`, `application*.properties`, `docker-compose.yml`, new Grafana/Tempo (or Jaeger) wiring. *(~1 day)*
2. **Observability config** — new `config/ObservabilityConfig.java`: `ObservedAspect`, reactor context propagation, cost `ObservationHandler` (tokens→$), `prompt.version` attribute helper. *(~1 day)*
3. **Graph path spans** — `@Observed` on `EmotionAnalyzerNode`, `PlannerNode`, `L1–L5` nodes; manual `emotion.graph.invoke` wrapper in `EmotionGraph` / `EmotionController`. *(~1 day)*
4. **RAG spans** — stage spans in `HybridSearchService.hybridSearch` (or current equivalent), `HyDEService`, `CBTKnowledgeService`, `LLMRerankerService`; mark cache hit/miss only if the current implementation has cache, and always mark fallback events. *(~1–1.5 days)*
5. **Memory spans** — `MemoryService` (updateLongMemory / generateReflection / findSimilarTrigger), `MemoryCompressionService` (async, with context capture). *(~1 day)*
6. **ReAct + WS spans (the risky bit)** — manual root in `EmotionWebSocketHandler.handleTextMessage` closed in `doFinally`; `ReActAgent` phase1/phase2/tool spans with context propagation across `boundedElastic` + `CompletableFuture`. **Spike this first.** *(~1.5–2.5 days)*
7. **Dashboards + verification** — commit a Grafana dashboard JSON (per-step p50/p95, cost-per-conversation, fallback rate); add a trace-presence assertion to the verification tests. *(~1 day)*

**Execution order:** implement P1-01 and P1-02 first, then immediately run the P1-06 canary before fanning out P1-03/P1-04/P1-05. If WebSocket/ReAct context propagation fails, fix that foundation before adding broad instrumentation.

**Risks:** reactive context propagation (Path B), streaming token-usage emission, prompt-version discipline. **Mitigation:** canary on `ReActAgent` before fanning out; keep all changes additive (no behavior change except adding error status to existing catch blocks).

**Done when:** a single conversation (both HTTP and WS) renders as one connected trace in Jaeger/Tempo with model + prompt.version on each LLM span, token/cost where usage is reliably available, and a fallback path shows as a red span with a reason event.

### Phase 2 — Runtime benchmark: my ReAct runtime vs LangGraph  (≈ 5–8 dev-days)

**Objective:** turn the traces from Phase 1 into a reproducible, apples-to-apples benchmark of the **custom streaming ReAct runtime (Path B)** against **LangGraph4j (Path A)** on identical scenarios — and publish the numbers.

**Approach**
1. **Golden scenario set** — a versioned dataset of conversations covering L1–L5, multi-turn trajectories, and tool-triggering inputs (the eval asset the working tree is missing today). *(~1 day)*
2. **Harness** — a runner that drives each scenario through both `ReActAgent.runStreaming` and `EmotionGraph.invoke` under 100% trace sampling, tagging each run with `runtime=react|langgraph` + `scenario.id` + `prompt.version`. *(~1.5–2 days)*
3. **Metrics extracted from traces** — TTFT (streaming), end-to-end latency (p50/p95), #LLM calls, total tokens, $/conversation, tool-dispatch overhead, fallback/failure rate. Quantify the speculative-tool-dispatch win that `ReActAgent`'s comments *claim* (TTFT 2.8s→0.9s) — now with real data. *(~1–1.5 days)*
4. **Comparison dashboard + report** — `docs/benchmark-results.md` + a Grafana board overlaying the two runtimes; a short written analysis of the trade-offs (orchestration overhead & observability ergonomics of LangGraph4j vs control & TTFT of the hand-rolled loop). *(~1.5 days)*
5. *(Stretch)* External LangGraph (Python) target for an industry-standard reference point, driven through the same scenario set via an HTTP shim. *(~2 days, optional)*

**Risks:** the two runtimes are not perfectly feature-equal (ReAct streams + uses tools speculatively; the graph is blocking and routes to fixed nodes) — the report must state the comparison's boundaries honestly. Streaming vs blocking makes "latency" a two-number story (TTFT vs total); keep them separate.

**Done when:** `docs/benchmark-results.md` reports TTFT / latency / tokens / cost / failure-rate for both runtimes on the golden set, reproducible via one command, backed by traces.

---

## 4. Resume / interview narrative (why this sequencing)

- **Phase 1 is the credential.** "Instrumented a multi-runtime LLM system end-to-end with OpenTelemetry — per-step latency, token cost, model, prompt version, retry, and failure reason across a streaming ReAct agent, a LangGraph state machine, a hybrid-RAG pipeline (HyDE+BM25+RRF+LLM-rerank), and a 3-tier memory system." That sentence is Applied-AI/Infra-shaped and now *true and demonstrable*.
- **Phase 2 is the differentiator.** Most candidates can say "I used LangGraph." Few can say "I built my own runtime **and** benchmarked it against LangGraph with trace-derived TTFT/cost numbers, and here's when each wins." The dual runtime already in the repo makes this credible rather than contrived.
- **Order matters:** you cannot benchmark what you cannot measure. Phase 1 must land first; Phase 2 is pure leverage on top of it.

---

## Appendix — GitHub issue drafts

Drafts live as standalone, paste-ready files under [`docs/observability-issues/`](observability-issues/) (one per issue, `gh issue create -F <file>`):

**Phase 1**
- `p1-01-otel-deps-collector.md` — dependencies + OTLP collector + Grafana/Tempo
- `p1-02-observability-config.md` — `ObservabilityConfig`, reactor context propagation, cost handler, prompt-version helper
- `p1-03-graph-node-spans.md` — analyzer / planner / L1–L5 spans
- `p1-04-rag-pipeline-spans.md` — HyDE / Pinecone / ES / RRF / rerank + cache + fallback events
- `p1-05-memory-spans.md` — long-memory / reflection / dedup / compression spans
- `p1-06-react-ws-spans.md` — ReAct phase1/2/tool + WS root span + reactive context propagation **(spike first)**
- `p1-07-dashboards-verify.md` — Grafana dashboard + trace-presence assertions

**Phase 2**
- `p2-01-golden-scenarios.md` — versioned conversation eval set (L1–L5 + tool-triggering)
- `p2-02-benchmark-harness.md` — dual-runtime runner with run tagging
- `p2-03-trace-metrics-extraction.md` — TTFT / latency / tokens / cost / failure-rate from traces
- `p2-04-benchmark-report.md` — `benchmark-results.md` + comparison dashboard
- `p2-05-langgraph-python-reference.md` — *(stretch)* external LangGraph reference target
