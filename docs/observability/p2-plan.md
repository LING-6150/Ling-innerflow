# P2 Observability Plan

Date: 2026-07-06
Base: `origin/main` after PR #78

## Goal

P2 moves the P1 instrumentation from "spans and first dashboard exist" to "the team can validate, tune, and safely alert on the system." It should stay operationally focused: improve dashboard fidelity, record runtime evidence, and prepare alert rules after a baseline window.

## Non-Goals

P2 should not add new business behavior, change emotion routing, change RAG ranking, or refactor memory persistence. It should also avoid new high-cardinality metric labels, user text in metrics, or hard alert thresholds before a local baseline has been observed.

## P1 Local Runtime Validation

Run this on a machine with Docker, real API keys, and enough memory for the full compose stack.

1. Create `.env` from `.env.example` and fill:

```bash
cp .env.example .env
```

Required values:

- `DB_PASSWORD`
- `REDIS_PASSWORD`
- `OPENAI_API_KEY`
- `PINECONE_API_KEY`
- `JWT_SECRET`
- `TRACING_SAMPLE_RATE=1.0` for the validation run. Append this to `.env`; it is not listed in `.env.example`, and compose otherwise defaults to `0.1`.

2. Start the stack:

```bash
docker compose up -d --build
docker compose ps
```

3. Confirm infrastructure is reachable:

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Tempo: `http://localhost:3200`
- App health: `http://localhost:8080/actuator/health`

4. Register with `/api/auth/register` or log in with `/api/auth/login`, then trigger an L3/L4 `/api/emotion/analyze` request. Use a realistic emotional input that should route to RAG, for example stress, anxiety, or negative thinking content.

5. Open Grafana dashboard `InnerFlow P1 Observability` and confirm:

- HTTP request rate, latency, and 5xx panels show data.
- `emotion.graph.invoke` shows data.
- `node.*` panels include analyzer, planner, and the routed level node.
- RAG stage panels include `hyde`, `vector_search`, `keyword_search`, `rrf_merge`, `candidate_fetch`, `rerank`, and `final_context` when the L3/L4 path is exercised.
- memory panels include `add_message`, `build_context`, or related memory operations.
- prompt usage shows fixed `prompt.id` values only.

6. Open Tempo Explore and confirm the trace shape from `docs/observability/p1-acceptance.md`:

```text
http.server.requests
  -> memory.add_message
  -> emotion.graph.invoke
    -> node.analyzer
    -> node.planner
    -> node.l3 / node.l4
      -> rag.hybrid_search
        -> rag.*
  -> memory.add_message
  -> emotion.log
```

7. Record a validation note with:

- date and commit SHA
- trace ID or Tempo query
- dashboard screenshot or panel notes
- any orphan spans or missing panels
- whether the run used `TRACING_SAMPLE_RATE=1.0`

## P2 PR Sequence

### PR 1: Dashboard Refinements

Scope:

- Replace hard-coded `[5m]` PromQL windows with `$__rate_interval`.
- Tighten broad prompt usage metric regexes to known observation timer families.
- Add error-rate panels grouped by `node.name`, `rag.stage`, and `memory.operation` where existing tags support it.
- Keep this PR Grafana-only unless a missing metric makes a panel impossible.

Validation:

- Dashboard JSON parses.
- Grafana can load the provisioned dashboard.
- Existing panels still render after the PromQL changes.
- Refined panels should be checked during the same local runtime run used to create the validation record.

### PR 2: Runtime Validation Record

Scope:

- Add a dated validation record under `docs/observability/`.
- Link the trace ID or include the Tempo query used.
- Record any caveats, especially async `memory.compress` root spans and LangGraph same-thread parentage assumptions.

Validation:

- Documentation-only review.
- The record should clearly separate confirmed runtime evidence from assumptions.

### PR 3: Alert Rule Drafts

Scope:

- Add alert rule candidates as disabled or clearly non-production provisioning.
- Cover HTTP 5xx, HTTP p95, graph latency, RAG stage latency, memory failures, and metric cardinality watchpoints.
- Keep thresholds conservative and explicitly tied to the observed baseline.

Validation:

- Grafana provisioning loads without enabling noisy alerts by default.
- Each alert rule points to an existing dashboard panel or PromQL query.

### PR 4: Trace Drilldown Polish

Scope:

- Add dashboard links or documentation shortcuts for Tempo filters.
- Prefer simple links and documented queries over complex panel wiring until trace queries are stable.

Validation:

- Links resolve in local Grafana.
- Filters return useful traces for `node.name`, `rag.stage`, `memory.operation`, and `prompt.id`.

## Phase Exit Criteria

P2 is complete when:

- P1 runtime validation has been recorded from a Docker-backed local run.
- Dashboard panels use Grafana-friendly intervals and avoid overly broad metric regexes.
- Error-rate visibility exists for the main graph, RAG, and memory paths.
- Alert candidates are reviewable but not noisy by default.
- Remaining gaps are explicitly listed as P3 work rather than hidden in assumptions.
