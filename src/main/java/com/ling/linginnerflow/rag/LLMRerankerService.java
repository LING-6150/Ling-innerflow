package com.ling.linginnerflow.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * LLM Re-ranker
 *
 * After hybrid retrieval returns a pool of candidates, cosine similarity
 * alone cannot reliably order them by *relevance to the user's situation*.
 * A cross-encoder would be ideal, but since OpenAI is already in use we
 * approximate cross-encoder scoring with a single batched LLM call.
 *
 * The LLM sees both the user query and the document simultaneously (unlike
 * bi-encoder/cosine which encodes them independently), giving it the context
 * to judge fine-grained relevance and return a ranked list of document indices.
 *
 * Cost: 1 LLM call regardless of candidate count.
 */
@Slf4j
@Service
public class LLMRerankerService {

    private final ChatClient.Builder chatClientBuilder;

    // Maximum characters from each document included in the ranking prompt.
    // Keeps the prompt short while giving the LLM enough signal to rank.
    private static final int DOC_SNIPPET_LENGTH = 300;

    public LLMRerankerService(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * Re-ranks the candidate documents against the user query and returns
     * the top-N document IDs in relevance order.
     *
     * @param userQuery    original user query (NOT the HyDE expansion)
     * @param docContents  document texts, parallel to {@code docIds}
     * @param docIds       document IDs, parallel to {@code docContents}
     * @param topN         how many to keep
     * @return reranked IDs (at most topN), highest relevance first
     */
    public List<String> rerank(String userQuery,
                               List<String> docContents,
                               List<String> docIds,
                               int topN) {
        if (docIds.isEmpty()) return List.of();
        if (docIds.size() <= topN) return docIds;

        String prompt = buildRankingPrompt(userQuery, docContents, topN);
        String llmResponse = chatClientBuilder.build().prompt().user(prompt).call().content();
        log.info("[Reranker] LLM raw response: {}", llmResponse);

        return parseRankedIds(llmResponse, docIds, topN);
    }

    private String buildRankingPrompt(String userQuery,
                                      List<String> docs,
                                      int topN) {
        StringBuilder docBlock = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            String snippet = docs.get(i).length() > DOC_SNIPPET_LENGTH
                    ? docs.get(i).substring(0, DOC_SNIPPET_LENGTH) + "…"
                    : docs.get(i);
            docBlock.append(i + 1).append(". ").append(snippet).append("\n\n");
        }

        return """
                A user says: "%s"

                Below are CBT knowledge documents retrieved as candidates.
                Rank the %d most relevant documents for helping this user.

                Documents:
                %s
                Output ONLY a comma-separated list of document numbers (1-based), \
                most relevant first.
                Example: 3,1,5
                Do not include any other text.
                """.formatted(userQuery, topN, docBlock);
    }

    /**
     * Parses the LLM's ranked number list (e.g. "3,1,5") back into document IDs.
     * Falls back gracefully: if the LLM returns fewer than topN valid indices,
     * the remaining positions are filled from the original RRF order.
     */
    List<String> parseRankedIds(String response, List<String> ids, int topN) {
        List<String> ranked = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();

        // Extract any digits that look like document numbers
        String numbersOnly = response.replaceAll("[^0-9,\\s]", " ").trim();
        String[] parts = numbersOnly.split("[,\\s]+");

        for (String part : parts) {
            if (part.isBlank()) continue;
            try {
                int oneBasedIdx = Integer.parseInt(part.trim());
                int zeroIdx = oneBasedIdx - 1;
                if (zeroIdx >= 0 && zeroIdx < ids.size() && seen.add(zeroIdx)) {
                    ranked.add(ids.get(zeroIdx));
                }
            } catch (NumberFormatException ignored) {
            }
            if (ranked.size() >= topN) break;
        }

        // Fill remaining slots from original RRF order (fallback)
        for (int i = 0; i < ids.size() && ranked.size() < topN; i++) {
            if (!ranked.contains(ids.get(i))) {
                ranked.add(ids.get(i));
            }
        }

        log.info("[Reranker] Final ranked IDs: {}", ranked);
        return ranked;
    }
}
