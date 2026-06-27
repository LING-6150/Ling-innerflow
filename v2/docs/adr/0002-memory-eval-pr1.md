# ADR 0002 — Stage 2 PR-1: measure memory before building it

- Status: Accepted
- Date: 2026-06
- Deciders: project owner + design review (Codex)

## Context

Stage 2 must show that explicit cross-session **consolidation + conflict
resolution** beats naive memory, on the pre-registered metrics. The risk
(flagged in review) is producing a false-positive result: weak baselines plus a
kernel that wins only because it memorized the fixtures.

## Decision

1. **Observations-as-input for PR-1.** Systems ingest a structured observation
   stream (semantic_key + content + optional context), NOT raw messages.
   Message→observation extraction needs an LLM (PR-2); doing it deterministically
   would be fake. PR-1 isolates and measures consolidation / conflict resolution
   / retrieval. The observation stream is INPUT; gold (which is current, how
   conflicts resolve, what is relevant) is held only by the evaluator — systems
   never see gold or `case_id`.

2. **Anchor metrics on a closed `semantic_key` schema.** Contradiction is
   compared within a semantic_key against the gold effective observation-id set;
   the denominator is all gold facts (a missing claim is a contradiction, so an
   empty profile scores worst, not best). Conflicts match by observation-id pair,
   not free text. (Both were review findings.)

3. **Add `B-latest-by-key` (last-write-wins) as the discriminating baseline,**
   plus `B-extract-only`. The kernel must beat last-write-wins on
   context-specific exceptions (keep_both) and on historical retrieval — proving
   its value is more than "keep the newest".

4. **PR-1 is a deterministic diagnostic floor, not a proof.** Fixtures are split
   dev/locked and pre-registered (authored before kernel rules; kernel rules are
   generic — kind-based accumulate-vs-replace + context-aware keep_both — not
   tuned by peeking at failures). PR-2 (LLM/embedding) may not change locked
   fixtures, only add new splits.

## Alternatives considered

- **Deterministic extraction from raw messages in PR-1.** Rejected: hand-rules
  overfit and would make the result meaningless.
- **Only B-full / B-rag baselines.** Rejected: neither is the cheap-but-strong
  competitor; without last-write-wins, a kernel "win" proves little.

## Consequences

- The `context` field is an explicit input feature standing in for what PR-2's
  LLM judge infers from content — documented as a floor, not the end state.
- The deterministic result (see `eval/RESULTS_MEMORY.md`) shows the kernel as the
  only system strong on every column; each baseline fails for a principled,
  documented reason. PR-2 must reproduce this with real extraction/judgment.
