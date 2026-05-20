package com.ling.linginnerflow.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies the HyDE + Re-ranking pipeline in isolation.
 *
 * Q1. HyDE embeds the *hypothetical document*, not the raw user query.
 * Q2. Re-ranker parses a clean LLM response "3,1,2" into the correct ID order.
 * Q3. Re-ranker falls back to original RRF order when LLM returns too few indices.
 * Q4. Re-ranker handles noisy LLM output (prose mixed with numbers).
 * Q5. Re-ranker skips the LLM call when candidate count ≤ topN (nothing to rank).
 */
@ExtendWith(MockitoExtension.class)
class RAGQualityVerificationTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder mockChatBuilder;

    @Mock
    private EmbeddingModel mockEmbeddingModel;

    private HyDEService hydeService;
    private LLMRerankerService reranker;

    @BeforeEach
    void setUp() {
        hydeService = new HyDEService(mockChatBuilder, mockEmbeddingModel);
        reranker = new LLMRerankerService(mockChatBuilder);
    }

    // ── Q1: HyDE embeds the hypothetical doc, not the raw query ─────────────

    @Test
    @DisplayName("Q1a: HyDE embeds the LLM-generated hypothetical document, not the raw user query")
    void hyde_embeds_hypotheticalDoc_notRawQuery() {
        String rawQuery = "I feel like nothing will ever get better";
        String hypotheticalDoc = "Cognitive restructuring addresses hopelessness by identifying distortions.";

        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn(hypotheticalDoc);
        when(mockEmbeddingModel.embed(hypotheticalDoc))
                .thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        hydeService.expandQuery(rawQuery);

        // Embedding was called with the hypothetical text, NOT the raw query
        verify(mockEmbeddingModel).embed(hypotheticalDoc);
        verify(mockEmbeddingModel, never()).embed(rawQuery);
    }

    @Test
    @DisplayName("Q1b: HyDE expandQuery returns float list matching the embedding output")
    void hyde_expandQuery_returnsCorrectVector() {
        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Some hypothetical CBT document.");
        when(mockEmbeddingModel.embed(anyString()))
                .thenReturn(new float[]{0.5f, -0.3f, 0.8f});

        List<Float> vector = hydeService.expandQuery("I keep failing");

        assertThat(vector).containsExactly(0.5f, -0.3f, 0.8f);
    }

    // ── Q2: Re-ranker parses clean LLM response correctly ───────────────────

    @Test
    @DisplayName("Q2: Re-ranker parses '3,1,2' and returns IDs in that relevance order")
    void reranker_cleanResponse_returnsCorrectOrder() {
        // 5 candidates, topN=3 → LLM is invoked (5 > 3)
        List<String> ids = List.of("CBT-001", "CBT-002", "CBT-003", "CBT-004", "CBT-005");
        List<String> docs = List.of("doc A", "doc B", "doc C", "doc D", "doc E");

        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("3,1,2");

        List<String> result = reranker.rerank("I feel hopeless", docs, ids, 3);

        // Doc 3 → CBT-003, Doc 1 → CBT-001, Doc 2 → CBT-002
        assertThat(result).containsExactly("CBT-003", "CBT-001", "CBT-002");
    }

    // ── Q3: Falls back to original order when LLM returns fewer indices ──────

    @Test
    @DisplayName("Q3: Re-ranker fills remaining slots from original RRF order when LLM returns fewer indices")
    void reranker_partialResponse_fillsFromOriginalOrder() {
        List<String> ids = List.of("CBT-A", "CBT-B", "CBT-C", "CBT-D");
        List<String> docs = List.of("d1", "d2", "d3", "d4");

        // LLM only returns 2 numbers but topN=3
        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("4,2");

        List<String> result = reranker.rerank("feeling anxious", docs, ids, 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo("CBT-D"); // rank 1 from LLM (index 4)
        assertThat(result.get(1)).isEqualTo("CBT-B"); // rank 2 from LLM (index 2)
        assertThat(result.get(2)).isEqualTo("CBT-A"); // fallback: first not yet included
    }

    // ── Q4: Handles noisy LLM output ────────────────────────────────────────

    @Test
    @DisplayName("Q4: Re-ranker extracts document numbers from prose-wrapped LLM response")
    void reranker_noisyResponse_extractsNumbersCorrectly() {
        List<String> ids = List.of("X-1", "X-2", "X-3", "X-4", "X-5");
        List<String> docs = List.of("d1", "d2", "d3", "d4", "d5");

        // LLM wraps the answer in prose — should still parse correctly
        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("The most relevant documents are: 2, then 3, then 1.");

        List<String> result = reranker.rerank("I feel stuck", docs, ids, 3);

        assertThat(result).containsExactly("X-2", "X-3", "X-1");
    }

    // ── Q5: Skips LLM when candidates ≤ topN ────────────────────────────────

    @Test
    @DisplayName("Q5: Re-ranker skips LLM call when candidate count is ≤ topN")
    void reranker_fewCandidates_noLLMCall() {
        List<String> ids = List.of("A", "B");
        List<String> docs = List.of("d1", "d2");

        // 2 candidates, topN=3 → early return, no LLM needed
        List<String> result = reranker.rerank("query", docs, ids, 3);

        verify(mockChatBuilder, never()).build();
        assertThat(result).containsExactlyInAnyOrder("A", "B");
    }

    // ── parseRankedIds edge-case unit tests ──────────────────────────────────

    @Test
    @DisplayName("parseRankedIds: out-of-range index is silently ignored")
    void parseRankedIds_outOfRangeIndex_ignored() {
        LLMRerankerService svc = new LLMRerankerService(mockChatBuilder);
        List<String> ids = List.of("D1", "D2");

        // Index 5 is out of range for a 2-element list
        List<String> result = svc.parseRankedIds("5,1", ids, 2);

        assertThat(result).doesNotContainNull();  // no nulls from bad indices
        assertThat(result).contains("D1");
    }

    @Test
    @DisplayName("parseRankedIds: duplicate indices produce no duplicates in output")
    void parseRankedIds_duplicateIndex_deduped() {
        LLMRerankerService svc = new LLMRerankerService(mockChatBuilder);
        List<String> ids = List.of("D1", "D2", "D3");

        List<String> result = svc.parseRankedIds("1,1,2", ids, 3);

        long distinctCount = result.stream().distinct().count();
        assertThat(distinctCount).isEqualTo(result.size());
    }
}
