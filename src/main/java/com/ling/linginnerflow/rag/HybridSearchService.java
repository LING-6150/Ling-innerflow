package com.ling.linginnerflow.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
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
        try {
            // ── Stage 1: HyDE query expansion ────────────────────────────
            List<Float> hydeVector = hydeService.expandQuery(userInput);

            // ── Stage 2: Pinecone vector search (candidate pool) ──────────
            List<String> pineconeIds = cbtKnowledgeService
                    .retrieveIdsByVector(hydeVector, candidateK);
            log.info("[HybridSearch] Pinecone hits: {}", pineconeIds);

            // ── Stage 3: ES BM25 keyword search ───────────────────────────
            List<String> esIds = esKeywordSearch(userInput, esCandidateK);
            log.info("[HybridSearch] ES hits: {}", esIds);

            // ── Stage 4: RRF fusion ────────────────────────────────────────
            List<String> candidateIds = rrfMerge(pineconeIds, esIds);
            log.info("[HybridSearch] RRF candidate pool ({}): {}", candidateIds.size(), candidateIds);

            if (candidateIds.isEmpty()) return "";

            // ── Stage 5: LLM re-ranking ────────────────────────────────────
            List<String> candidateDocs = fetchDocumentContents(candidateIds);
            List<String> rerankedIds = reranker.rerank(
                    userInput, candidateDocs, candidateIds, TOP_N);
            log.info("[HybridSearch] After re-rank top-{}: {}", TOP_N, rerankedIds);

            // ── Stage 6: Assemble final result ─────────────────────────────
            return fetchContent(rerankedIds);

        } catch (Exception e) {
            log.error("[HybridSearch] Pipeline failed, falling back to plain Pinecone: {}",
                    e.getMessage());
            return cbtKnowledgeService.retrieveRelevantCBT(userInput);
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
}
