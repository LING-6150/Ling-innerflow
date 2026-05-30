package com.ling.linginnerflow.pattern.dedup;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PatternDeduplicatorTest {

    private final RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
    private final PatternDeduplicator deduplicator = new PatternDeduplicator(embeddingModel);

    @Test
    void returns_null_on_empty_existing_list() {
        PatternInstance duplicate = deduplicator.findDuplicate(
                "self-critical achievement summary",
                "self_criticism",
                List.of());

        assertThat(duplicate).isNull();
    }

    @Test
    void above_threshold_different_key_is_duplicate() {
        PatternInstance existing = instance(
                "existing-1",
                "worth_through_achievement",
                "I only feel okay when I outperform everyone at work");
        embeddingModel.returnVectors(List.of(
                unitX(),
                vectorWithCosine(0.95f)));

        PatternInstance duplicate = deduplicator.findDuplicate(
                "I feel acceptable only when I achieve more than others",
                "self_criticism",
                List.of(existing));

        assertThat(duplicate).isSameAs(existing);
    }

    @Test
    void above_threshold_same_key_is_NOT_duplicate() {
        PatternInstance existing = instance(
                "existing-1",
                "self_criticism",
                "I only feel okay when I outperform everyone at work");

        PatternInstance duplicate = deduplicator.findDuplicate(
                "I feel acceptable only when I achieve more than others",
                "self_criticism",
                List.of(existing));

        assertThat(duplicate).isNull();
        assertThat(embeddingModel.calls()).isZero();
    }

    @Test
    void exactly_at_threshold_counts_as_match() {
        PatternInstance existing = instance(
                "existing-1",
                "worth_through_achievement",
                "I only feel okay when I outperform everyone at work");
        embeddingModel.returnVectors(List.of(
                unitX(),
                vectorWithCosine(0.88f)));

        PatternInstance duplicate = deduplicator.findDuplicate(
                "I feel acceptable only when I achieve more than others",
                "self_criticism",
                List.of(existing));

        assertThat(duplicate).isSameAs(existing);
    }

    @Test
    void below_threshold_no_match() {
        PatternInstance existing = instance(
                "existing-1",
                "worth_through_achievement",
                "I only feel okay when I outperform everyone at work");
        embeddingModel.returnVectors(List.of(
                unitX(),
                vectorWithCosine(0.85f)));

        PatternInstance duplicate = deduplicator.findDuplicate(
                "I feel acceptable only when I achieve more than others",
                "self_criticism",
                List.of(existing));

        assertThat(duplicate).isNull();
    }

    @Test
    void graceful_degrade_on_embedding_exception() {
        PatternInstance existing = instance(
                "existing-1",
                "worth_through_achievement",
                "I only feel okay when I outperform everyone at work");
        embeddingModel.throwOnEmbed(new RuntimeException("embedding service unavailable"));

        PatternInstance duplicate = assertDoesNotThrow(() -> deduplicator.findDuplicate(
                "I feel acceptable only when I achieve more than others",
                "self_criticism",
                List.of(existing)));

        assertThat(duplicate).isNull();
    }

    @Test
    void uses_batch_embed_once() {
        String newSummary = "new summary";
        PatternInstance existing1 = instance("existing-1", "worth_through_achievement", "existing summary 1");
        PatternInstance existing2 = instance("existing-2", "rumination", "existing summary 2");
        embeddingModel.returnVectors(List.of(
                unitX(),
                vectorWithCosine(0.20f),
                vectorWithCosine(0.95f)));

        PatternInstance duplicate = deduplicator.findDuplicate(
                newSummary,
                "self_criticism",
                List.of(existing1, existing2));

        assertThat(duplicate).isSameAs(existing2);
        assertThat(embeddingModel.calls()).isEqualTo(1);
        assertThat(embeddingModel.lastTexts()).containsExactly(
                newSummary,
                existing1.getPersonalizedSummary(),
                existing2.getPersonalizedSummary());
    }

    private PatternInstance instance(String id, String patternKey, String personalizedSummary) {
        PatternInstance instance = new PatternInstance();
        instance.setId(id);
        instance.setUserId("user-1");
        instance.setPatternKey(patternKey);
        instance.setDomain(Domain.work);
        instance.setStatus(PatternStatus.candidate);
        instance.setPersonalizedSummary(personalizedSummary);
        return instance;
    }

    private float[] unitX() {
        return new float[] {1.0f, 0.0f, 0.0f};
    }

    private float[] vectorWithCosine(float cosine) {
        return new float[] {cosine, (float) Math.sqrt(1.0 - cosine * cosine), 0.0f};
    }

    private static class RecordingEmbeddingModel implements EmbeddingModel {
        private List<float[]> vectors = List.of();
        private RuntimeException exception;
        private int calls;
        private List<String> lastTexts = List.of();

        void returnVectors(List<float[]> vectors) {
            this.vectors = vectors;
            this.exception = null;
        }

        void throwOnEmbed(RuntimeException exception) {
            this.exception = exception;
        }

        int calls() {
            return calls;
        }

        List<String> lastTexts() {
            return lastTexts;
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            calls++;
            lastTexts = new ArrayList<>(texts);
            if (exception != null) {
                throw exception;
            }
            return vectors;
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException("not used in these tests");
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            throw new UnsupportedOperationException("not used in these tests");
        }
    }
}
