package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class B1_LexicalBaselineTest {

    private static PatternDefinitionLoader loader;

    @BeforeAll
    static void loadDefinitions() {
        loader = new PatternDefinitionLoader();
        loader.load();
    }

    // ── Test 1: 4 records hit people_pleasing cues → predicted ──────────────

    @Test
    void predictsPeoplePleasingWhenFourRecordsHitCues() {
        GTPersona persona = personaWithCorpus(List.of(
                record("我说了yes但其实不想去"),
                record("对不起，我不应该这样"),
                record("不好意思，能帮我一下吗"),
                record("我怕他失望所以没说实话")
        ));

        Set<PredictedPattern> predictions = new B1_LexicalBaseline(loader, 3).predict(persona);

        assertThat(predictions).contains(new PredictedPattern("people_pleasing", Domain.social));
    }

    // ── Test 2: 2 hits below threshold → not predicted ─────────────────────

    @Test
    void doesNotPredictPeoplePleasingWhenHitCountBelowThreshold() {
        GTPersona persona = personaWithCorpus(List.of(
                record("我说了yes但不想去"),
                record("对不起，我不应该这样")
        ));

        Set<PredictedPattern> predictions = new B1_LexicalBaseline(loader, 3).predict(persona);

        assertThat(predictions)
                .extracting(PredictedPattern::patternKey)
                .doesNotContain("people_pleasing");
    }

    // ── Test 3: cues present → predicted at YAML primary_domain ────────────
    // V1 design choice: B1 always uses the pattern's YAML primary_domain (rumination=self),
    // not any domain inferred from the persona's context. Limit: cross-domain signals are lost.

    @Test
    void usesPrimaryDomainFromYamlNotPersonaContext() {
        // rumination: primary_domain=self; cues: "一直在想", "反复想那件事", "脑子里停不下来", etc.
        GTPersona persona = personaWithCorpus(List.of(
                record("一直在想那件事情"),
                record("反复想那件事，停不下来"),
                record("脑子里停不下来"),
                record("keep replaying what happened")
        ));

        Set<PredictedPattern> predictions = new B1_LexicalBaseline(loader, 3).predict(persona);

        assertThat(predictions).contains(new PredictedPattern("rumination", Domain.self));
    }

    // ── Test 4: one record with multiple cues still counts as 1 hit ─────────

    @Test
    void countsEachRecordAtMostOncePerPatternEvenWithMultipleCues() {
        // Single record containing 3 people_pleasing cues → still counts as 1 hit
        GTPersona persona = personaWithCorpus(List.of(
                record("我说了yes，对不起，不好意思真的")
        ));

        Set<PredictedPattern> predictions = new B1_LexicalBaseline(loader, 3).predict(persona);

        assertThat(predictions)
                .extracting(PredictedPattern::patternKey)
                .doesNotContain("people_pleasing"); // only 1 hit, below threshold of 3
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private GTPersona personaWithCorpus(List<CorpusRecord> corpus) {
        return new GTPersona("test", "fixture", List.of(), List.of(), List.of(), corpus);
    }

    private CorpusRecord record(String text) {
        return new CorpusRecord(LocalDate.of(2026, 1, 1), "chat", text);
    }
}
