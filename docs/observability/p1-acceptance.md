# P1 Observability Acceptance

Date: 2026-07-02
Base: `origin/main` after PR #76

## Scope

This note closes the P1 observability slice added across PRs #71-#76:

- OTEL foundation
- WebSocket/ReAct trace canary
- Emotion graph path spans
- RAG pipeline spans
- Memory/session persistence spans
- Memory compression lock ownership fix

## Local Validation

The following local checks passed on the acceptance branch:

```bash
./mvnw -q -DskipTests compile
./mvnw -q -Dtest=EmotionAnalyzerNodeTest,RAGQualityVerificationTest,MemoryCompressionTest test
```

`ReActStreamingVerificationTest` remains intentionally disabled because it tracks a pre-existing mismatch with the current speculative streaming implementation. It is not counted as a passing P1 acceptance test.

## Expected Trace Shape

Trigger an L3/L4 `/api/emotion/analyze` request in a deployed environment and verify this Tempo shape:

```text
http.server.requests
  -> memory.add_message
  -> emotion.graph.invoke
    -> node.analyzer
      -> gen_ai.*
    -> node.planner
      -> gen_ai.*
    -> node.l3 / node.l4
      -> rag.hybrid_search
        -> rag.hyde
          -> gen_ai.*
        -> rag.vector_search
        -> rag.keyword_search
        -> rag.rrf_merge
        -> rag.candidate_fetch
        -> rag.rerank
          -> gen_ai.*
        -> rag.final_context
      -> gen_ai.*
  -> memory.add_message
  -> emotion.log
```

For WebSocket/ReAct flows, verify:

```text
ws.message.handle
  -> memory.add_message
  -> react.run
    -> react.phase1
    -> tool.execute
    -> react.phase2
  -> memory.add_message
```

`memory.compress` is expected to appear as a root span because it runs as a fire-and-forget `@Async` task. Do not force it under the triggering HTTP/WS span; use a span link or explicit correlation field in a future PR if causal linkage is needed.

## Tag Cardinality Checklist

Confirm in metrics and traces that these tags stay bounded:

- `node.name`: `analyzer`, `planner`, `l1`, `l2`, `l3`, `l4`, `l5`
- `emotion.level`: `1`-`5` or `unknown`
- `route.level`: `1`-`5`
- `prompt.id`: fixed prompt identifiers only
- `prompt.version`: fixed version strings only
- `rag.stage`: fixed RAG stage identifiers only
- `rag.source`: fixed source identifiers only
- `rag.hit_bucket`: `0`, `1-3`, `4-10`, `11+`
- `memory.operation`: fixed memory operation identifiers only
- `memory.store`: `redis`, `repository`, or `mixed`
- `memory.size_bucket`: `0`, `1-2`, `3-10`, `11-20`, `21+`
- `memory.role`: `user`, `assistant`, `system`, or `other`
- `emotion.source`: `chat`, `checkin`, `websocket`, or `unknown`

These values must not contain user IDs, raw messages, prompts, RAG document IDs, retrieved content, LLM output, Redis keys, or exception messages.

## Query Smoke Checks

In Tempo or Grafana, verify that the following filters return useful traces:

- `node.name="l3"` or `node.name="l4"`
- `rag.stage="rerank"`
- `memory.operation="build_context"`
- `prompt.id="rag.reranker"`
- `prompt.id="memory.wiki.merge"`
- `emotion.source="chat"`

## Deployment Acceptance

Before calling P1 observability complete, record a production-like trace sample that confirms:

- HTTP, graph, node, Spring AI, RAG, memory, and emotion-log spans are connected without unexpected orphan spans.
- `memory.compress` appears as a root async background span when compression is triggered.
- Prometheus/OTEL metric cardinality does not grow with user input, tool hallucinations, document IDs, or model output.
- Error spans appear on failing child operations while existing fallback behavior remains unchanged.

If these checks pass, the next implementation slice should move from instrumentation to dashboarding and alerting.
