# M0 v1.3 official v1.2 exclusion provenance

**Status:** frozen selection prerequisite

**Source protocol:** M0 v1.2
**Generator:** `v2/scripts/freeze_m0_v13_exclusions.py`

The exclusion ledgers are derived only from the immutable v1.2 corpus and its
existing freeze manifest:

- `v2/eval/m0/fixtures/memory_reliability_m0.json`
- `v2/eval/m0/fixtures/candidate_registry.json`
- `v2/eval/m0/manifests/M0_CORPUS_FREEZE.json`

The candidate-hash ledger contains the 24 canonical scenario hashes already
recorded by the v1.2 freeze. The normalized-overlap ledger contains SHA-256
fingerprints of eligible user-authored setup text, probe text, answer options,
and deletion-target surface forms after NFKC normalization, Unicode case-fold,
whitespace collapse, and removal of fragments shorter than 20 characters.

This automatic ledger detects exact and normalization-only reuse. It does not
claim to detect semantic paraphrases. The whole-pool reviewer disposition in
`M0_V13_CANDIDATE_POOL_FREEZE_REVIEW.md` remains the evidence for the required
near-duplicate review; the automatic ledger does not replace that judgment.

The exclusion manifest binds the three v1.2 source-file hashes, the algorithm
identifier, both ledger identities, and both counts. Official selection rebuilds
all three outputs from the frozen sources and rejects any mismatch.
