# P1 Observability Dashboard

This dashboard is the first Grafana surface for the P1 instrumentation added in PRs #71-#77.

It is intentionally a provisioning skeleton plus a small set of high-signal panels. The goal is to verify that the emitted metrics line up with the span and tag design before adding alert rules or more detailed drilldowns.

## Files

- `grafana/provisioning/dashboards/innerflow.yml`
- `grafana/provisioning/dashboards/innerflow/innerflow-p1-observability.json`

Grafana already mounts `./grafana/provisioning` into `/etc/grafana/provisioning`, so the dashboard loads automatically when the `grafana` service starts.

The `application` variable is populated from Prometheus via `label_values(http_server_requests_seconds_count, application)`. The default current value remains `Ling-innerflow`, matching `spring.application.name` today, but the variable will refresh if the application label changes.

## Panels

The dashboard covers:

- HTTP request rate, p95 latency, and 5xx ratio
- `emotion.graph.invoke` latency
- `node.*` latency and error rate grouped by `node.name`
- RAG stage latency, throughput, and error rate grouped by `rag.stage`
- memory operation latency and error rate grouped by `memory.operation`
- prompt usage grouped by `prompt.id` from known P1 observation timer families

HTTP p95 uses the existing `http.server.requests` histogram. The custom P1 observation timers currently use avg/max style panels because custom histogram buckets are not enabled for every observation family yet.

Rate queries use Grafana's `$__rate_interval` so the window adapts to the selected dashboard time range. Error-rate panels count observation timer series that expose a non-`none` `error` label; verify label shape during runtime validation before turning any of these into alert rules.

## Trace Drilldown

Use Tempo Explore for trace drilldown. Useful filters:

- `node.name="l3"` or `node.name="l4"`
- `rag.stage="rerank"`
- `memory.operation="build_context"`
- `prompt.id="rag.reranker"`
- `prompt.id="memory.wiki.merge"`
- `emotion.source="chat"`

Run trace verification with the eval profile or `TRACING_SAMPLE_RATE=1.0`; the default sample rate is `0.1`.

For copyable TraceQL queries and empty-result triage, see `docs/observability/p2-trace-drilldown.md`.

## Alerting Candidates

Do not enable these as hard alert rules until the dashboard has a baseline window. Use them as candidates for the next PR:

- HTTP 5xx ratio above baseline for 5 minutes
- HTTP p95 latency above baseline for 10 minutes
- `emotion.graph.invoke` avg/max latency above baseline
- RAG rerank or vector search latency above baseline
- memory persistence failures or high memory operation latency
- unexpected growth in metric series involving `prompt.id`, `rag.stage`, `node.name`, or `memory.operation`

## Acceptance

Before calling the dashboard ready:

- Start the stack with Grafana, Prometheus, Tempo, and the app.
- Trigger an L3/L4 `/api/emotion/analyze` request with sampling set to `1.0`.
- Confirm HTTP, graph, RAG, memory, and prompt panels show data.
- Open Tempo Explore and confirm the trace shape from `docs/observability/p1-acceptance.md`.
