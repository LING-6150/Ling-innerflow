package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.SourceType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContrastiveStrengthCalculatorTest {
    @Test
    void supportiveStrengthUsesTraceChainStrength() {
        ContrastiveStrengthCalculator calculator = newCalculator(text -> new float[] {1.0f, 0.0f});
        StandalonePipeline.PatternTrace trace = new StandalonePipeline.PatternTrace("self_criticism");
        trace.setConfidence(0.73);

        assertThat(calculator.supportiveStrength(trace)).isEqualTo(0.73);
    }

    @Test
    void contrastiveStrengthReturnsMaxNotAverage() {
        PatternDefinitionLoader definitions = loadedDefinitions();
        String strongAnchor = definitions.get("rumination").getEvidenceShapes().getFirst();
        ContrastiveStrengthCalculator calculator = newCalculator(text -> {
            if (text.equals("evidence") || text.equals(strongAnchor)) {
                return new float[] {1.0f, 0.0f};
            }
            return new float[] {0.0f, 1.0f};
        });

        ContrastiveStrengthCalculator.ContrastiveMatch match = calculator.contrastiveStrength(
                "self_criticism",
                java.util.List.of(new StandalonePipeline.EvidenceTrace(
                        SourceType.journal_entry, "r1", "evidence", true, "interp")));

        assertThat(match.patternKey()).isEqualTo("rumination");
        assertThat(match.strength()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void lowMarginAbstains() {
        ContrastiveStrengthCalculator calculator = newCalculator(text -> new float[] {1.0f});

        ContrastiveStrengthCalculator.ContrastiveDecision decision = calculator.decide(
                0.60,
                new ContrastiveStrengthCalculator.ContrastiveMatch("rumination", 0.50));

        assertThat(decision.surface()).isFalse();
        assertThat(decision.reason()).isEqualTo(ContrastiveStrengthCalculator.ContrastiveReason.LOW_MARGIN);
    }

    @Test
    void sufficientMarginSurfaces() {
        ContrastiveStrengthCalculator calculator = newCalculator(text -> new float[] {1.0f});

        ContrastiveStrengthCalculator.ContrastiveDecision decision = calculator.decide(
                0.60,
                new ContrastiveStrengthCalculator.ContrastiveMatch("rumination", 0.40));

        assertThat(decision.surface()).isTrue();
        assertThat(decision.reason()).isEqualTo(ContrastiveStrengthCalculator.ContrastiveReason.SURFACE);
    }

    @Test
    void insufficientSupportAbstains() {
        ContrastiveStrengthCalculator calculator = newCalculator(text -> new float[] {1.0f});

        ContrastiveStrengthCalculator.ContrastiveDecision decision = calculator.decide(
                0.40,
                new ContrastiveStrengthCalculator.ContrastiveMatch("rumination", 0.00));

        assertThat(decision.surface()).isFalse();
        assertThat(decision.reason()).isEqualTo(ContrastiveStrengthCalculator.ContrastiveReason.INSUFFICIENT_SUPPORT);
    }

    @Test
    void exactMarginThresholdSurfaces() {
        ContrastiveStrengthCalculator calculator = newCalculator(text -> new float[] {1.0f});

        ContrastiveStrengthCalculator.ContrastiveDecision decision = calculator.decide(
                0.50,
                new ContrastiveStrengthCalculator.ContrastiveMatch("rumination", 0.35));

        assertThat(decision.surface()).isTrue();
        assertThat(decision.reason()).isEqualTo(ContrastiveStrengthCalculator.ContrastiveReason.SURFACE);
    }

    @Test
    void matrixContainsExpectedConfusables() {
        ContrastiveStrengthCalculator calculator = newCalculator(text -> new float[] {1.0f});

        Map<String, java.util.List<String>> matrix = calculator.matrix();

        assertThat(matrix.get("worth_through_achievement"))
                .containsExactly("perfectionism", "comparison_loop", "self_criticism", "family_pressure");
    }

    private ContrastiveStrengthCalculator newCalculator(ContrastiveStrengthCalculator.Vectorizer vectorizer) {
        return new ContrastiveStrengthCalculator(loadedDefinitions(), vectorizer);
    }

    private PatternDefinitionLoader loadedDefinitions() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }
}
