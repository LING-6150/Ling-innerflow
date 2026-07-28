# M0 v1.3 candidate-pool F1–F4 closure package

**Status:** targeted patch complete; awaiting closure review

**Patch base:** `9363f36`

This patch accepts and addresses only the four blocking findings from the
whole-pool review. It does not change the signed inventory, category/action
matrix, selection rules, gates, metrics, model configuration, or milestone
scope.

## F1 — correction/supersession construct collapse

**Disposition:** accept and patch

- Correction histories now begin with a concrete instruction recorded as
  factually true; the intervention explicitly states that it was mistaken and
  must not be treated as true.
- Supersession histories now begin with a formerly valid, time-bounded
  preference; the intervention explicitly replaces it from a new effective
  date.
- Category, assignment, action pair, and paired-world structure are unchanged.
- Automated checks assert the distinct lifecycle language for every correction
  and supersession candidate.

## F2 — probe/action leakage

**Disposition:** accept and patch

The four named candidates were rewritten:

- public/private feedback now states that either shared-thread or private-note
  delivery is acceptable;
- venting/planning now describes a minor setback and neutral request for a
  response;
- unrelated scheduling no longer states that stored memory is irrelevant;
- internal situation-slot labels were removed from rendered non-memory state.

Negative assertions retain the exact leaked phrases as forbidden text.

## F3 — deletion derived-storage evidence

**Disposition:** accept and patch

All eight deletion candidates now execute the real deterministic
`FaithfulSummaryPolicy` path through:

1. `memory.compression.summary`;
2. `memory.wiki.first_extract`;
3. `memory.reflection`;
4. final context rendering.

The audit marks derived deletion as passing only when the exact target claim:

- appears in the generated summary;
- retains its source-event attribution;
- appears in a structured Wiki field;
- appears in the rendered pre-delete derived context.

The audit records these booleans and the exact operation sequence per
candidate. A declaration in `deletion_storage_locations` alone is no longer
sufficient.

## F4 — template/fingerprint near duplicates

**Disposition:** accept and patch

- Every situation slot now has two authored neutral history details.
- Every candidate receives 19 non-repeating, situation-grounded filler
  utterances; no exact filler utterance is shared across candidates.
- Reserves use a separate narrative progression and a different probe
  construction, rather than replacing only the subject noun.
- Fingerprints are recomputed from event content, probes, and semantic claims;
  candidate IDs, roles, and `primary`/`reserve` labels are not fingerprint
  salts.
- Automated checks compare every reserve with its signed primary target and
  reject exact filler reuse across the whole corpus.

## Regenerated authoritative derivatives

The following were regenerated after the content patch:

- `M0_V13_AUTHORED_CANDIDATES.json`;
- `M0_V13_AUTOMATED_CONFORMANCE_AUDIT.json`;
- `M0_V13_CANDIDATE_REGISTRY_DRAFT.json`;
- all candidate content hashes and fingerprints;
- every registry and per-candidate audit hash.

The signed pre-content inventory is unchanged.

## Validation

```text
Candidate-pool tests: 10 passed
Full v2 suite: 140 passed
Python compileall: PASS
git diff --check: PASS
Model calls: 0
Beacon requests: 0
Selection/G0/M1: not executed
```

## Closure verdict

Review only F1–F4 and direct regressions introduced by their minimum patch.
Return:

- `FREEZE_CANDIDATE_POOL` if F1–F4 are closed; or
- `PATCH_CANDIDATE_POOL` with a concrete surviving counterexample.

A freeze authorizes corpus/registry hash freezing and the future-seed waiting
period only. It does not authorize Beacon retrieval, selection, model calls,
G0, or M1.
