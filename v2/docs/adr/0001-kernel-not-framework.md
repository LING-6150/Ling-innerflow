# ADR 0001 — InnerFlow v2 is a Memory/Safety Kernel, not an agent framework

- Status: Accepted
- Date: 2026-06
- Deciders: project owner + design review (Codex)

## Context

The author's portfolio already has two related projects:

- **ling-ai-generation-engine** (Java) — multi-agent orchestration (LangGraph4j) +
  production system.
- **llm-codegen-eval** (Python) — an eval framework for the above.

The original Java **InnerFlow** is, in capability and stack, a near-twin of
ling-ai-generation-engine (same Java/Vue stack, same LangGraph4j multi-agent
orchestration, same streaming/tools/observability). Two near-identical
"orchestration + production" projects is redundant signal for hiring.

## Decision

Re-conceive InnerFlow v2 as a **Memory/Safety Kernel** in **Python**, not as a
port of the Java app and not as another orchestration framework. It occupies a
distinct cell: different capability (long-horizon memory + verifiable safety,
which the code-gen agent never has to solve), different stack (Python).

Two consequences locked here:

1. **Own the boundary, not a framework's surface.** Business logic faces our own
   `Agent / Tool / Memory / SafetyGuard / Event / Session`, not a framework's
   node/edge/checkpoint API. A framework (e.g. LangGraph Python) may later serve
   as an *internal* runtime only. (This mirrors the author's own OpenMAIC SDK
   research conclusion; principle reused, no employer content copied.)
2. **Measure before building.** The first PR ships interfaces + eval fixtures +
   metric definitions + tests, and explicitly *no* agent loop / LangGraph /
   FastAPI / LLM / vector DB. The claims must be testable before the runtime is
   built.

## Alternatives considered

- **1:1 Java→Python port of InnerFlow.** Rejected: it recreates the twin in a new
  language — translation effort, not differentiation, and low signal.
- **Keep Java, re-brand as "agent orchestration".** Rejected: collides head-on
  with ling-ai-generation-engine.
- **Lead with eval.** Rejected as the *headline*: llm-codegen-eval already owns
  "eval depth"; here eval is a supporting chapter, not the brand.

## Consequences

- Memory and safety logic are hand-written (the contribution / mechanism-depth
  signal), not delegated to a framework.
- The deterministic SafetyGuard is a fail-safe *floor*, intentionally incomplete;
  its bypass/false-positive rates are measured to motivate a later context layer.
- A real agent runtime, retrieval, and LLM integration are deferred to later PRs,
  each gated by the metrics defined here.
