# V2 Candidate Generator Redesign Note

Status: design note after PR #50/#51 candidate-generator audit.

This note is intentionally docs-first. Do not implement broader candidate generation from sealed Tier A-H misses. Design on Tier A, then use sealed personas only as a confirmation check.

## Problem

The V2 threshold sweep showed post-hoc thresholds cannot recover recall once true labels are absent from the candidate table. The candidate-generator audit then pinned the R1.5 bottleneck:

- Tier A generated true positives: `4/12`.
- Tier A missing true labels: `8`.
- Full-decoy false positives: `13` generated, `5` surfaced.
- The recovery target still requires full-decoy surfaced FP `<= 2` without recall collapse.

`eval/RESULTS_V2_TIER_A_RECOVERABILITY.md` further splits the 8 Tier A misses:

- `1/8` is recoverable by pattern-key relabel only.
- `7/8` require new candidate generation.

Therefore, the main bottleneck is under-generation, not thresholding. Domain relabeling is a bounded cheap fix, but it cannot make the pipeline interview-ready by itself.

## Constraints

- Keep strict `(pattern_key, domain)` recall as the headline metric.
- Treat pattern-key-only matching as a diagnostic upper bound, not a scoring change.
- Do not tune on Tier A-H sealed labels. The sealed set should remain a confirmation slice.
- Re-measure full-decoy generated FP and surfaced FP after any broader generation change.
- Do not resume Pattern Structure modules until the trust boundary passes the recovery criteria in `docs/product/pattern-structure-pause.md`.

## Proposed Minimal Experiment

Build the next implementation as an offline candidate-generation experiment, not a product feature:

1. Keep the existing generator as the baseline.
2. Add a Tier-A-only candidate expansion path that is evidence-gated:
   - emit a candidate only when at least one corpus excerpt is cited;
   - preserve the candidate's proposed domain;
   - report generated candidates before the abstain gate.
3. Compare against the current R1.5 table:
   - strict Tier A generated TP / true labels;
   - domain-relabel upper bound;
   - generated FP count;
   - surfaced FP after the existing gate;
   - cost and latency if any LLM call is introduced.

The first implementation should target the `needs_new_generation` class, not the single `recoverable_by_relabel` case.

## Success Bar

For an implementation PR to be worth merging, it should show one of these:

- strict Tier A generated recall improves over `4/12` without increasing full-decoy surfaced FP above `5`; or
- full-decoy surfaced FP moves toward `<=2` while strict Tier A recall does not collapse; or
- the experiment fails, but explains which generator assumption failed and what should be cut.

Do not claim held-out proof unless the sealed set is run exactly once as confirmation after the Tier A design is frozen.
