[P2-04] Benchmark report + comparison dashboard

**Phase:** 2 · **Est:** ~1.5 days · **Risk:** Low · **Labels:** eval, benchmark, docs

## Why
Publish the result — this is the resume centerpiece.

## Scope
- `docs/benchmark-results.md`: TTFT / latency / tokens / cost / failure-rate tables for both runtimes on the golden set, + written trade-off analysis (LangGraph4j orchestration overhead & ergonomics vs hand-rolled ReAct control & TTFT; when each wins).
- A Grafana board overlaying the two runtimes.
- State comparison boundaries (streaming vs blocking; feature asymmetry) explicitly.

## Acceptance
- Report reproducible via one command, backed by traces; numbers match P2-03.

## Depends on
P2-03.
