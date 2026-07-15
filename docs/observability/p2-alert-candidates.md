# P2 Alert Candidates

Date: 2026-07-06
Base: `origin/main` after PR #81

## Purpose

This document captures alert candidates for the P1 observability surface. These are not enabled alert rules. Do not provision or enable them until a Docker-backed runtime validation record exists and the team has at least one baseline window for normal traffic.

## Ground Rules

- Keep alerts tied to existing dashboard queries where possible.
- Use `application` as the top-level filter, matching the Grafana dashboard variable.
- Prefer ratios or per-stage rates over raw counts.
- Do not alert on empty error panels; empty can mean zero errors.
- Do not page on latency until the threshold is based on observed baseline data.
- Keep candidate labels bounded: `node.name`, `rag.stage`, `memory.operation`, `prompt.id`, and `status`.

## Required Baseline Before Enabling

Record these in the runtime validation note before turning any candidate into a real alert:

- commit SHA and deployment profile
- `TRACING_SAMPLE_RATE` used for validation
- normal HTTP request rate
- HTTP p95 and average latency for a short baseline window
- graph latency avg/max during L3/L4 requests
- RAG stage latency for `hyde`, `vector_search`, `keyword_search`, `rerank`, and `final_context`
- memory operation latency for the exercised operations
- whether error-rate panels are empty because there were no errors or because labels did not match

## Candidate Queries

Replace `Ling-innerflow` with the selected dashboard `application` value when testing directly in Prometheus.

The examples below use explicit `[5m]` windows because alert rules need fixed evaluation windows. Dashboard panels should continue using Grafana's `$__rate_interval`.

### HTTP 5xx Ratio

Visibility source: `HTTP 5xx Ratio` panel.

```promql
sum(rate(http_server_requests_seconds_count{application="Ling-innerflow",status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{application="Ling-innerflow"}[5m]))
```

Candidate behavior:

- warn when ratio stays above the observed baseline for 5 minutes
- page only after a baseline-backed threshold exists and false positives are reviewed

### HTTP p95 Latency

Visibility source: `HTTP Latency` panel.

```promql
histogram_quantile(
  0.95,
  sum by (le) (
    rate(http_server_requests_seconds_bucket{application="Ling-innerflow"}[5m])
  )
)
```

Candidate behavior:

- warn when p95 stays above the baseline envelope for 10 minutes
- do not use this as an SLA until enough samples exist

### Emotion Graph Latency

Visibility source: `Emotion Graph Latency` panel.

```promql
sum(rate(emotion_graph_invoke_seconds_sum{application="Ling-innerflow"}[5m]))
/
sum(rate(emotion_graph_invoke_seconds_count{application="Ling-innerflow"}[5m]))
```

Candidate behavior:

- warn on sustained graph average latency above baseline
- inspect Tempo for child spans before assuming the graph itself is slow

### RAG Stage Latency

Visibility source: `RAG Stage Latency` panel.

```promql
sum by (rag_stage) (
  rate({__name__=~"rag_.*_seconds_sum",application="Ling-innerflow",rag_stage=~".+"}[5m])
)
/
sum by (rag_stage) (
  rate({__name__=~"rag_.*_seconds_count",application="Ling-innerflow",rag_stage=~".+"}[5m])
)
```

Candidate behavior:

- warn when `rerank`, `vector_search`, or `hyde` exceeds its baseline envelope
- keep stages separate; do not collapse all RAG stages into one alert

### Node Error Rate

Visibility source: `Node Error Rate by node.name` panel.

```promql
sum by (node_name, error) (
  rate({__name__=~"node_.*_seconds_count",application="Ling-innerflow",node_name=~".+",error=~".+",error!="none"}[5m])
)
```

Candidate behavior:

- warn on any sustained non-zero node error rate during normal traffic
- verify the failure in Tempo before escalating

### RAG Error Rate

Visibility source: `RAG Error Rate by rag.stage` panel.

```promql
sum by (rag_stage, error) (
  rate({__name__=~"rag_.*_seconds_count",application="Ling-innerflow",rag_stage=~".+",error=~".+",error!="none"}[5m])
)
```

Candidate behavior:

- warn on sustained errors in `vector_search`, `keyword_search`, `rerank`, or `final_context`
- keep fallback behavior in mind: some RAG failures may degrade gracefully

### Memory Error Rate

Visibility source: `Memory Error Rate by memory.operation` panel.

```promql
sum by (memory_operation, error) (
  rate({__name__=~"memory_.*_seconds_count",application="Ling-innerflow",memory_operation=~".+",error=~".+",error!="none"}[5m])
)
```

Candidate behavior:

- warn on sustained memory persistence or context-building errors
- treat `memory.compress` separately because it runs async and can appear as a root trace

### Prompt Cardinality Watch

Visibility source: `Prompt Usage by prompt.id` panel.

```promql
count(
  count by (prompt_id) (
    {__name__=~"(emotion_graph_invoke|node_.*|rag_.*|memory_.*|emotion_log|react_.*|tool_execute|ws_message_handle)_seconds_count",application="Ling-innerflow",prompt_id=~".+"}
  )
)
```

Candidate behavior:

- warn if the number of prompt IDs grows beyond the fixed prompt inventory
- investigate immediately if prompt IDs contain user text, retrieved content, or model output

## Enablement Checklist

Before converting a candidate into a provisioned Grafana alert rule:

- runtime validation record exists
- query returns expected data in local Grafana/Prometheus
- threshold is tied to a recorded baseline, not a guess
- alert has a documented owner and response action
- alert does not page on expected empty panels or sampling gaps
- related Tempo drilldown query is documented in `docs/observability/p2-trace-drilldown.md`

## Deferred Work

- Provision disabled Grafana alert rules after baseline data exists.
- Add dashboard links from alert panels to Tempo queries after runtime validation confirms attribute names.
- Add production-specific thresholds only after production-like traffic has been observed.
