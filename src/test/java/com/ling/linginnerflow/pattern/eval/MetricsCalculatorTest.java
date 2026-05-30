package com.ling.linginnerflow.pattern.eval;

import com.ling.linginnerflow.pattern.domain.Domain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsCalculatorTest {
    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    void scoreComputesExpectedMetricValues() {
        MetricReport report = calculator.score(Set.of(
                new PredictedPattern("rumination", Domain.self),
                new PredictedPattern("avoidance", Domain.self),
                new PredictedPattern("perfectionism", Domain.work)
        ), truth());

        assertThat(report.precision()).isCloseTo(1.0 / 3.0, withinThreeDecimals());
        assertThat(report.recall()).isCloseTo(0.5, withinThreeDecimals());
        assertThat(report.f1()).isCloseTo(0.4, withinThreeDecimals());
        assertThat(report.hardNegativeFPR()).isCloseTo(0.5, withinThreeDecimals());
    }

    @Test
    void scoreRequiresPatternKeyAndDomainToMatch() {
        MetricReport report = calculator.score(Set.of(
                new PredictedPattern("rumination", Domain.work)
        ), truth());

        assertThat(report.precision()).isZero();
        assertThat(report.recall()).isZero();
    }

    @Test
    void scoreUsesSafeDenominatorsForEmptyInputs() {
        GTPersona emptyTruth = new GTPersona("empty", "fixture", List.of(), List.of(), List.of(), List.of());

        MetricReport report = calculator.score(Set.of(), emptyTruth);

        assertThat(report.precision()).isZero();
        assertThat(report.recall()).isZero();
        assertThat(report.f1()).isZero();
        assertThat(report.hardNegativeFPR()).isZero();
    }

    @Test
    void recallRetentionReportsMonotoneStageFractions() {
        RecallRetention retention = calculator.recallRetention(
                truth(),
                Set.of(
                        new PredictedPattern("rumination", Domain.self),
                        new PredictedPattern("people_pleasing", Domain.family)),
                Set.of(new PredictedPattern("rumination", Domain.self)),
                Set.of()
        );

        assertThat(retention.s0()).isEqualTo(1.0);
        assertThat(retention.s2()).isEqualTo(1.0);
        assertThat(retention.s3()).isEqualTo(0.5);
        assertThat(retention.s4()).isEqualTo(0.0);
        assertThat(retention.s0()).isGreaterThanOrEqualTo(retention.s2());
        assertThat(retention.s2()).isGreaterThanOrEqualTo(retention.s3());
        assertThat(retention.s3()).isGreaterThanOrEqualTo(retention.s4());
    }

    private GTPersona truth() {
        return new GTPersona(
                "fixture",
                "fixture",
                List.of(
                        new GTLabel("rumination", Domain.self, "medium", "note"),
                        new GTLabel("people_pleasing", Domain.family, "medium", "note")),
                List.of(
                        new GTLabel("avoidance", Domain.self, null, "why not"),
                        new GTLabel("perfectionism", Domain.self, null, "why not")),
                List.of(),
                List.of());
    }

    private org.assertj.core.data.Offset<Double> withinThreeDecimals() {
        return org.assertj.core.data.Offset.offset(0.001);
    }
}
