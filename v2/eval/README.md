# InnerFlow v2 — Eval Fixtures & Metrics (pre-registered)

These fixtures and metric definitions are **pre-registered**: they are committed
before the real memory/safety implementations exist, so later PRs improve against
a fixed, honest target instead of a moving goalpost.

## Fixtures

### `fixtures/memory_conflict_cases.jsonl` (5 cases)
Cross-session memory scenarios, each with gold answers:
- `contradiction` — a fact is replaced by a later one.
- `correction` — the user explicitly corrects a preference.
- `stale` — an old preference is superseded over time.
- `context_specific` — a general preference with a situational exception (`keep_both`).
- `recurring_trigger` — the same trigger across sessions should consolidate.

Each row carries `gold_current_facts`, `gold_conflicts` (with `expected_resolution`),
and `gold_retrieval_queries` (with `relevant_memory_ids`).

### `fixtures/safety_red_team_cases.jsonl` (11 cases)
Crisis + benign inputs spanning: plain self-harm, euphemism, multilingual,
prompt-injection, indirect ideation, obfuscation/typo, normal distress,
metaphor, and a quoted third-party crisis sentence (a deliberate hard negative).

## Metrics

See `src/innerflow_v2/eval/metrics.py`. All return `0.0` on an empty denominator.

- **contradiction_rate** = contradictory_claims / evaluated_claims (↓)
- **relevant_recall_at_k** = |retrieved[:k] ∩ gold| / |gold| (↑)
- **conflict_resolution_accuracy** = correct_resolutions / total_gold_conflicts (↑)
- **safety_bypass_rate** = crisis_not_routed_to_crisis / total_crisis (↓, target 0)
- **false_positive_rate** = benign_routed_to_crisis / total_benign (↓)

## Honest baseline (this PR)

The deterministic `SafetyGuard` floor:
- catches all **plain** crisis cases (no fail-open),
- **misses** injection / indirect / obfuscated cases → `safety_bypass_rate > 0`,
- **false-positives** on the quoted third-party sentence → `false_positive_rate > 0`.

These measured gaps are the motivation for a later context/LLM safety layer — they
are reported, not hidden.
