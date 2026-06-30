[P1-01] Add OpenTelemetry deps + OTLP collector + Grafana/Tempo

**Phase:** 1 · **Est:** ~1 day · **Risk:** Low · **Labels:** observability, infra

## Why
We have `micrometer-registry-prometheus` but **no tracing**. Spring AI 1.0 auto-emits `gen_ai.*` spans (model, tokens, finish reason) as soon as a `Tracer` bean exists — so this issue unlocks most of the cost/model gap with no call-site changes.

## Scope
- `pom.xml`: add `io.micrometer:micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`, `io.micrometer:context-propagation`.
- `application.properties` / `-local` / `-prod`: enable management in local profile; set `management.tracing.sampling.probability` (1.0 local/eval, 0.1 prod) and `management.otlp.tracing.endpoint`.
- `docker-compose.yml`: add an OTLP collector + trace backend (Tempo or Jaeger). Reuse the already-provisioned Grafana datasource dir.

## Acceptance
- App boots with a `Tracer` bean present; a sample HTTP request produces a trace visible in the backend.
- No behavior change to request handling.

## Notes
Verify streaming LLM spans emit token usage on the final chunk (see P1-06).
