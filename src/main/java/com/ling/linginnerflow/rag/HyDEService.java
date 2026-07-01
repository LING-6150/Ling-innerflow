package com.ling.linginnerflow.rag;

import com.ling.linginnerflow.config.Observations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * HyDE (Hypothetical Document Embedding)
 *
 * Problem: user inputs like "I feel hopeless" embed into a very different
 * vector space than CBT knowledge base entries like "Cognitive restructuring
 * addresses helplessness schemas by...". Cosine similarity is therefore low
 * even when the content is highly relevant.
 *
 * Solution: use an LLM to generate a hypothetical CBT document that *would*
 * answer the user's concern, then embed that document instead of the raw
 * user query. The hypothetical document uses the same vocabulary and style
 * as the knowledge base, so its vector is much closer to the real matches.
 */
@Slf4j
@Service
public class HyDEService {

    private final ChatClient.Builder chatClientBuilder;
    private final EmbeddingModel embeddingModel;
    private final Observations observations;

    public HyDEService(ChatClient.Builder chatClientBuilder,
                       EmbeddingModel embeddingModel,
                       Observations observations) {
        this.chatClientBuilder = chatClientBuilder;
        this.embeddingModel = embeddingModel;
        this.observations = observations;
    }

    /**
     * Expands the user query into a hypothetical CBT document vector.
     * One LLM call + one embedding call.
     *
     * @param userQuery raw user input
     * @return vector of the hypothetical document (same dimension as the index)
     */
    public List<Float> expandQuery(String userQuery) {
        String hypothetical = generateHypotheticalDocument(userQuery);
        log.info("[HyDE] Hypothetical doc: {}", hypothetical);
        return toFloatList(embeddingModel.embed(hypothetical));
    }

    /**
     * Generates a hypothetical CBT knowledge-base entry for the given user input.
     * The prompt is deliberately written in clinical knowledge-base style so that
     * the resulting embedding sits close to real CBT documents in vector space.
     */
    String generateHypotheticalDocument(String userQuery) {
        String prompt = """
                You are a CBT knowledge base.
                A user says: "%s"

                Write a 2–3 sentence clinical knowledge-base entry describing:
                1. The cognitive distortion or emotional pattern being expressed.
                2. The recommended CBT intervention or technique.

                Write in the style of a reference document (not a reply to the user).
                Output ONLY the entry text, no prefix or label.
                """.formatted(userQuery);

        observations.tagPrompt("rag.hyde", "v1");
        return chatClientBuilder.build().prompt().user(prompt).call().content();
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) list.add(f);
        return list;
    }
}
