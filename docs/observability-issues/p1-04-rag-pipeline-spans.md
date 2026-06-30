[P1-04] Spans for hybrid-RAG pipeline + cache/fallback visibility

**Phase:** 1 · **Est:** ~1–1.5 days · **Risk:** Med · **Labels:** observability, rag

## Why
`HybridSearchService.doHybridSearch` runs 5 stages (HyDE → Pinecone → ES BM25 → RRF → LLM rerank) with only `log.info` per stage. The graceful fallback to plain Pinecone (`:126`) is invisible — a degraded search looks identical to a healthy one.

## Scope
- Stage spans in `HybridSearchService.doHybridSearch`: `rag.hyde`, `rag.pinecone_query`, `rag.es_bm25`, `rag.rrf_merge`, `rag.rerank`.
- Mark Redis cache hit/miss (`:80/:88`) as a span attribute.
- In the catch block (`:123`), set span status = error + event `rag.fallback.reason` **before** degrading.
- Spans inside `HyDEService`, `CBTKnowledgeService`, `LLMRerankerService` (auto `gen_ai` children attach).

## Acceptance
- A L3/L4 conversation shows the full RAG sub-tree with per-stage latency, a cache-hit flag, and (when forced) a red fallback span with a reason.

## Depends on
P1-01, P1-02.
