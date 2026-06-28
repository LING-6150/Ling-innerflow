# InnerFlow v2 — Memory/Safety Kernel

> **This is NOT an agent framework, and NOT a Python rewrite of Java InnerFlow.**

InnerFlow v2 is a small, eval-first **kernel** that isolates the two hard problems
a companion agent must solve and a code-generation agent never does:

1. **Cross-session memory** — consolidate observations across sessions and resolve
   contradictions, more consistently than full-history context or naive RAG-over-chat.
2. **Verifiable safety** — enforce crisis detection as a **structurally
   non-bypassable** check that runs before any LLM/tool/runtime step, is
   **fail-safe** (never downgrades a detected signal), and has **zero bypass on
   the pre-registered red-team set**. It is explicitly *not* a completeness
   guarantee — see below.

The discipline here is *measure before you build*: this first PR defines the
interfaces, eval fixtures, and metric definitions — with tests that lock the
intended behavior — **before** any agent loop, LangGraph, FastAPI, vector DB, or
LLM integration exists.

## What's in this PR (Stage 1)

- `MemoryKernel` / `SafetyGuard` / `TraceRecorder` interfaces (typed, Pydantic).
- `SafetyGuard` ships a **minimal but real** deterministic crisis floor: structurally
  non-bypassable, fail-safe, with **zero bypass on the current red-team fixtures**
  (generalizable leetspeak normalization + real crisis vocabulary, not memorized
  test strings). It is **not** complete — novel obfuscation / purely-contextual
  phrasing can still bypass a keyword floor, and a quoted third-party crisis
  sentence trips a *measured* false positive. Those residuals are the spec for a
  later context/LLM layer; the red-team set is the living coverage spec.
- Pre-registered eval fixtures: 5 memory-conflict cases + 11 safety red-team cases.
- Locked metric definitions + focused unit tests.

## What's deliberately NOT here

LangGraph, multi-agent orchestration, MCP/A2A/FHIR, multimodal, FastAPI app, UI,
vector DB, OpenTelemetry, full product conversation flow, Java feature migration.
(See `docs/adr/0001-kernel-not-framework.md`.) An LLM provider + embeddings ARE
used by the Stage 2 PR-2b memory kernel only (offline tests use a deterministic
fake; the live run is opt-in and key-gated).

## Metrics (pre-registered, see `eval/README.md`)

| metric | meaning | direction |
|---|---|---|
| `contradiction_rate` | profile claims conflicting with current gold facts | ↓ |
| `relevant_recall_at_k` | gold-relevant memories retrieved in top-k | ↑ |
| `conflict_resolution_accuracy` | resolutions matching gold | ↑ |
| `safety_bypass_rate` | crisis cases not routed to crisis | ↓ (target 0) |
| `false_positive_rate` | benign cases routed to crisis | ↓ |

## Stage 2 PR-1 — memory eval harness + baselines (the memory claim, measured)

Measures cross-session **consolidation + conflict resolution** against baselines,
on an observation stream (extraction is PR-2). See `docs/adr/0002-memory-eval-pr1.md`
and `docs/notes/memory-prior-art.md` (Letta/MemGPT contrast).

- Systems (`memory/systems.py`): `B-full`, `B-rag`, **`B-latest-by-key`** (the
  discriminating last-write-wins baseline), `B-extract-only`, `Kernel-deterministic`.
- Metrics anchored on a closed `semantic_key` schema; empty profile cannot game
  contradiction; conflicts matched by observation-id pair. Systems see only
  observations — gold is held by the evaluator (`eval/run_memory_eval.py`).
- 16 pre-registered fixtures (dev/locked) across 7 categories.
- Result (`eval/RESULTS_MEMORY.md`, deterministic floor — not a proof): the kernel
  is the only system strong on every column; last-write-wins loses keep_both +
  historical recall, B-full keeps stale facts, B-rag builds no profile. PR-2 must
  reproduce this with real LLM extraction/judgment + embeddings.

## Stage 2 PR-2b — LLM/embedding memory kernel (the real result)

`KernelLLM` (`memory/systems_llm.py`) infers `semantic_key` + context **from the
text** (constrained to the closed vocabulary), judges conflicts via an LLM, and
retrieves via embeddings — scored against the SAME locked gold + precision guards
+ baselines. Offline tests use a deterministic `FakeReasoner` (no tokens); the
live run is opt-in:

```bash
MY_OPENAI_KEY="$(cat ~/.innerflow_openai_key)" uv run python scripts/run_llm_eval.py
```

Live result (`eval/RESULTS_MEMORY_LLM.md`, gpt-4o-mini + text-embedding-3-small):
embedding retrieval is strong (recall ≈ 1.0 current & historical), but **inferring
key/context from free text is genuinely harder** than PR-1 (which was handed those
fields): the LLM kernel's contradiction_rate and conflict accuracy degrade versus
the deterministic kernel, and the **`extra_claim_rate` guard catches the LLM
over-emitting** (vindicating the PR-2a precision guards). That inference gap is
the spec for further work.

## Run

```bash
uv run pytest
```

(or `python -m pytest` inside a 3.11+ env with `pydantic` + `pytest`.)
