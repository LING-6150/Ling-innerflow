[P1-02] ObservabilityConfig: ObservedAspect, reactor context propagation, cost handler, prompt-version helper

**Phase:** 1 · **Est:** ~1 day · **Risk:** Low · **Labels:** observability

## Why
Central wiring so the rest of the spans (P1-03..06) work, plus the two cross-cutting concerns the system lacks: **cost** and **prompt version**.

## Scope (new file `config/ObservabilityConfig.java`)
- `ObservedAspect` bean so `@Observed` works on graph nodes / services.
- `Hooks.enableAutomaticContextPropagation()` for reactive (`ReActAgent`) context flow.
- Custom `ObservationHandler` that reads Spring AI `gen_ai.usage.input_tokens` / `output_tokens` and increments a cost `Counter` via a static `gpt-4o-mini` pricing map (tokens → $).
- Helper to stamp `prompt.id` + `prompt.version` as span attributes (new convention — there is no prompt versioning today).

## Acceptance
- `@Observed` produces spans.
- An LLM call yields a span carrying token counts and a derived cost metric.
- A prompt builder can stamp `prompt.version` onto the active span.

## Depends on
P1-01.
