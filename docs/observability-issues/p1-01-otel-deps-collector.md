[P1-01] Add OpenTelemetry deps + OTLP collector + Grafana/Tempo

**Phase:** 1 · **Est:** ~1 day · **Risk:** Low · **Labels:** observability, infra

## Why
We have `micrometer-registry-prometheus` but **no tracing**. Spring AI 1.0 can emit `gen_ai.*` observations/spans once a `Tracer` bean exists, so this issue should unlock model metadata and much of the token/cost gap with minimal call-site changes.

## Scope
- `pom.xml`: add `io.micrometer:micrometer-tracing-bridge-otel`, `io.opentelemetry:opentelemetry-exporter-otlp`, `io.micrometer:context-propagation`.
- `application.properties` / `-local` / `-prod`: enable management in local profile; set `management.tracing.sampling.probability` (1.0 local/eval, 0.1 prod) and `management.otlp.tracing.endpoint`.
- `docker-compose.yml`: add an OTLP collector + trace backend (Tempo or Jaeger). Reuse the already-provisioned Grafana datasource dir.

## Acceptance
- App boots with a `Tracer` bean present; a sample HTTP request produces a trace visible in the backend.
- A sample blocking LLM call is checked for `gen_ai.*` model and usage attributes; if token usage is absent, document the exact gap for P1-02/P1-06 instead of assuming cost metrics are complete.
- No behavior change to request handling.

## Notes
Verify streaming LLM spans emit token usage on the final chunk (see P1-06).
