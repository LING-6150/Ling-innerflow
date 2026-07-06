# P2 Trace Drilldown Guide

Date: 2026-07-06
Base: `origin/main` after PR #80

## Purpose

This guide gives copyable Tempo Explore queries for the P1 trace shape. It is a drilldown aid, not a runtime validation record. Record real trace IDs and dashboard screenshots in a separate validation note after running the Docker stack locally with `TRACING_SAMPLE_RATE=1.0`.

## Before Querying

Use Grafana Explore with the `Tempo` datasource. The local datasource is provisioned at `http://tempo:3200` inside Docker and surfaced through Grafana at `http://localhost:3000`.

For a single local validation request, set `TRACING_SAMPLE_RATE=1.0`; otherwise a successful request may not be sampled.

## HTTP and Graph Entry

Find emotion analyze requests:

```traceql
{ name = "http.server.requests" && span.http.route = "/api/emotion/analyze" }
```

Find graph invocations:

```traceql
{ name = "emotion.graph.invoke" && span.runtime = "langgraph" }
```

Expected parentage for an L3/L4 HTTP request:

```text
http.server.requests
  -> memory.add_message
  -> emotion.graph.invoke
    -> node.analyzer
    -> node.planner
    -> node.l3 / node.l4
      -> rag.hybrid_search
  -> memory.add_message
  -> emotion.log
```

If `node.*` spans appear as root traces instead of children, record it as a runtime finding. That would mean the LangGraph same-thread scope assumption did not hold in the tested environment.

## Node Queries

Find routed graph nodes:

```traceql
{ span.node.name = "l3" || span.node.name = "l4" }
```

Find analyzer and planner spans:

```traceql
{ span.node.name = "analyzer" || span.node.name = "planner" }
```

Find node failures:

```traceql
{ name =~ "node\\..*" && status = error }
```

Expected bounded tags:

- `node.name`: `analyzer`, `planner`, `l1`, `l2`, `l3`, `l4`, `l5`
- `emotion.level`: `1`-`5` or `unknown`
- `route.level`: `1`-`5`
- `prompt.id`: fixed prompt identifiers only

## RAG Queries

Find the hybrid RAG parent span:

```traceql
{ name = "rag.hybrid_search" }
```

Find common RAG stages:

```traceql
{ span.rag.stage = "hyde" || span.rag.stage = "vector_search" || span.rag.stage = "keyword_search" || span.rag.stage = "rerank" }
```

Find reranker prompt usage:

```traceql
{ span.rag.stage = "rerank" && span.prompt.id = "rag.reranker" }
```

Find RAG failures:

```traceql
{ name =~ "rag\\..*" && status = error }
```

Expected bounded tags:

- `rag.stage`: `hyde`, `vector_search`, `keyword_search`, `rrf_merge`, `candidate_fetch`, `rerank`, `final_context`, or `fallback`
- `rag.source`: fixed backend/source identifiers only
- `rag.hit_bucket`: `0`, `1-3`, `4-10`, `11+`

## Memory Queries

Find memory context building:

```traceql
{ span.memory.operation = "build_context" }
```

Find wiki merge prompt usage:

```traceql
{ span.prompt.id = "memory.wiki.merge" || span.prompt.id = "memory.wiki.first_extract" }
```

Find compression spans:

```traceql
{ name = "memory.compress" }
```

Find memory failures:

```traceql
{ name =~ "memory\\..*" && status = error }
```

`memory.compress` is expected to be a root span because compression runs as a fire-and-forget async task. Treat that as expected unless a future PR adds explicit correlation.

Expected bounded tags:

- `memory.operation`: fixed operation identifiers only
- `memory.store`: `redis`, `repository`, or `mixed`
- `memory.size_bucket`: `0`, `1-2`, `3-10`, `11-20`, `21+`
- `memory.role`: `user`, `assistant`, `system`, or `other`

## WebSocket and ReAct Queries

Find WebSocket message handling:

```traceql
{ name = "ws.message.handle" && span.ws.handler = "emotion" }
```

Find ReAct runs:

```traceql
{ name = "react.run" }
```

Find tool execution spans:

```traceql
{ name = "tool.execute" }
```

Expected WebSocket/ReAct shape:

```text
ws.message.handle
  -> memory.add_message
  -> react.run
    -> react.phase1
    -> tool.execute
    -> react.phase2
  -> memory.add_message
```

## Empty Result Triage

If a query returns no traces:

1. Confirm the request was triggered after the stack started.
2. Confirm `TRACING_SAMPLE_RATE=1.0` reached the app container.
3. Confirm Tempo is healthy and Grafana's `Tempo` datasource points to `http://tempo:3200`.
4. Broaden the query to `{ }` over the last 15 minutes to check whether any traces exist.
5. Search by span name only before adding `span.*` attribute filters.
6. If only error panels are empty, check whether there were actually failures; an empty error panel can mean zero errors.

## What to Record

When the local runtime run is complete, record:

- commit SHA
- request path and scenario
- trace ID or TraceQL query
- whether the expected parent-child shape appeared
- any root spans that were expected, especially `memory.compress`
- any unexpected orphan spans
- screenshots or notes for dashboard panels that were empty
