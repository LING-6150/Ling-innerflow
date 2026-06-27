# Stage 2 Memory Eval — Diagnostic Floor (deterministic)

Split: `locked` · retrieval k=3. Systems receive only the observation stream; gold is held by the evaluator. Deterministic floor — NOT a proof; the real (LLM/embedding) result is PR-2.

| system | contradiction_rate ↓ | coverage ↑ | conflict_acc ↑ | recall@k current ↑ | recall@k historical ↑ | extra_claim_rate ↓ | false_conflicts ↓ |
|---|---:|---:|---:|---:|---:|---:|---:|
| B-full | 0.300 | 1.000 | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| B-rag | 1.000 | 0.000 | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| B-latest-by-key | 0.300 | 1.000 | 0.000 | 0.933 | 0.000 | 0.000 | 0 |
| B-extract-only | 0.300 | 1.000 | 0.000 | 1.000 | 1.000 | 0.000 | 0 |
| Kernel-deterministic | 0.000 | 1.000 | 1.000 | 1.000 | 1.000 | 0.000 | 0 |

Reading: B-latest-by-key is strong on current facts but loses keep_both (context-specific exceptions) and historical recall; B-full/B-extract keep stale facts as current; B-rag builds no profile. The kernel should be the only system strong on all columns. The precision guards (extra_claim_rate / false_conflicts) read 0 for these deterministic systems — they exist to catch an LLM over-emitting in PR-2.
