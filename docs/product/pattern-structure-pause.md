# Pattern Structure Pause and Recovery Criteria

> Date: 2026-06-16
> Status: active product decision
> Scope: docs-only correction after PR #47

## Decision

Pause Pattern Structure module implementation.

PR #47 merged the aggregate Structure shell into `main`. That work is retained
as dormant infrastructure, not as an active product direction. The shell may
continue to return eligibility, evidence-window metadata, module empty states,
and safety metadata. It must not be followed by Scene Distribution,
Relationship Objects, Temporal Structure, or Neighbor Pattern content until the
discovery/abstain pipeline earns a reliable trust boundary.

## Why

`docs/STATE.md` correctly records the V1 LIVE result: the full Pattern Engine
pipeline underperformed the B2 single-prompt baseline and reached F1 `0.000` on
Tier A-H human prose. It also surfaced high-confidence false positives on the
full-decoy `ah-06` persona.

That failure mode makes Pattern Structure unsafe as an immediate product layer.
Structure does not make a weak pattern more trustworthy; it makes the weak
pattern more inspectable and therefore more persuasive. If the input pattern is
wrong, richer structure is a noise amplifier.

The confirmed/high-trust gate is the right product boundary in principle, but
the system has not yet proven that it can create high-trust candidates from
human prose without either admitting decoy false positives or collapsing recall.

## Current Allowed Work

Allowed:

- maintain the merged Structure shell as dormant infrastructure;
- keep eligibility and evidence endpoints compiling and tested;
- use the Structure shell only as a restrained unavailable/empty-state surface;
- improve discovery, abstain, OOD, calibration, and eval reporting;
- document recovery criteria and final V2 results.

Not allowed for now:

- implement Temporal Structure;
- implement Scene Distribution;
- implement Relationship Objects;
- implement Neighbor Patterns;
- add LLM-assisted Structure labels or summaries;
- build frontend UI that presents Structure as product-ready;
- use Structure to compensate for weak discovery quality.

## Recovery Criteria

Resume Pattern Structure modules only when one pipeline satisfies all of the
following on the agreed evaluation split:

1. matches or beats the B2 single-prompt baseline on held-out F1, or explicitly
   documents a tradeoff where F1 is comparable but safety is materially better;
2. keeps full-decoy false positives at or below `2`;
3. avoids recall collapse, with true positives preserved enough that the system
   can surface useful evidence-backed candidates;
4. reports precision, recall, F1, abstain rate, decoy false positives, cost, and
   latency in a committed results document;
5. distinguishes dev diagnostics from held-out proof so future decisions do not
   tune on the final test set.

If these criteria are not met, the honest product behavior is to abstain or show
insufficient-signal states, not to generate Structure.

## Active Next Work

The next implementation line is V2 abstain/calibration:

1. pick the candidate generator to evaluate, likely starting from the
   verifier-off pipeline or B2;
2. define the abstain score and threshold inputs;
3. sweep thresholds and produce a results table;
4. choose an operating point against F1, abstain rate, decoy false positives,
   cost, and latency;
5. write `eval/RESULTS_V2_FINAL.md` or an equivalent final results artifact.

The portfolio story should lead with this: InnerFlow found a dangerous failure
mode, paused an attractive feature direction, and returned to calibration and
evidence before adding user-facing structure.
