# M0 v1.3 candidate authoring brief

**Status:** pre-content inventory proposal; not signed, frozen, or authorized
for candidate authoring

This brief fixes the authoring surface for the M0 v1.3 candidate pool before
any scenario claim, source event, probe, gold action, or surface-form lexicon
is written.

## Scope

The authoring round must produce exactly 36 eligible candidates: one for each
slot in the 12-row matrix frozen in
`docs/innerflow-memory-reliability-eval-v1.3.md` §4.3. Each slot has one
pre-registered primary ID. Each matrix row also has one pre-registered reserve
ID bound to a designated slot in that row. The reserve may replace only that
slot's primary, and only when a named, pre-model review finds that the primary
violates a frozen eligibility predicate. Both records remain in the final
registry.

No candidate content may be authored until:

1. the inventory proposal and this brief are committed;
2. the owner and an independent adversarial reviewer approve the inventory
   identity;
3. the approved inventory is emitted as a `SignedAuthoringInventory` whose
   canonical assignment hash binds every candidate ID to its primary/reserve
   role, category, action band, situation slot, provenance tier, taxonomy
   anchors, and reserve target;
4. the authoring agent is given this brief, the frozen protocol, the action
   executor, and public taxonomy references—but not v1.2 item-level outputs.

## Frozen authoring rules

- Required-memory candidates use byte-identical probes and non-memory state
  across `world_a` and `world_b`; only one effective memory claim changes.
- The two effective claims are non-empty, distinct, and bind different
  `CURRENT_EFFECTIVE` tracked claims.
- Both frozen actions must be plausible and safe without memory. Grammar,
  safety, politeness, factual correctness, or general social norms must not
  reveal the gold.
- No-memory candidates contain two distinct tracked `IRRELEVANT` claims while
  keeping the probe and `USE_UNPERSONALIZED_DEFAULT` gold unchanged.
- Deletion candidates use identical pre/post probes, bind the pre-delete claim
  as `DELETED`, and remove it from the post-delete world. At least one eventual
  selected deletion candidate must target content already absorbed into a
  summary or Wiki field.
- External-provenance (`E`) slots are construction-pattern items unless an
  independently auditable public item ID is recorded. They must never be
  described as item-level adaptations without that ID.
- Product-extension (`P`) slots are disclosed InnerFlow scenarios. Deletion is
  always a product-invariant extension rather than an exact benchmark mapping.
- Every primary and reserve receives a registry record. Rejected and replaced
  records are never deleted.
- Registry records must reproduce the complete signed assignment for their ID;
  matching only the signed candidate-ID set is insufficient.
- Candidate, event, template, and semantic-overlap fingerprints are unique.
- No model output, baseline trace, selected split, or gate-world assignment is
  available during authoring.

## Provenance anchors

| Local category | Permitted construction-pattern anchor |
|---|---|
| correction | LongMemEval knowledge-update; PersonaMem dynamic preference evolution |
| supersession | LongMemEval knowledge-update/temporal reasoning; PersonaMem preference evolution |
| context-exception | BenchPreS appropriate application/suppression; RPEval rational preference utilization |
| no-memory | LongMemEval abstention; RPEval irrelevant-memory interference |
| deletion | InnerFlow product invariant; nearest prior-work constructs disclosed as knowledge-update plus abstention |

The external anchors establish taxonomy and construction patterns only. M0
does not execute an external benchmark slice and does not claim benchmark
adaptation unless item-level provenance is later supplied.

## Reserve discipline

A reserve has the same category, action band, situation slot, provenance tier,
and taxonomy anchor as its primary. It must use a different underlying event,
probe-template fingerprint, and semantic-overlap fingerprint. A reserve cannot
be activated because the primary appears difficult or produces an unfavorable
model result. Replacement is permitted only before any baseline request and
only under a named conformance predicate with retained evidence.

## Required review output

The inventory reviewer returns:

- `FREEZE_INVENTORY`, or `PATCH_INVENTORY`;
- confirmation that all 12 rows, 36 slots, provenance minima, and 12 reserves
  are fixed without scenario content;
- confirmation that no v1.2 item-level output or treatment result informed the
  inventory;
- the reviewer identity and disposition artifact hash.

`FREEZE_INVENTORY` authorizes candidate content authoring. It does not
authorize pool eligibility decisions, Beacon selection, sealing, model calls,
G0, or M1.
