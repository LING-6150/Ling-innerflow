[P1-04] Spans for hybrid-RAG pipeline + fallback visibility

**Phase:** 1 · **Est:** ~1–1.5 days · **Risk:** Med · **Labels:** observability, rag

## Why
`HybridSearchService.hybridSearch` (or the current equivalent entrypoint on latest `main`) runs 5 stages (HyDE → Pinecone → ES BM25 → RRF → LLM rerank) with only `log.info` per stage. The graceful fallback to plain Pinecone is invisible — a degraded search looks identical to a healthy one.

## Scope
- Stage spans in `HybridSearchService.hybridSearch` (or current equivalent): `rag.hyde`, `rag.pinecone_query`, `rag.es_bm25`, `rag.rrf_merge`, `rag.rerank`.
- Mark cache hit/miss as span attributes only if the current implementation has a cache layer; do not add a fake cache metric.
- In the fallback catch block, set span status = error + event `rag.fallback.reason` **before** degrading.
- Spans inside `HyDEService`, `CBTKnowledgeService`, `LLMRerankerService` (auto `gen_ai` children attach).

## Acceptance
- A L3/L4 conversation shows the full RAG sub-tree with per-stage latency and, when forced, a red fallback span with a reason.
- If cache exists in latest code, traces include a real cache hit/miss attribute; if not, the issue closes without cache attributes.

## Depends on
P1-01, P1-02.
