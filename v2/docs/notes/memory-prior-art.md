# Memory prior art — Letta / MemGPT (and where InnerFlow v2 differs)

Notes only (no dependency, no framework). Positions the memory kernel against
the best-known memory-centric agent system so the contribution is "standing on
known work", not reinventing it.

## MemGPT / Letta (what they do)

- Treat the LLM context window as paged virtual memory: a hierarchy of
  **in-context (working)** vs **out-of-context (archival/recall)** memory.
- The agent **self-edits** memory via tools (write/append/replace), deciding
  what to keep in limited context.
- Strong at: long-horizon recall, letting the agent manage its own memory.

## Where InnerFlow v2 focuses (the gap we test)

MemGPT/Letta emphasize *storage tiers + self-editing recall*. They do not make a
measurable claim about **cross-session consolidation consistency**:

- **Conflict resolution**: when a later observation contradicts an earlier one,
  is the resulting profile self-consistent? (supersede vs keep-both-by-context).
- **Consolidation of recurrence** without treating it as contradiction.
- **Not collapsing to last-write-wins** (our `B-latest-by-key` baseline) — and
  not losing historical/exception facts.

InnerFlow v2's contribution is to make these **measurable** (contradiction_rate,
conflict_resolution_accuracy, historical recall) against explicit baselines —
including a last-write-wins baseline — rather than only demonstrating "it
remembers".

## Honest caveats

- We are not reimplementing MemGPT's paging; retrieval here is deliberately
  simple (lexical floor in PR-1, embeddings in PR-2).
- The claim is narrow and eval-anchored, not "better memory agent than Letta".
