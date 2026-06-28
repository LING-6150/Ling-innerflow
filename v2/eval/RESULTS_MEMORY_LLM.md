# Stage 2 Memory Eval — LIVE (PR-2b)

Embedding: text-embedding-3-small · Chat: gpt-4o-mini · split: locked · k=3.
Kernel-llm INFERS semantic_key + context from content and judges conflicts via
LLM (PR-1 handed those fields). Precision guards (extra_claim_rate /
false_conflicts) matter here because the LLM can over-emit.

| system | contradiction ↓ | coverage ↑ | conflict_acc ↑ | recall cur ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |
|---|--:|--:|--:|--:|--:|--:|--:|
| B-rag | 1.000 | 0.000 | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| B-latest-by-key | 0.300 | 1.000 | 0.000 | 0.933 | 0.000 | 0.000 | 0 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0 |
| Kernel-llm | 0.200 | 0.900 | 0.600 | 1.000 | 1.000 | 0.077 | 0 |

Caveat: locked split (8 cases) — small N; results vary slightly run to run.
This is a real result, not a floor. Inferring key/context from free text is
where the LLM diverges from the deterministic kernel — the spec for further work.
