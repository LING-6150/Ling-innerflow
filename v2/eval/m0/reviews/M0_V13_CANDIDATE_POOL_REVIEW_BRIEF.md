# M0 v1.3 candidate-pool adversarial review brief

**Status:** candidate content authored; automated conformance complete;
candidate pool not frozen

**Pre-content inventory commit:** `089b04b`

## Authorization boundary

The signed inventory authorized candidate-content authoring only. This review
may return `FREEZE_CANDIDATE_POOL` or `PATCH_CANDIDATE_POOL`. It does not
authorize selection, a NIST Beacon request, responder-model calls, G0, or M1.

## Review surface

- `eval/m0/candidates/M0_V13_AUTHORED_CANDIDATES.json`
- `eval/m0/audits/M0_V13_AUTOMATED_CONFORMANCE_AUDIT.json`
- `eval/m0/registries/M0_V13_CANDIDATE_REGISTRY_DRAFT.json`
- `scripts/author_m0_v13_candidates.py`
- `scripts/audit_m0_v13_candidate_pool.py`
- `src/innerflow_v2/reliability/protocol_v13.py`
- `tests/reliability/test_m0_v13_authored_candidates.py`

The signed inventory and its review artifact are authoritative inputs and must
not change during candidate-pool review.

## Required whole-pool checks

1. Every one of the 48 signed assignments has exactly one authored candidate.
2. The 36 primary candidates fill the frozen 12-row matrix; the 12 reserves
   remain inactive and can replace only their signed primary target.
3. Every required-memory pair has byte-identical probes/non-memory state,
   exactly one changed setup-memory event, opposite frozen actions, and no
   normatively preferred answer without memory.
4. Every no-memory pair changes only one irrelevant memory and keeps the
   unpersonalized-default gold.
5. Every deletion pair appends one delete event targeting the tracked claim.
   The target appears before the faithful summary boundary and is declared in
   both summary and Wiki storage.
6. Every world contains at least 20 ordered events and demonstrably executes
   the faithful `B-summary` compression path.
7. Provenance is construction-pattern or disclosed product-extension
   provenance; no candidate claims item-level benchmark adaptation.
8. Candidate/event/probe/semantic fingerprints are content-derived, unique,
   and do not use candidate IDs merely as salts to hide duplicates.
9. Primary/reserve pairs differ in underlying event, probe, and semantic
   fingerprints.
10. Situations remain ordinary and low-stakes. Safety, politeness, grammar,
    generic social norms, or probe wording must not independently reveal gold.
11. No candidate copies a v1.2 item or uses a v1.2 result, selection state,
    gate-world assignment, baseline trace, or treatment output.
12. The draft registry hashes every authored record, reports 36 eligible
    primaries plus 12 inactive reserves, and remains marked pending whole-pool
    review.

## Required commands

```bash
cd /private/tmp/innerflow-memory-m0-pool/v2
UV_CACHE_DIR=/private/tmp/innerflow-m0-uv-cache \
  uv run pytest -q tests/reliability/test_m0_v13_authored_candidates.py
UV_CACHE_DIR=/private/tmp/innerflow-m0-uv-cache uv run pytest -q
```

## Required verdict

Return exactly one:

- `FREEZE_CANDIDATE_POOL`
- `PATCH_CANDIDATE_POOL`

For every blocking finding, provide a concrete candidate ID, counterexample,
and minimum patch. A freeze verdict must state that it authorizes corpus
hash-freezing and the future-seed waiting period only—not Beacon retrieval,
selection, model calls, G0, or M1.
