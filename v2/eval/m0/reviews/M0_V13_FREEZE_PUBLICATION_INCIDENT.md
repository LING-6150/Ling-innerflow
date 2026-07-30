# M0 v1.3 candidate-pool freeze publication incident

**Detected at:** `2026-07-30T03:47:46Z`

**Affected local commit:** `67336ed9998e26e1bacdb5d5c788fdc401472d81`

**Disposition:** the original local freeze is ineligible for official Beacon
selection because it was not published before its recorded 24-hour boundary.

## Evidence

At detection time, both of these read-only commands returned no remote ref:

```text
git ls-remote --heads origin codex/memory-reliability-m0-candidate-pool
git ls-remote origin 67336ed9998e26e1bacdb5d5c788fdc401472d81
```

No Beacon pulse was retrieved and no selection, split, model call, G0, or M1
execution occurred.

## Resolution

- Preserve the original manifest and commit as incident evidence.
- Preserve every reviewed candidate, registry entry, audit entry, and hash.
- Publish a new manifest before its future effective freeze time.
- Restart the 24-hour waiting period from
  `2026-07-30T04:04:41Z`.
- Reject every Beacon pulse earlier than
  `2026-07-31T04:04:41Z`.

This is a publication-process correction only. It does not authorize corpus
changes, Beacon retrieval, selection, model calls, G0, or M1.
