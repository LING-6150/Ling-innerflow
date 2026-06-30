[P2-02] Dual-runtime benchmark harness (ReAct vs LangGraph4j)

**Phase:** 2 · **Est:** ~1.5–2 days · **Risk:** Med · **Labels:** eval, benchmark, agent-runtime

## Why
Drive the same golden scenarios (P2-01) through both runtimes already in the repo — `ReActAgent.runStreaming` (Path B) and `EmotionGraph.invoke` (Path A) — under full tracing.

## Scope
- A runner (test or CLI) iterating scenarios × {react, langgraph}.
- 100% trace sampling on an `eval` profile.
- Tag every run with `runtime=react|langgraph`, `scenario.id`, `prompt.version`.
- Handle the streaming-vs-blocking asymmetry: capture TTFT for ReAct, total latency for both.

## Acceptance
- One command runs all scenarios through both runtimes and produces tagged traces.

## Depends on
P1 complete, P2-01.

## Note
The two runtimes are not feature-identical (ReAct streams + speculative tools; graph is blocking + fixed routing). The harness records the asymmetry; the report (P2-04) states comparison boundaries honestly.
