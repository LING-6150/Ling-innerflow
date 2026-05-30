# PE-0 — Epic: Pattern Engine V1.2 — finish line (eval + tests + Tier A-H)

## Goal

Bring Pattern Engine V1.2 from "compiles" to "ships behind a flag with green eval + tests."
The implementation foundation, retrieval, verification, scoring, and orchestration layers
are already committed (`0f034c2`, `43d79a9`). What remains is **the evaluation layer that
proves it works** + **unit tests that lock the invariants** + **the one piece of data that
can only be authored by a human**.

## Specs (frozen)

- `docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md` — product spec
- `docs/superpowers/specs/2026-05-29-pattern-engine-v1.md` — engine design
- `docs/superpowers/specs/2026-05-29-pattern-engine-v1.1.md` — P0 fixes
- `docs/superpowers/specs/2026-05-29-pattern-engine-v1.2.md` — **FINAL; conflicts → V1.2 wins**
- `docs/ARCHITECTURE_REVIEW.md` — reuse memory/RAG without inheriting clinical framing
- `docs/HANDOFF_pattern_engine.md` — what's done, what's left, contracts
- `docs/issues/README.md` — multi-terminal operations manual (READ THIS FIRST)

## Branch model

All sub-PRs target `epic/pattern-engine-v1.2`. When the Epic is green, one final PR squashes
into `main`. Each sub-issue spins up its own worktree via `docs/issues/spawn-worktree.sh`.

## Sub-issues

### Lane A — Eval framework (blocks lane B)
- [ ] **PE-1** Eval harness: `GroundTruthLoader` + `MetricsCalculator` + R40 recall-retention

### Lane B — Baselines (parallel after PE-1)
- [ ] **PE-2** Baseline B0 (Prior / chance level)
- [ ] **PE-3** Baseline B1 (Lexical / BM25 — **headline floor**, V1.2 R30)
- [ ] **PE-4** Baseline B2 (Single-prompt LLM, network-gated)
- [ ] **PE-5** Baseline B3 (Retrieval-no-verify — isolates verifier value)

### Lane C — Unit tests (always parallel)
- [ ] **PE-6** Test: `PatternDefinitionLoader` (12 keys, all valid)
- [ ] **PE-7** Test: `ConfidenceScorer` (no strength term, formula correctness)
- [ ] **PE-8** Test: `EvidenceChainAssembler` (4 invariants)
- [ ] **PE-9** Test: `PatternDeduplicator` (0.88 threshold, cross-key only)

### Lane D — Data (human-only, NO AI)
- [ ] **PE-10** Tier A-H human held-out personas (≥ 5)

## V1.2 hard rules carried in code (do not relitigate)

- Closed-set: exactly 12 `pattern_key`, 6 `domain`.
- Evidence chain: ≥ 3 items, ≥ 1 verbatim; verbatim span must be exact substring of source.
- Confidence formula has **no LLM strength term** (R13): `0.50·Evidence + 0.30·Recurrence + 0.20·Recency`.
- Verifier mode is whole-system BATCH or SINGLE_ITEM (R16′). No per-item shuffle-and-drop.
- Crisis-flagged source docs may never become evidence.
- Only `role='user'` chat turns are evidence-eligible.
- Per-pattern thresholds fit on Tier A, **collapse-checked on Tier A-H** (R37/R38).
- Tier A-H is sealed: never used for tuning. Only directional confound-falsification (R5, R34, R36).

## Exit criteria

- All 10 sub-PRs merged into `epic/pattern-engine-v1.2`.
- `./mvnw clean test` green (pattern tests + harness offline-runnable).
- Eval harness can score B0/B1 against Tier A end-to-end (B2/B3 may stay network-gated).
- A short results table (Tier A | Tier A-H | gap) committed to `eval/RESULTS.md`.
