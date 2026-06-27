# Pattern Engine V2 Semantic Candidate Recall Audit

Input: checked-in Tier A corpus + 12 pattern definitions (exemplars). Embedding: `OpenAI text-embedding-3-small (live)`.

⚠️ OFFLINE eval-only diagnostic. Does NOT modify `PatternRecallService`; NOT held-out proof — τ/topK calibrated on Tier A, full decoys are false-positive confirmation only. Recall is pattern_key level (domain assignment is downstream). Embeddings computed once and cached; the sweep only re-thresholds.

## Sweep

| topK | tau | Tier A gen TP | Tier A true keys | Tier A recall | Tier A gen FP | full-decoy gen FP |
|---:|---:|---:|---:|---:|---:|---:|
| 3 | 0.150 | 7 | 12 | 0.583 | 11 | 6 |
| 3 | 0.250 | 7 | 12 | 0.583 | 11 | 6 |
| 3 | 0.350 | 7 | 12 | 0.583 | 11 | 6 |
| 3 | 0.450 | 6 | 12 | 0.500 | 8 | 5 |
| 5 | 0.150 | 9 | 12 | 0.750 | 21 | 10 |
| 5 | 0.250 | 9 | 12 | 0.750 | 21 | 10 |
| 5 | 0.350 | 9 | 12 | 0.750 | 21 | 10 |
| 5 | 0.450 | 6 | 12 | 0.500 | 13 | 5 |
| 12 | 0.150 | 12 | 12 | 1.000 | 60 | 24 |
| 12 | 0.250 | 12 | 12 | 1.000 | 60 | 24 |
| 12 | 0.350 | 12 | 12 | 1.000 | 56 | 22 |
| 12 | 0.450 | 7 | 12 | 0.583 | 14 | 5 |

## Caveats

- Recovery target stays full-decoy generated FP `<= 2` without Tier A recall collapse.
- Deterministic fake embeddings only validate the harness; a real-embedding run is required for any recall claim.
- Key-level recall only; does not change the `(pattern_key, domain)` headline metric.
