[P1-07] Grafana dashboards + trace-presence verification

**Phase:** 1 · **Est:** ~1 day · **Risk:** Low · **Labels:** observability, infra, test

## Why
Make the new telemetry visible and regression-proof.

## Scope
- Commit a Grafana dashboard JSON: per-step p50/p95 latency, cost-per-conversation, tokens-per-step, fallback/degradation rate, TTFT (WS path).
- Add a verification test (extend `ReActStreamingVerificationTest` / `RAGQualityVerificationTest`) asserting a trace with the expected span names is produced.
- **Fix CI gap:** CI currently runs `mvn package -DskipTests` — make the verification job actually run these tests.

## Acceptance
- Dashboard renders from committed JSON.
- A failing instrumentation (missing span) fails the test.

## Depends on
P1-03..P1-06.
