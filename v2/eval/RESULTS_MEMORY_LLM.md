# Stage 2 Memory Eval — LIVE (PR-2b)

Embedding: text-embedding-3-small · Chat: gpt-4o-mini · split: locked · k=3.
Kernel-llm INFERS semantic_key + context from content and judges conflicts via LLM
(PR-1 handed those fields). Compared against the deterministic kernel and the
last-write-wins baseline. Precision guards (extra_claim_rate / false_conflicts)
now matter because the LLM can over-emit.

| system | contradiction ↓ | coverage ↑ | conflict_acc ↑ | recall cur ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |
|---|--:|--:|--:|--:|--:|--:|--:|
| B-latest-by-key | 0.300 | 1.000 | 0.000 | 0.933 | 0.000 | 0.000 | 0 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0 |
| Kernel-llm | 0.200 | 0.900 | 0.600 | 1.000 | 1.000 | 0.077 | 0 |

Caveat: locked split (8 cases) — small N; gpt-4o-mini at temperature 0. This is a
real result, not a floor. Inference (key/context from text) is where the LLM can
diverge from the deterministic kernel.
