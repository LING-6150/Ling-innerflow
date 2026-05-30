package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import com.ling.linginnerflow.pattern.retrieval.EvidenceRetrievalService;
import com.ling.linginnerflow.pattern.retrieval.PatternRecallService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class B3_RetrievalNoVerifyBaselineTest {

    @Test
    void predictsPeoplePleasingWhenRecallAndRetrievalFindThreeEmbeddedDocs() {
        PatternDefinitionLoader defs = definitions();
        B3_RetrievalNoVerifyBaseline baseline = new B3_RetrievalNoVerifyBaseline(
                null,
                new PatternRecallService(defs),
                new EvidenceRetrievalService(defs),
                defs,
                text -> new float[]{1.0f, 1.0f, 1.0f});

        Set<PredictedPattern> predictions = baseline.predict(peoplePleasingPersona());

        assertThat(predictions).contains(new PredictedPattern(
                "people_pleasing", Domain.valueOf(defs.get("people_pleasing").getPrimaryDomain())));
    }

    @Test
    void predictsNothingWhenStubEmbeddingsAreAllZero() {
        PatternDefinitionLoader defs = definitions();
        B3_RetrievalNoVerifyBaseline baseline = new B3_RetrievalNoVerifyBaseline(
                null,
                new PatternRecallService(defs),
                new EvidenceRetrievalService(defs),
                defs,
                text -> new float[]{0.0f, 0.0f, 0.0f});

        Set<PredictedPattern> predictions = baseline.predict(peoplePleasingPersona());

        assertThat(predictions).isEmpty();
    }

    @Test
    void defaultConstructorKeepsLiveModeDisabledWhenSystemPropertyUnset() {
        String previous = System.getProperty("pattern.eval.b3.live");
        System.clearProperty("pattern.eval.b3.live");
        try {
            PatternDefinitionLoader defs = definitions();
            B3_RetrievalNoVerifyBaseline baseline = new B3_RetrievalNoVerifyBaseline(
                    null,
                    new PatternRecallService(defs),
                    new EvidenceRetrievalService(defs),
                    defs,
                    text -> new float[]{1.0f});

            assertThat(baseline.live()).isFalse();
        } finally {
            if (previous == null) {
                System.clearProperty("pattern.eval.b3.live");
            } else {
                System.setProperty("pattern.eval.b3.live", previous);
            }
        }
    }

    @Disabled("requires live embeddings, see PE-5 issue")
    @Test
    void livePathUsesCorpusAssemblyEmbedding() {
        PatternDefinitionLoader defs = definitions();
        B3_RetrievalNoVerifyBaseline baseline = new B3_RetrievalNoVerifyBaseline(
                null,
                new PatternRecallService(defs),
                new EvidenceRetrievalService(defs),
                defs,
                null,
                true);

        assertThat(baseline.live()).isTrue();
    }

    private PatternDefinitionLoader definitions() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }

    private GTPersona peoplePleasingPersona() {
        return new GTPersona("fixture-b3", null, List.of(), List.of(), List.of(), List.of(
                record(0, "I don't want to upset them, so I agreed even though I was exhausted."),
                record(1, "I don't want to upset them at work, so I keep taking extra tasks."),
                record(2, "I don't want to upset them when friends ask for help, so I cancel my own plans."),
                record(3, "I ignored my own needs so everyone else would feel comfortable."),
                record(4, "I apologized twice for asking a small favor and tried to keep everyone happy.")));
    }

    private CorpusRecord record(int daysAgo, String text) {
        return new CorpusRecord(
                LocalDate.now().minusDays(daysAgo), "chat_message", text);
    }
}
