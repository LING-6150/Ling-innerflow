[P1-06] Spans for ReAct runtime + WebSocket root (reactive context propagation) — SPIKE FIRST

**Phase:** 1 · **Est:** ~1.5–2.5 days · **Risk:** HIGH · **Labels:** observability, agent-runtime, streaming

## Why
This is the riskiest and highest-value span work, and the canary for the whole effort. `ReActAgent.runStreaming` uses `Flux.create` + `Schedulers.boundedElastic().schedule(...)`, subscribes Phase-2 on another thread, and dispatches tools as `CompletableFuture.supplyAsync`. WebSocket frames are **not** auto-instrumented, so there is no ambient trace. Done wrong → orphan spans.

## Scope
- **Spike first** on `ReActAgent` alone: prove a span opened on the request thread is current inside the `boundedElastic` callback, the Phase-2 subscribe, and the tool `CompletableFuture` (needs `Hooks.enableAutomaticContextPropagation()` from P1-02 + explicit context capture/restore).
- Manual **root span** `ws.message.handle` in `EmotionWebSocketHandler.handleTextMessage`, closed in the stream's `doFinally` (NOT at method return — the method returns while the `Flux` is still streaming).
- `ReActAgent` spans: `react.run`, `react.phase1[round]`, `tool.<name>` (spans the CompletableFuture), `react.phase2`.
- Verify streaming `gen_ai` spans emit token usage on the final chunk; if not, keep the trace connected and record an explicit usage-capture follow-up instead of fabricating cost.
- Convert the existing ad-hoc `phase1Ms` / `toolFuture.join()` logs into span attributes (`react.ttft_ms`, `tool.join_wait_ms`).

## Acceptance
- A full WS conversation renders as ONE connected trace: `ws.message.handle → {analyzer, fusion, planner, react.run → {phase1, tool.x, phase2}}`, no orphans, with TTFT + tool-dispatch overhead as attributes.
- Streaming token/cost behavior is verified and documented separately from trace connectivity.

## Depends on
P1-01, P1-02.
