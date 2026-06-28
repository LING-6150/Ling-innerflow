# Stage 2 Memory Eval — PR-2c (prompt v1 vs v2, generalization check)

gpt-4o-mini + text-embedding-3-small · k=3. Prompt v2 adds context-inference
rules + few-shot. Question: does v2 beat v1, and does the gain hold on the
HELD-OUT challenge split (not tuned on)? locked must not be tuned on.

## split: dev (8 cases)

| system | contradiction ↓ | conflict_acc ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |
|---|--:|--:|--:|--:|--:|
| B-latest-by-key | 0.300 | 0.000 | 0.000 | 0.000 | 0 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| Kernel-llm-v1 | 0.100 | 0.400 | 1.000 | 0.000 | 0 |
| Kernel-llm-v2 | 0.000 | 0.800 | 1.000 | 0.000 | 0 |

## split: locked (8 cases)

| system | contradiction ↓ | conflict_acc ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |
|---|--:|--:|--:|--:|--:|
| B-latest-by-key | 0.300 | 0.000 | 0.000 | 0.000 | 0 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| Kernel-llm-v1 | 0.200 | 0.600 | 1.000 | 0.067 | 0 |
| Kernel-llm-v2 | 0.100 | 0.600 | 1.000 | 0.000 | 0 |

## split: challenge (6 cases)

| system | contradiction ↓ | conflict_acc ↑ | recall hist ↑ | extra_claim ↓ | false_conf ↓ |
|---|--:|--:|--:|--:|--:|
| B-latest-by-key | 0.375 | 0.000 | 0.000 | 0.000 | 0 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| Kernel-llm-v1 | 0.000 | 0.400 | 1.000 | 0.000 | 2 |
| Kernel-llm-v2 | 0.000 | 0.600 | 1.000 | 0.000 | 1 |

Caveat: small N per split; gpt-4o-mini temperature 0 still varies run to run.
Verdict is whatever the challenge split shows — report it honestly, do not
iterate the prompt against locked/challenge until it looks good.
