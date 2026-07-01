package com.ling.linginnerflow.rag;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

/**
 * Enhanced hybrid retrieval pipeline:
 *
 *  1. HyDE  — LLM generates a hypothetical CBT document from the user query,
 *             then embeds it; this vector sits closer to real CBT entries than
 *             the raw user query embedding.
 *  2. Pinecone — vector search with the HyDE vector (candidate-k = 10)
 *  3. ES BM25  — keyword search on raw query (top-5)
 *  4. RRF     — fuses the two ranked lists into one candidate pool
 *  5. Re-rank — single LLM call scores every candidate against the original
 *               query; returns the top-N by relevance
 *
 * Fallback: any stage failure degrades gracefully to plain Pinecone search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final CBTKnowledgeService cbtKnowledgeService;
    private final CBTDocumentRepository cbtDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final HyDEService hydeService;
    private final LLMRerankerService reranker;
    private final ObservationRegistry observationRegistry;

    // How many candidates to pull from Pinecone before re-ranking.
    // Larger pool = better recall, but more tokens in the re-ranking prompt.
    @Value("${rag.pinecone.candidate-k:10}")
    private int candidateK;

    // ES candidate count (separate from candidateK so it can be tuned)
    @Value("${rag.es.candidate-k:5}")
    private int esCandidateK;

    // RRF constant (empirical; prevents high rank-position dominance)
    private static final int RRF_K = 60;

    // Final number of documents returned to the caller
    private static final int TOP_N = 3;

    /**
     * Main entry point.
     * Returns a single string of newline-separated CBT document snippets.
     */
    public String hybridSearch(String userInput) {
        Observation observation = Observation.createNotStarted("rag.hybrid_search", observationRegistry)
                .lowCardinalityKeyValue("rag.mode", "hybrid")
                .start();

        try (Observation.Scope ignored = observation.openScope()) {
            try {
                // ── Stage 1: HyDE query expansion ────────────────────────────
                List<Float> hydeVector = observeStage("rag.hyde", "hyde", "llm_embedding",
                        () -> hydeService.expandQuery(userInput));

                // ── Stage 2: Pinecone vector search (candidate pool) ──────────
                List<String> pineconeIds = observeStage("rag.vector_search", "vector_search", "pinecone", () ->
                        cbtKnowledgeService.retrieveIdsByVector(hydeVector, candidateK));
                tagHitBucket(observation, pineconeIds.size());
                log.info("[HybridSearch] Pinecone hits: {}", pineconeIds);

                // ── Stage 3: ES BM25 keyword search ───────────────────────────
                List<String> esIds = observeStage("rag.keyword_search", "keyword_search", "elasticsearch", () ->
                        esKeywordSearch(userInput, esCandidateK));
                log.info("[HybridSearch] ES hits: {}", esIds);

                // ── Stage 4: RRF fusion ────────────────────────────────────────
                List<String> candidateIds = observeStage("rag.rrf_merge", "rrf_merge", "rrf", () ->
                        rrfMerge(pineconeIds, esIds));
                tagHitBucket(observation, candidateIds.size());
                log.info("[HybridSearch] RRF candidate pool ({}): {}", candidateIds.size(), candidateIds);

                if (candidateIds.isEmpty()) {
                    observation.lowCardinalityKeyValue("rag.fallback", "false");
                    return "";
                }

                // ── Stage 5: LLM re-ranking ────────────────────────────────────
                List<String> candidateDocs = observeStage("rag.context_build", "context_build", "repository", () ->
                        fetchDocumentContents(candidateIds));
                List<String> rerankedIds = observeStage("rag.rerank", "rerank", "llm", () ->
                        reranker.rerank(userInput, candidateDocs, candidateIds, TOP_N));
                log.info("[HybridSearch] After re-rank top-{}: {}", TOP_N, rerankedIds);

                // ── Stage 6: Assemble final result ─────────────────────────────
                String content = observeStage("rag.context_build", "context_build", "repository", () ->
                        fetchContent(rerankedIds));
                observation.lowCardinalityKeyValue("rag.fallback", "false");
                return content;

            } catch (Exception e) {
                observation.lowCardinalityKeyValue("rag.fallback", "true");
                log.error("[HybridSearch] Pipeline failed, falling back to plain Pinecone: {}",
                        e.getMessage());
                return observeStage("rag.fallback", "fallback", "pinecone", () ->
                        cbtKnowledgeService.retrieveRelevantCBT(userInput));
            }
        } finally {
            observation.stop();
        }
    }

    // ── ES BM25 search ──────────────────────────────────────────────────────

    private List<String> esKeywordSearch(String userInput, int maxResults) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q
                            .match(m -> m
                                    .field("content")
                                    .query(userInput)
                                    .minimumShouldMatch("30%")
                            )
                    )
                    .withMaxResults(maxResults)
                    .build();

            SearchHits<CBTDocument> hits = elasticsearchOperations
                    .search(query, CBTDocument.class);

            List<String> ids = new ArrayList<>();
            hits.forEach(hit -> ids.add(hit.getId()));
            log.info("[HybridSearch] ES full-text hits: {}", ids.size());
            return ids;

        } catch (Exception e) {
            log.error("[HybridSearch] ES search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── RRF ─────────────────────────────────────────────────────────────────

    /**
     * Reciprocal Rank Fusion:  score(doc) = Σ  1 / (k + rank_in_list)
     * Returns all candidates ordered by descending RRF score (no hard cut here;
     * the re-ranker decides the final count).
     */
    private List<String> rrfMerge(List<String> pineconeIds, List<String> esIds) {
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(pineconeIds);
        allIds.addAll(esIds);

        Map<String, Double> scores = new HashMap<>();
        for (String id : allIds) {
            double score = 0.0;
            int pr = pineconeIds.indexOf(id);
            if (pr >= 0) score += 1.0 / (RRF_K + pr + 1);
            int er = esIds.indexOf(id);
            if (er >= 0) score += 1.0 / (RRF_K + er + 1);
            scores.put(id, score);
        }

        List<String> sorted = new ArrayList<>(scores.keySet());
        sorted.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        return sorted;
    }

    // ── Content fetching ────────────────────────────────────────────────────

    /** Fetch document texts in the order of the given ID list. */
    private List<String> fetchDocumentContents(List<String> ids) {
        List<String> contents = new ArrayList<>();
        for (String id : ids) {
            cbtDocumentRepository.findById(id)
                    .ifPresent(doc -> contents.add(doc.getContent()));
        }
        return contents;
    }

    /** Fetch and concatenate document texts for the final result string. */
    private String fetchContent(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            cbtDocumentRepository.findById(id)
                    .ifPresent(doc -> sb.append(doc.getContent()).append("\n---\n"));
        }
        return sb.toString();
    }

    private <T> T observeStage(String name, String stage, String source, Supplier<T> supplier) {
        Observation observation = Observation.createNotStarted(name, observationRegistry)
                .lowCardinalityKeyValue("rag.stage", stage)
                .lowCardinalityKeyValue("rag.source", source)
                .start();

        try (Observation.Scope ignored = observation.openScope()) {
            T result = supplier.get();
            tagResult(observation, result);
            return result;
        } catch (RuntimeException e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private void tagResult(Observation observation, Object result) {
        if (result instanceof Collection<?> collection) {
            tagHitBucket(observation, collection.size());
        } else if (result instanceof String text) {
            observation.lowCardinalityKeyValue("rag.empty", String.valueOf(text.isBlank()));
        }
    }

    private void tagHitBucket(Observation observation, int count) {
        observation.lowCardinalityKeyValue("rag.hit_bucket", hitBucket(count));
    }

    private String hitBucket(int count) {
        if (count == 0) return "0";
        if (count <= 3) return "1-3";
        if (count <= 10) return "4-10";
        return "11+";
    }
}
