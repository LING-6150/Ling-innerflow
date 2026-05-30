# Pattern Engine V1 LIVE Validation

Generated: 2026-05-30T14:04:06.726879Z

## RQ1 — Full vs Baselines

### Tier A (6 synthetic personas)
| Baseline | Avg Precision | Avg Recall | Avg F1 | Avg HardNegFPR | Tokens used | Wall time |
|---|---:|---:|---:|---:|---:|---:|
| B0 | 0.167 | 0.083 | 0.111 | 0.083 | 0 | 0.021s |
| B1 | 0.000 | 0.000 | 0.000 | 0.083 | 0 | 0.008s |
| B2 | 0.275 | 0.722 | 0.386 | 0.667 | 14398 | 17.471s |
| full | 0.167 | 0.083 | 0.111 | 0.083 | 28512 | 161.639s |

### Tier A-H (5 human personas)
| Baseline | Avg Precision | Avg Recall | Avg F1 | Avg HardNegFPR | Tokens used | Wall time |
|---|---:|---:|---:|---:|---:|---:|
| B0 | 0.067 | 0.040 | 0.050 | 0.000 | 0 | 0.014s |
| B1 | 0.000 | 0.000 | 0.000 | 0.000 | 0 | 0.010s |
| B2 | 0.324 | 0.427 | 0.359 | 0.560 | 23410 | 14.036s |
| full | 0.000 | 0.000 | 0.000 | 0.133 | 71663 | 262.040s |

### Per-persona breakdown (for full pipeline)
| persona | true patterns | predicted | matched | F1 |
|---|---:|---:|---:|---:|
| a-01 | 2 | 3 | 0 | 0.000 |
| a-02 | 2 | 1 | 1 | 0.667 |
| a-03 | 2 | 0 | 0 | 0.000 |
| a-04 | 1 | 0 | 0 | 0.000 |
| a-05 | 2 | 1 | 0 | 0.000 |
| a-06 | 3 | 1 | 0 | 0.000 |
| ah-02 | 6 | 0 | 0 | 0.000 |
| ah-03 | 5 | 0 | 0 | 0.000 |
| ah-04 | 3 | 2 | 0 | 0.000 |
| ah-05 | 0 | 0 | 0 | 0.000 |
| ah-06 | 0 | 12 | 0 | 0.000 |

## RQ2 — Verifier Ablation

### Tier A
| Variant | F1 | Tokens | Wall time |
|---|---:|---:|---:|
| full | 0.111 | 28512 | 161.639s |
| full-no-verify | 0.333 | 12513 | 36.329s |
| Δ | 0.222 | -15999 | -125.310s |

### Tier A-H
| Variant | F1 | Tokens | Wall time |
|---|---:|---:|---:|
| full | 0.000 | 71663 | 262.040s |
| full-no-verify | 0.000 | 28434 | 43.162s |
| Δ | 0.000 | -43229 | -218.878s |

## Raw cost summary

Chat tokens total: 97036  Embedding tokens total: 81894

Total USD (gpt-4o-mini @ $0.15/$0.60 per 1M, text-embedding-3-small @ $0.02 per 1M): $0.0248

## Implementation notes

- This LIVE validation bypasses Spring Boot and reads the OpenAI API key via `System.getenv` (preferring `MY_OPENAI_KEY`, then `PERSONAL_OPENAI_KEY`, then `OPENAI_API_KEY`).
- `src/main/java` was not modified; the V1 pipeline is manually assembled in test validation code.
- Token totals are API metadata totals captured from Spring AI chat/embedding responses.

## Interpretation

The raw Tier A averages put full at F1 0.111 versus B0 0.111, B1 0.000, and B2 0.386. The data suggests the full pipeline is clearly below the simple baselines on the synthetic set.

On Tier A-H, full F1 is 0.000 versus B2 0.359. This is the more important read because the sealed personas include adversarial human writing; the data suggests is clearly below B2 on this slice.

Removing §4 verification changes F1 from 0.111 to 0.333 on Tier A and from 0.000 to 0.000 on Tier A-H. That is consistent with verification being less helpful than expected in this small run.

For ah-05/ah-06, the full pipeline surfaced 12 total pattern(s). The separate decoy forensics report should be read as the qualitative safety check for whether abstention came from downstream evidence filtering or from no recall candidates.
