#!/usr/bin/env bash
#
# Create the observability + benchmark plan as GitHub issues.
#
# Prereq (one-time):   gh auth login        # pick LING-6150/Ling-innerflow's host (github.com), SSH or HTTPS
# Run:                 bash scripts/create-observability-issues.sh
# Dry run (no writes): DRY_RUN=1 bash scripts/create-observability-issues.sh
#
# Idempotency note: re-running creates DUPLICATE issues (GitHub has no upsert).
# Labels/milestones are safe to re-run. Run once.

set -euo pipefail

REPO="LING-6150/Ling-innerflow"
DIR="docs/observability-issues"
DRY_RUN="${DRY_RUN:-0}"

run() { if [[ "$DRY_RUN" == "1" ]]; then echo "DRY: $*"; else "$@"; fi; }

command -v gh >/dev/null || { echo "gh not installed"; exit 1; }
if [[ "$DRY_RUN" != "1" ]]; then
  gh auth status >/dev/null 2>&1 || { echo "Run 'gh auth login' first."; exit 1; }
fi

# ── 1. Labels (idempotent) — "name color" pairs (bash 3.2 compatible) ─────
LABELS=(
  "observability 0e8a16" "infra 5319e7"     "agent-runtime 1d76db"
  "rag fbca04"           "memory c5def5"     "streaming bfd4f2"
  "eval d93f0b"          "benchmark b60205"  "test 0052cc"
  "docs cccccc"          "stretch fef2c0"    "epic 3e4b9e"
)
echo "── labels ──"
for pair in "${LABELS[@]}"; do
  set -- $pair
  run gh label create "$1" --repo "$REPO" --color "$2" --force >/dev/null
done

# ── 2. Milestones ─────────────────────────────────────────────────────────
echo "── milestones ──"
ms_number() { gh api "repos/$REPO/milestones?state=all" --jq ".[] | select(.title==\"$1\") | .number" 2>/dev/null | head -1; }
ensure_ms() {
  local title="$1" desc="$2" num
  if [[ "$DRY_RUN" == "1" ]]; then echo "DRY: ensure milestone '$title'"; return; fi
  num="$(ms_number "$title")"
  if [[ -z "$num" ]]; then
    gh api "repos/$REPO/milestones" -f title="$title" -f description="$desc" --jq '.number' >/dev/null
  fi
}
ensure_ms "Phase 1: OTEL Trace"        "Distributed tracing across the core LLM chain"
ensure_ms "Phase 2: Runtime Benchmark" "Trace-driven benchmark: custom ReAct runtime vs LangGraph"
MS1="Phase 1: OTEL Trace"
MS2="Phase 2: Runtime Benchmark"

# ── 3. Helper: create one issue from a draft file ─────────────────────────
# Draft format: line 1 = title, line 2 = blank, line 3+ = body.
create_issue() {
  local file="$1" milestone="$2"; shift 2
  local title body label_args=()
  title="$(head -1 "$DIR/$file")"
  body="$(tail -n +3 "$DIR/$file")"$'\n\n---\nSource of truth: `'"$DIR/$file"'` · Plan: `docs/observability-plan.md`'
  for l in "$@"; do label_args+=(--label "$l"); done
  echo "  + $title"
  run gh issue create --repo "$REPO" --title "$title" --body "$body" \
      --milestone "$milestone" "${label_args[@]}"
}

# ── 3b. Epic / tracking issue (the "main plan") ───────────────────────────
echo "── epic ──"
EPIC_BODY=$(cat <<'EOF'
Reposition InnerFlow from "emotional-support app" to a **production-grade AI runtime platform** (resume target: Applied AI / AI Infra). Full plan: `docs/observability-plan.md`.

**Problem:** the repo has a dual agent runtime (custom streaming ReAct **and** LangGraph4j), hybrid RAG, 3-tier memory, layered safety — but **zero** per-step observability. Today we cannot see latency, token/cost, model, prompt version, retry, or failure reason for any step. No OpenTelemetry; no custom Micrometer; LLM usage metadata is discarded.

### Phase 1 — OTEL trace across the core chain (~6–9 dev-days)
- [ ] P1-01 OTEL deps + OTLP collector + Grafana/Tempo
- [ ] P1-02 ObservabilityConfig (ObservedAspect, reactive context propagation, cost handler, prompt-version)
- [ ] P1-03 Graph node spans (analyzer / planner / L1–L5)
- [ ] P1-04 RAG pipeline spans + fallback visibility (cache hit/miss only if present on current `main`)
- [ ] P1-05 Memory spans (long-memory / reflection / dedup / compression)
- [ ] P1-06 ReAct + WebSocket spans, reactive context propagation **(spike first — highest risk)**
- [ ] P1-07 Grafana dashboards + trace-presence verification (also fix CI `-DskipTests`)

### Phase 2 — Runtime benchmark: my ReAct runtime vs LangGraph (~5–8 dev-days)
- [ ] P2-01 Golden conversation eval set (L1–L5 + tool-triggering)
- [ ] P2-02 Dual-runtime benchmark harness (ReAct vs LangGraph4j)
- [ ] P2-03 Extract benchmark metrics from traces (TTFT / latency / tokens / cost / failure-rate)
- [ ] P2-04 Benchmark report + comparison dashboard
- [ ] P2-05 (stretch) External LangGraph (Python) reference target

**Key lever:** Spring AI 1.0 can emit `gen_ai.*` observations/spans once a Tracer bean exists → model metadata and much of the cost/model gap should close with minimal call-site changes, while streaming token usage must be verified. **Key risk:** reactive context propagation on the WS/ReAct path (P1-06) — do it first as the canary.
EOF
)
run gh issue create --repo "$REPO" --title "[EPIC] Observability + runtime benchmark — reposition as AI runtime platform" \
    --body "$EPIC_BODY" --label epic --label observability

# ── 4. Phase 1 issues ─────────────────────────────────────────────────────
echo "── Phase 1 issues ──"
create_issue p1-01-otel-deps-collector.md   "$MS1" observability infra
create_issue p1-02-observability-config.md  "$MS1" observability
create_issue p1-03-graph-node-spans.md      "$MS1" observability agent-runtime
create_issue p1-04-rag-pipeline-spans.md    "$MS1" observability rag
create_issue p1-05-memory-spans.md          "$MS1" observability memory
create_issue p1-06-react-ws-spans.md        "$MS1" observability agent-runtime streaming
create_issue p1-07-dashboards-verify.md     "$MS1" observability infra test

# ── 5. Phase 2 issues ─────────────────────────────────────────────────────
echo "── Phase 2 issues ──"
create_issue p2-01-golden-scenarios.md            "$MS2" eval benchmark
create_issue p2-02-benchmark-harness.md           "$MS2" eval benchmark agent-runtime
create_issue p2-03-trace-metrics-extraction.md    "$MS2" eval benchmark observability
create_issue p2-04-benchmark-report.md            "$MS2" eval benchmark docs
create_issue p2-05-langgraph-python-reference.md  "$MS2" eval benchmark stretch

echo "Done. View: gh issue list --repo $REPO"
