package com.ling.linginnerflow.pattern.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the offline semantic candidate recall audit logic.
 *
 * The audit is the eval-only diagnostic proposed in the V2.2 design (the first,
 * eval-only PR). It must NOT touch production PatternRecallService and must run
 * fully offline — embeddings are supplied via an {@link SemanticCandidateRecallAudit.EmbeddingFn}
 * seam so tests use a deterministic fake and the real run plugs in the model.
 *
 * These tests exercise the pure logic (cosine + key-level recall + slice
 * counting) with a deterministic bag-of-vocab fake embedding, so they are
 * reproducible and need no LLM.
 */
class SemanticCandidateRecallAuditTest {

    private final SemanticCandidateRecallAudit audit = new SemanticCandidateRecallAudit();

    /** Deterministic fake embedding: counts of a tiny fixed vocabulary. */
    private static final List<String> VOCAB = List.of("yes", "everyone", "replay", "control", "power");

    private final SemanticCandidateRecallAudit.EmbeddingFn fakeEmbed = text -> {
        float[] v = new float[VOCAB.size()];
        String lower = text.toLowerCase();
        for (int i = 0; i < VOCAB.size(); i++) {
            int idx = 0, count = 0;
            while ((idx = lower.indexOf(VOCAB.get(i), idx)) >= 0) { count++; idx += 1; }
            v[i] = count;
        }
        return v;
    };

    private static final Map<String, List<String>> EXEMPLARS = Map.of(
            "people_pleasing", List.of("say yes to everyone"),
            "rumination", List.of("replay replay replay"),
            "covert_control", List.of("control power")
    );

    @Test
    @DisplayName("cosine is 1.0 for identical vectors and 0.0 for orthogonal")
    void cosineBasics() {
        assertThat(SemanticCandidateRecallAudit.cosine(new float[]{1, 0}, new float[]{2, 0}))
                .isCloseTo(1.0, within(1e-6));
        assertThat(SemanticCandidateRecallAudit.cosine(new float[]{1, 0}, new float[]{0, 1}))
                .isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("recall surfaces the semantically matching pattern key, not unrelated ones")
    void recallMatchesByMeaningNotSubstring() {
        // Corpus paraphrases people_pleasing with NO literal cue substring overlap guaranteed,
        // but shares vocab with its exemplar.
        List<String> corpus = List.of("i always say yes when everyone asks");
        Set<String> recalled = audit.recall(corpus, EXEMPLARS, fakeEmbed, 3, 0.1);
        assertThat(recalled).contains("people_pleasing");
        assertThat(recalled).doesNotContain("rumination");
    }

    @Test
    @DisplayName("tau gate: a high threshold abstains on a partial (sub-1.0 cosine) match")
    void highTauAbstains() {
        // Shares only "yes" with the people_pleasing exemplar [yes,everyone] -> cosine ~0.707,
        // so a 0.999 gate abstains while a 0.5 gate still recalls it.
        List<String> corpus = List.of("i say yes a lot");
        assertThat(audit.recall(corpus, EXEMPLARS, fakeEmbed, 3, 0.999)).isEmpty();
        assertThat(audit.recall(corpus, EXEMPLARS, fakeEmbed, 3, 0.5)).contains("people_pleasing");
    }

    @Test
    @DisplayName("topK caps the number of recalled keys")
    void topKCaps() {
        List<String> corpus = List.of("say yes everyone control power replay");
        Set<String> top1 = audit.recall(corpus, EXEMPLARS, fakeEmbed, 1, 0.0);
        assertThat(top1).hasSize(1);
    }

    @Test
    @DisplayName("slice metric counts generated TP and FP at key level")
    void sliceMetricCounts() {
        // recalled people_pleasing (a true key) + covert_control (not true) for a persona
        // whose only true key is people_pleasing -> 1 TP, 1 FP.
        SemanticCandidateRecallAudit.SliceMetric m = audit.scoreSlice(
                Set.of("people_pleasing", "covert_control"),
                Set.of("people_pleasing"));
        assertThat(m.generatedTruePositives()).isEqualTo(1);
        assertThat(m.generatedFalsePositives()).isEqualTo(1);
    }

    @Test
    @DisplayName("decoy slice: any recalled key is a false positive")
    void decoySliceAllFalsePositive() {
        SemanticCandidateRecallAudit.SliceMetric m = audit.scoreSlice(
                Set.of("people_pleasing", "covert_control"),
                Set.of()); // full decoy: no true keys
        assertThat(m.generatedTruePositives()).isZero();
        assertThat(m.generatedFalsePositives()).isEqualTo(2);
    }
}
