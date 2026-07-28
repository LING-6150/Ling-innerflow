# M0 v1.3 deterministic conformance layer

**Status:** adversarial-review candidate; candidate pool and responder run not authorized

This change implements only the deterministic protocol surface frozen in
`docs/innerflow-memory-reliability-eval-v1.3.md`. It does not author the
official 36-candidate pool, obtain a formal NIST pulse, call a responder model,
or reinterpret the archived v1.2 result.

## Isolation from v1.2

The archived v1.2 models, fixtures, manifests, raw hashes, reports, and G0
implementation are unchanged. v1.3 uses separate modules:

- `protocol_v13.py`: frozen ontology, paired-world schema, claim labels,
  complete eligible/rejected/replaced registry, 36-slot pool linter, RFC 8785
  hashes, deterministic selection/split/gate assignment, public holdout
  redaction, and binding invalidation rules;
- `execution_v13.py`: exact transport grading, deterministic action executor,
  complete-replicate ordering, sealed checkpoint/resume manifests, gate-world
  projection, and the termination state machine;
- `M0_V13_ACTION_EXECUTOR.json`: concrete downstream directives for every
  frozen action; canonical executor SHA-256
  `1abe404415d06ea546a1d6089785aa4c720d16bb437075aa51dc2a8a975ec0b1`;
- `check_m0_v13_conformance.py`: offline pool validation and selection from a
  supplied, already verified Beacon pulse artifact.

`rfc8785` is pinned in `uv.lock`; ad hoc `json.dumps(sort_keys=True)` is not
used for v1.3 corpus identity.

## Frozen §11 traceability

| §11 requirement | Deterministic evidence |
|---:|---|
| 1–2 | Bare JSON and one exact outer `json` fence acceptance tests |
| 3 | Parameterized rejection of nested/untyped fences, prose, extra/duplicate keys, invalid enums, arrays, and removed `memory_action`/`NONE` |
| 4 | Single-action schema and complete deterministic executor binding |
| 5 | Required-memory worlds bind distinct effective tracked claims, frozen actions, and certificate bindings |
| 6 | No-memory worlds bind two distinct tracked irrelevant claims and the unpersonalized action |
| 7 | Deletion pre/post worlds bind a tracked deleted claim and require its post-delete absence |
| 8 | Frozen `ResponseAction` enum and executor completeness validator |
| 9–10 | Exact 36-slot matrix, provenance quotas, and 6/4/6/4/4 plus 16/8 selection tests |
| 11 | RFC 8785 identity, deterministic Beacon assignment, and signed authoritative-selection enforcement |
| 12 | Required symmetry certificate and named reviewer disposition |
| 13 | World-bound applicability plus deterministic frozen-lexicon classification from rendered context |
| 14–15 | Independently signed authoring inventory, complete audit registry, and hash-bound nonempty exclusions |
| 16 | Aggregate-only public holdout and checkpoint representations |
| 17 | Content identity, raw response/attempt ledger, single execution epoch per replicate, run-bound checkpoint/resume |
| 18 | Counter-world exclusion, exact replicate sets, unchanged gates, and derived—not caller-supplied—Path B evidence |
| 19 | Manifest-bound incident histories enforce terminal outcomes and one replay/repair |
| 20 | Manifest-bound invalidation history derives counts and terminates a second valid request |
| 21 | Frozen grader replay prevents hand-authored grades; exhausted provider failures remain missing |

## Adversarial implementation-review closure

The `PATCH BEFORE COMMIT` review findings are closed as follows:

- **F1:** category-specific paired-world, tracked-claim, deletion, and
  certificate invariants now reject empty or non-causal fixtures.
- **F2:** registry validation now requires a separately frozen and signed
  authoring inventory; deleting a rejected record and rewriting the registry's
  own inventory identity no longer passes.
- **F3:** request ordering, checkpointing, and G0 revalidate registry, pool,
  and selected candidate hashes; probe/claim/gold drift is rejected.
- **F4:** every cell retains raw response, request attempts, and one
  replicate-execution identity. Cross-attempt cell merging is rejected, and
  an interrupted attempt recorded in the run ledger is permanently void.
- **F5:** G0 accepts only complete replicates `{1,2,3}` or
  `{1,2,3,4,5}`, including every mandatory counterworld.
- **F6:** Path B evidence is derived from the gate world's applicable claim
  and actual B-full rendered context through the frozen lexicon classifier.
- **F7:** checkpoints and resumes bind the full run manifest, including exact
  model version, corpus, date, stage prompts, temperatures, token limits,
  retry policy, dependency lock, and order seed.
- **F8:** the signed conformance and frozen run manifests now contain their
  unique absolute history paths. Official APIs derive the path from those
  manifests; callers can no longer select a second path during initialization
  or transition.
- **N1:** every execution entry verifies the pre-signed authoritative
  selection hash. Same-category split swaps, balanced gate swaps, and
  quota-compatible candidate replacements are rejected.

## Validation

```text
cd v2
uv run pytest tests/reliability -q
```

```text
69 passed
```

```text
cd v2
uv run pytest -q
```

```text
119 passed
```

## Authorization boundary

Passing these tests alone does not authorize candidate authoring. A final
targeted closure review must return `COMMIT`; only then does this layer permit
candidate authoring and whole-pool review. It never authorizes pool sealing or
model execution. Before selection, the next change must supply:

1. the complete 36-candidate registry and frozen old-item
   hashes/fingerprints;
2. signed symmetry/provenance/overlap dispositions for the entire pool;
3. the committed pool hash and timezone-aware freeze timestamp;
4. a Beacon 2.0 pulse at least 24 hours later, with independently verified
   signature evidence.

Only after the selected corpus, process-sealed holdout, action executor, run
manifest, and every pre-run check are signed may responder requests begin.
