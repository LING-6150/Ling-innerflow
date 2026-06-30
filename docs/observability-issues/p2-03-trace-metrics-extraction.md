[P2-03] Extract benchmark metrics from traces

**Phase:** 2 · **Est:** ~1–1.5 days · **Risk:** Med · **Labels:** eval, benchmark, observability

## Why
Turn tagged traces (P2-02) into comparable numbers.

## Scope
Per runtime, aggregate from traces: TTFT (ReAct), end-to-end latency p50/p95, #LLM calls, total tokens, $/conversation, tool-dispatch overhead, fallback/failure rate. Specifically validate the speculative-tool-dispatch claim in `ReActAgent` comments (TTFT 2.8s→0.9s) with real data.

## Acceptance
- A reproducible step outputs a per-runtime metrics table from the trace backend.

## Depends on
P2-02.
