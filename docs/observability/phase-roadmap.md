# Observability and Runtime Benchmark Roadmap

Date: 2026-07-15
Base: `origin/main` after PR #82

## Why the phases are being realigned

The original design in PR #70 defines two outcomes: instrument both runtimes,
then benchmark the custom streaming ReAct runtime against LangGraph4j. PRs
#71-#82 implemented instrumentation and operational preparation, but that
follow-up was also called "P2". This roadmap restores one clear sequence:

- **P1 — Instrumentation:** spans, metrics, Tempo, and dashboards.
- **P1.5 — Runtime acceptance and hardening:** prove the instrumentation works
  in a Docker-backed run and close evidence-driven gaps.
- **P2 — Dual-runtime benchmark:** compare ReAct and LangGraph4j on a fixed,
  reproducible scenario set.

The alert and trace-drilldown documents now belong to P1.5. This naming change
does not enable alerts or claim that runtime validation has passed.

## Current status

### P1 — Instrumentation: implemented, awaiting runtime acceptance

Merged work:

- #71: OpenTelemetry foundation and Tempo wiring
- #72: WebSocket/ReAct trace canary
- #73: LangGraph4j graph and node spans
- #74: RAG pipeline spans
- #75: memory and persistence spans
- #77: P1 acceptance checklist
- #78: initial Grafana dashboard

P1 code is implemented, but it is not operationally accepted until P1.5
records real trace and metric evidence.

### P1.5 — Runtime acceptance and hardening: in progress

Completed preparation:

- #80: dashboard query refinements and error panels
- #81: Tempo trace drilldown guide
- #82: alert candidates and baseline gates

Still required:

- Docker-backed validation of HTTP/LangGraph4j and WebSocket/ReAct
- a dated record with commit SHA, trace IDs, sampling rate, and dashboard evidence
- verification of blocking and streaming token-usage availability
- confirmation of span parentage, fallback/error status, and bounded tags
- fixes or tests for gaps discovered by the runtime run

### P2 — Dual-runtime benchmark: not started

No golden scenario set, dual-runtime runner, result extractor, or benchmark
report has been committed for this comparison.

## P1.5 — Runtime acceptance and hardening

### Preconditions

- Docker and real OpenAI/Pinecone credentials are available.
- The validation run uses `TRACING_SAMPLE_RATE=1.0`.
- The exact application commit is recorded.

### Validation run

Follow `docs/observability/p1-acceptance.md` and use
`docs/observability/p1-5-trace-drilldown.md`. At minimum, exercise:

1. an L3/L4 `POST /api/emotion/analyze` request through LangGraph4j and RAG;
2. a WebSocket conversation through ReAct, including a tool;
3. a controlled fallback or failure path where practical; and
4. memory reads/writes and async compression when reachable.

Create a dated record under `docs/observability/validation/` containing:

- commit SHA, profile, sampling rate, and environment notes
- trace IDs or reproducible Tempo queries for both runtimes
- dashboard screenshots or precise panel observations
- whether blocking and streaming LLM token usage was emitted
- orphan spans, missing metrics, cardinality, and error semantics
- follow-ups separated into required fixes and optional improvements

### Hardening policy

Implement only evidence-backed changes. Checks likely to matter include:

- restoring or replacing the disabled streaming ReAct verification test
- testing observation parentage and error status
- enabling histograms for custom timers that need p95
- making CI run the narrow observability regression suite
- recording usage as unavailable instead of estimating it when it is absent

Alert candidates in `docs/observability/p1-5-alert-candidates.md` remain disabled
until a baseline exists. Real alert provisioning is not required for P1.5.

### P1.5 exit criteria

P1.5 is complete when:

- both runtime paths have connected, recorded Tempo traces;
- dashboard queries match emitted metric names and labels;
- blocking and streaming usage support is recorded without guessed values;
- orphan spans and cardinality leaks are fixed or explicitly accepted;
- a fallback/failure is visibly distinguishable from success; and
- no unresolved blocker prevents benchmark measurement.

## P2 — Dual-runtime benchmark

### Goal

Produce a reproducible comparison of ReAct and LangGraph4j, backed by versioned
scenarios, raw results, and a written analysis. Capability and quality must be
reported alongside speed and cost.

### Fairness contract

Freeze and version before building the runner:

- scenario dataset and expected routing/tool/safety outcomes
- model, model parameters, prompt IDs and prompt versions
- application commit, runtime configuration, and dependency versions
- warm-up policy, repeats, timeout, concurrency, and retry policy
- success/failure definitions and handling of unavailable metrics

The runtimes are not feature-identical. Report TTFT only where it is defined;
do not manufacture LangGraph4j TTFT for a blocking response. Compare total
latency on a shared completion boundary and label runtime-only features.

`scenario.id` and `run.id` may be trace attributes or result fields, but must
not become low-cardinality Prometheus labels.

### PR sequence

#### P2-01: Benchmark contract and golden scenarios

- Add versioned L1-L5, multi-turn, tool, RAG, fallback, and safety scenarios.
- Define route accuracy, tool success, safety, and answer-quality evaluation.
- Reuse existing eval assets when their schema and provenance fit.

#### P2-02: Dual-runtime harness

- Drive the same scenario through ReAct and LangGraph4j.
- Attach runtime, scenario, prompt, dataset, and run metadata.
- Persist raw results and make one command reproduce a run.

#### P2-03: Metrics and statistical summary

- Extract TTFT where defined, total latency, calls, tokens, cost, failures,
  route accuracy, tool success, and safety results.
- Run warm-ups and repeated samples; report sample count, p50/p95, dispersion or
  confidence intervals, and missing data.
- Preserve raw results separately from generated summaries.

#### P2-04: Report and comparison dashboard

- Publish command, environment, limitations, raw-result link, and analysis.
- Explain orchestration overhead, streaming behavior, observability ergonomics,
  quality, and operational complexity without claiming false equivalence.

#### P2-05: Python LangGraph reference (optional)

- Add only after the two in-repository runtimes are reproducibly compared.
- Use the same scenarios and reporting contract through a documented adapter.

### P2 exit criteria

P2 is complete when one documented command produces raw results and a report
for both runtimes on the frozen scenario set, with reproducible latency,
quality, token/cost availability, and failure measurements. The report must
state limitations and distinguish measured facts from interpretation.

## Non-goals

- P1.5 does not change routing, RAG ranking, or memory behavior unless runtime
  evidence reveals a narrowly scoped defect.
- P1.5 does not enable guessed production alert thresholds.
- P2 does not declare one runtime superior from latency alone.
- The optional Python reference does not block the core P2.
