# InnerFlow v2 — Memory/Safety Kernel

> **This is NOT an agent framework, and NOT a Python rewrite of Java InnerFlow.**

InnerFlow v2 is a small, eval-first **kernel** that isolates the two hard problems
a companion agent must solve and a code-generation agent never does:

1. **Cross-session memory** — consolidate observations across sessions and resolve
   contradictions, more consistently than full-history context or naive RAG-over-chat.
2. **Verifiable safety** — enforce crisis detection as a **non-bypassable invariant**
   that runs before any LLM/tool/runtime step and never fails open.

The discipline here is *measure before you build*: this first PR defines the
interfaces, eval fixtures, and metric definitions — with tests that lock the
intended behavior — **before** any agent loop, LangGraph, FastAPI, vector DB, or
LLM integration exists.

## What's in this PR (Stage 1)

- `MemoryKernel` / `SafetyGuard` / `TraceRecorder` interfaces (typed, Pydantic).
- `SafetyGuard` ships a **minimal but real** deterministic crisis floor (fail-safe,
  never fail-open). Its recall gaps are *measured*, not hidden.
- Pre-registered eval fixtures: 5 memory-conflict cases + 11 safety red-team cases.
- Locked metric definitions + focused unit tests.

## What's deliberately NOT here

LangGraph, multi-agent orchestration, MCP/A2A/FHIR, multimodal, FastAPI app, UI,
LLM provider integration, vector DB, real embeddings, OpenTelemetry, full product
conversation flow, Java feature migration. (See `docs/adr/0001-kernel-not-framework.md`.)

## Metrics (pre-registered, see `eval/README.md`)

| metric | meaning | direction |
|---|---|---|
| `contradiction_rate` | profile claims conflicting with current gold facts | ↓ |
| `relevant_recall_at_k` | gold-relevant memories retrieved in top-k | ↑ |
| `conflict_resolution_accuracy` | resolutions matching gold | ↑ |
| `safety_bypass_rate` | crisis cases not routed to crisis | ↓ (target 0) |
| `false_positive_rate` | benign cases routed to crisis | ↓ |

## Run

```bash
uv run pytest
```

(or `python -m pytest` inside a 3.11+ env with `pydantic` + `pytest`.)
