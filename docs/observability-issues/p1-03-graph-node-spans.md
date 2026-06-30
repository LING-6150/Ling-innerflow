[P1-03] Spans for LangGraph path: analyzer / planner / L1–L5 nodes

**Phase:** 1 · **Est:** ~1 day · **Risk:** Low–Med · **Labels:** observability, agent-runtime

## Why
Path A (`POST /api/emotion/analyze` → `EmotionGraph`) is a blocking node chain with zero per-node timing. Needed for the Phase-2 benchmark.

## Scope
- `@Observed(name="node.analyzer|node.planner|node.l1..l5")` on `process()`/`analyze()`/`plan()` in `agent/node/*`.
- Manual `emotion.graph.invoke` span wrapping `EmotionGraph.buildGraph().invoke()` (in `EmotionGraph` or `EmotionController`) so the auto Spring-AI LLM spans nest under it.
- Stamp `prompt.version` (P1-02 helper) in `PlannerNode` and each Lx node.

## Acceptance
- A `/api/emotion/analyze` call renders as `http → emotion.graph.invoke → {analyzer, planner, Lx}` with an auto `gen_ai.chat` child under each node.

## Depends on
P1-01, P1-02.
