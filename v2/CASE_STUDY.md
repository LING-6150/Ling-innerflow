# InnerFlow v2 — Case Study

A short, honest account of why this kernel exists and what it demonstrates.

## The 30-second version

I had a Java mental-health agent app that was, in capability and stack, a near
duplicate of my own code-generation agent project — same Java/Vue stack, same
LangGraph orchestration. Two near-identical "orchestration" projects is weak
portfolio signal. So I re-conceived InnerFlow as a Python **Memory/Safety
Kernel**, focused on the two hard problems a companion agent has that a code
generator never does: **cross-session memory consolidation + conflict
resolution**, and a **non-bypassable, verifiable safety floor**. I built it
**eval-first** — metrics and adversarial fixtures pinned *before* the
implementation — and I honestly measured where an LLM still falls short.

## The arc

1. **Diagnose.** Reviewing my own Java app as an interviewer would, I found (a) a
   fail-open safety bug — crisis severity came from an LLM integer; a malformed
   response silently defaulted to the *lowest* level — and (b) the strategic
   "twin project" problem above.
2. **Stop the bleeding.** Made crisis detection fail-*safe* (deterministic
   keyword net + `max(LLM, keyword)`), plus P0/P1 fixes (resource leak, unmanaged
   thread, swallowed exceptions). Shipped with TDD.
3. **Reposition.** Rebuilt the core in Python around memory + verifiable safety,
   so it no longer duplicates the code-gen project.
4. **Build eval-first.** Defined metrics (contradiction rate, conflict-resolution
   accuracy, retrieval recall, safety bypass/FP) and adversarial fixtures, then
   the implementation. Deterministic baselines first (to get a discriminator
   matrix), then a live LLM kernel.
5. **Report honestly.** Embedding retrieval is strong; inferring structure from
   free text is harder — and a precision guard I added *before* wiring the LLM
   immediately caught it over-emitting. I wrote the gap down as the spec for next
   work rather than hiding it.

## Key technical details

### Safety: from fail-open to a verifiable invariant
`SafetyGuard` is a deterministic floor that is **structurally non-bypassable**
(every input is checked before any LLM/tool step) and **fail-safe** (never
downgrades a detected signal). It has **zero bypass on the pre-registered
red-team set** using generalizable techniques (leetspeak normalization + real
crisis vocabulary), **not** memorized test strings. It is explicitly *not*
complete — a quoted third-party crisis sentence trips a *measured* false
positive (`false_positive_rate ≈ 0.25`); that residual is the spec for a context
layer, stated rather than hidden.

### Memory: an eval-first discriminator matrix
Five systems implement one `MemorySystem` interface: `B-full`, `B-rag`,
**`B-latest-by-key`** (last-write-wins — the cheap-but-strong baseline the kernel
must beat), `B-extract-only`, and the kernel. Metrics are anchored on a closed
`semantic_key` schema; a missing claim counts as a contradiction (so an empty
profile can't game the denominator); conflicts match by observation-id pair.
Systems receive only observations — gold is held by the evaluator.

The deterministic kernel (context-aware keep-both + kind-based
accumulate-vs-replace, history retained) is the **only** system strong on every
column: last-write-wins loses context-specific exceptions *and* historical
recall; B-full keeps stale facts as current; B-rag builds no profile.

### LLM kernel + the precision guards that earned their keep
`KernelLLM` infers `semantic_key` + context from text (closed vocabulary), judges
conflicts via an LLM, and retrieves via embeddings — scored against the same gold.
Live (gpt-4o-mini + text-embedding-3-small): retrieval recall ≈ 1.0, but
inference is genuinely harder than being handed the fields — contradiction rate
0.0→0.2, conflict accuracy 1.0→0.6, and the `extra_claim_rate` guard caught the
LLM over-emitting (0.077). Parsing fails safe (bad JSON / shapes → empty), and a
hallucinated observation id drops the whole claim rather than being laundered
into a partially-correct one.

## How it was built (process is part of the point)
Spec-first (a design doc per stage), pre-registered success criteria, TDD, an
ADR per real decision, and an adversarial external review of every PR — accepted
with technical judgment, not blindly (e.g. I refused to make safety "pass" by
adding fixture-specific keywords, which would be false safety / overfitting).

## What this is not (stated up front)
A kernel + eval harness, not a finished product: no agent runtime or production
API yet. The locked eval split is small (honest, small-N caveat). Message→
observation extraction and prompt/inference tuning are deliberately deferred.
