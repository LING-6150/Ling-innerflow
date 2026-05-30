# Pattern Engine eval harness

Pattern Engine results are reported as a metric triple: Tier A | Tier A-H | Tier B. Tier A is the open synthetic set used for development and calibration. Tier A-H is sealed and must not be read during tuning runs; the test harness enforces this convention with `-Dpattern.eval.tuning=true`. Tier B is reserved for later broader evaluation, so current PE-1 utilities only provide the shared objects and math that later baselines call.

The primary per-persona report contains precision, recall, F1, and hard-negative false-positive rate. Pattern hits require both `pattern_key` and domain to match, so domain reassignment receives no partial credit. R30-style reporting should keep these metrics separate for true labels and hard negatives rather than collapsing decoys into generic errors.

V1.2 R34 keeps a mechanism only if it is statistically supported on Tier A and not sign-reversed on Tier A-H. V1.2 R40 additionally reports recall retention through the verifier: S0 baseline, S2 recall candidates, S3 retrieval-gate survivors, and S4 verifier-chain survivors. The S4 value is the preserved recall that remains after verifier constraints rather than recall measured before them.
