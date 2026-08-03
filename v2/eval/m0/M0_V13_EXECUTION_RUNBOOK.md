# M0 v1.3 execution runbook

**Status:** execution tooling candidate; no model request is authorized until
the execution freeze receives `FREEZE_EXECUTION` and its PR is merged.

This runbook starts after the candidate pool, Beacon evidence, and deterministic
selection have each been independently frozen. It does not permit fixture,
gold, split, gate-world, prompt, model, policy, grader, or threshold changes.

## Frozen run choice

- provider: ModelVerse (`https://api.modelverse.cn/v1`)
- responder alias: `gemini-2.5-flash`
- embedding model: `text-embedding-3-large`
- formation temperature: `0.2`
- response temperature: `0.4`
- initial complete replicates: `3`
- variance escalation: exactly `5` total replicates, only after
  `NEEDS_FIVE_RUNS`
- retry policy: `3` whole-cell attempts, deterministic backoff `2s`, `4s`
- request timeout: `120s`
- policy set: `B-summary`, `B-full`, `B-none`
- each replicate: `24 candidates × 2 mandatory worlds × 3 policies = 144 cells`

ModelVerse does not expose an immutable upstream snapshot for
`gemini-2.5-flash`. The `/models` metadata response is hashed and the alias is
frozen, but provider-side alias drift remains a declared reproducibility
limitation. Do not describe this run as snapshot-reproducible.

## Artifact boundary

Public Git contains:

- the exact run manifest and implementation hashes;
- the public execution-freeze manifest;
- visible selection IDs;
- only count/aggregate hash for the eight holdout items;
- request-order hashes and execution-attempt IDs, never holdout request rows.

The unredacted selection, signed conformance object, request plan, histories,
checkpoints, raw responses, and item-level holdout traces remain under the local
sealed root:

```text
/Users/apple/Documents/New project/innerflow-m0-sealed/
```

This is process sealing, not cryptographic separation from the owner.

## Phase A — prepare without model access

Run only from the reviewed tooling commit and a clean worktree:

```bash
cd v2
uv run python scripts/run_m0_v13_reliability.py prepare
uv run python scripts/run_m0_v13_reliability.py preflight
uv run pytest tests/reliability/test_m0_v13_execution.py \
  tests/reliability/test_m0_v13_execution_freeze.py -q
uv run pytest -q
```

`prepare` is create-only. It writes the two public freeze artifacts and the
sealed artifacts/histories. It performs no responder or embedding request.

The execution-freeze PR must receive exactly one disposition:

- `FREEZE_EXECUTION`; or
- `PATCH_EXECUTION` with a concrete counterexample and minimal patch.

Do not run Phase B before `FREEZE_EXECUTION`, green CI, and merge.

## Phase B — three initial complete replicates

Before each command, run `preflight`. Execute one replicate at a time:

```bash
cd v2
uv run python scripts/run_m0_v13_reliability.py preflight
uv run python scripts/run_m0_v13_reliability.py run-replicate --replicate 1
uv run python scripts/run_m0_v13_reliability.py run-replicate --replicate 2
uv run python scripts/run_m0_v13_reliability.py run-replicate --replicate 3
uv run python scripts/run_m0_v13_reliability.py evaluate
```

Each successful replicate produces one create-only sealed checkpoint. A later
replicate cannot start without the immediately preceding complete checkpoint.
The evaluator uses only the frozen gate worlds; counter-world cells remain
mandatory diagnostics and never enter the 24-scenario G0 denominator.

## Provider failure or interruption

If any cell exhausts all three provider attempts, the replicate is incomplete.
The runner writes a sealed partial artifact and stops. Do not:

- count the missing cell as wrong;
- rerun only that cell;
- merge cells from the partial attempt into another attempt;
- change provider, model, prompt, temperature, retry policy, or order;
- invoke G0.

Classify the incident under §9.1. A replay is allowed once only with
contemporaneous external outage evidence and a byte-identical run manifest.
Without that evidence, terminate as `INCONCLUSIVE_API_FAILURE`. A syntactically
complete but invalid response is a terminal wrong output, not a provider
failure and not retryable.

## Variance escalation

If and only if the sealed three-run decision is `NEEDS_FIVE_RUNS`:

```bash
cd v2
uv run python scripts/run_m0_v13_reliability.py prepare-variance
```

Commit and review only the public replicate-4/5 order hashes. Do not call the
model until that small artifact is frozen. Then run replicates 4 and 5 and
evaluate again:

```bash
uv run python scripts/run_m0_v13_reliability.py run-replicate --replicate 4
uv run python scripts/run_m0_v13_reliability.py run-replicate --replicate 5
uv run python scripts/run_m0_v13_reliability.py evaluate
```

## Terminal boundary

- `GO_PATH_A` or `GO_PATH_B`: sign the decision before any M1 code.
- `STOP_COMMON_FLOOR` or `STOP_NO_HEADROOM_PATH`: terminate M1.
- `INCONCLUSIVE_MODEL_VARIANCE`: terminate M1 without tuning or rerun.
- `INCONCLUSIVE_API_FAILURE`: follow the one-replay rule above; otherwise
  terminate M1.

No visible-output analysis may change the frozen run. No M1 implementation is
authorized by this runbook alone.
