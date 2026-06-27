# Stage 2 Memory Eval — Diagnostic Floor (deterministic)

Split: `locked` · retrieval k=3. Systems receive only the observation stream; gold is held by the evaluator. Deterministic floor — NOT a proof; the real (LLM/embedding) result is PR-2.

| system | contradiction_rate ↓ | coverage ↑ | conflict_acc ↑ | recall@k current ↑ | recall@k historical ↑ |
|---|---:|---:|---:|---:|---:|
| B-full | 0.300 | 1.000 | 0.000 | 1.000 | 1.000 |
| B-rag | 1.000 | 0.000 | 0.000 | 1.000 | 1.000 |
| B-latest-by-key | 0.300 | 1.000 | 0.000 | 0.933 | 0.000 |
| B-extract-only | 0.300 | 1.000 | 0.000 | 1.000 | 1.000 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 1.000 | 1.000 |

Reading: B-latest-by-key is strong on current facts but loses keep_both (context-specific exceptions) and historical recall; B-full/B-extract keep stale facts as current; B-rag builds no profile. The kernel should be the only system strong on all columns.
