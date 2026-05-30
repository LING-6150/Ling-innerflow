package com.ling.linginnerflow.pattern.scoring;

import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceScorerTest {

    private ConfidenceScorer scorer;

    @BeforeEach
    void setUp() throws Exception {
        scorer = new ConfidenceScorer();
        setDoubleField("wEvidence", 0.50);
        setDoubleField("wRecurrence", 0.30);
        setDoubleField("wRecency", 0.20);
        setDoubleField("surfaceThreshold", 0.60);
    }

    @Test
    @DisplayName("Empty evidence chain scores zero")
    void empty_chain_scores_zero() {
        assertThat(scorer.score(List.of())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Five items across four distinct days with latest today scores max value")
    void five_items_four_distinct_days_today_max_value() {
        List<EvidenceItem> items = evidenceItems(
                daysAgo(0),
                daysAgo(1),
                daysAgo(2),
                daysAgo(3),
                daysAgo(3)
        );

        assertThat(scorer.score(items)).isCloseTo(1.0, withinOneHundredth());
    }

    @Test
    @DisplayName("Fewer items lowers evidence term only")
    void fewer_items_lowers_evidence_term_only() {
        List<EvidenceItem> items = evidenceItems(
                daysAgo(0),
                daysAgo(1),
                daysAgo(2)
        );

        assertThat(scorer.score(items)).isCloseTo(0.73, withinOneHundredth());
    }

    @Test
    @DisplayName("Recency decays by 90 day halflife")
    void recency_decays_by_90_day_halflife() {
        LocalDateTime sixtyThreeDaysAgo = daysAgo(63);
        List<EvidenceItem> items = evidenceItems(
                sixtyThreeDaysAgo,
                sixtyThreeDaysAgo,
                sixtyThreeDaysAgo,
                sixtyThreeDaysAgo,
                sixtyThreeDaysAgo
        );

        assertThat(scorer.score(items)).isCloseTo(0.67, withinOneHundredth());
    }

    @Test
    @DisplayName("Single day evidence lowers recurrence")
    void single_day_evidence_lowers_recurrence() {
        LocalDateTime today = daysAgo(0);
        List<EvidenceItem> items = evidenceItems(today, today, today, today, today);

        assertThat(scorer.score(items)).isCloseTo(0.78, withinOneHundredth());
    }

    @Test
    @DisplayName("Should surface respects threshold")
    void should_surface_respects_threshold() {
        assertThat(scorer.shouldSurface(0.60)).isTrue();
        assertThat(scorer.shouldSurface(0.59)).isFalse();
    }

    @Test
    @DisplayName("Scoring is rounded to two decimals")
    void scoring_is_rounded_to_two_decimals() {
        List<EvidenceItem> items = evidenceItems(
                daysAgo(63),
                daysAgo(63),
                daysAgo(63),
                daysAgo(63),
                daysAgo(63)
        );

        double score = scorer.score(items);

        assertThat(Math.abs(score * 100.0 - Math.rint(score * 100.0))).isLessThan(1e-9);
    }

    @Test
    @DisplayName("Implementation does not expose a strength term")
    void no_strength_term_in_implementation() {
        Stream<String> reflectedNames = Stream.concat(
                Arrays.stream(ConfidenceScorer.class.getDeclaredFields()).map(Field::getName),
                Arrays.stream(ConfidenceScorer.class.getDeclaredMethods()).map(Method::getName)
        );

        assertThat(reflectedNames.map(String::toLowerCase)).noneMatch(name -> name.contains("strength"));
    }

    private List<EvidenceItem> evidenceItems(LocalDateTime... occurredAtValues) {
        return Arrays.stream(occurredAtValues)
                .map(this::evidenceItem)
                .toList();
    }

    private EvidenceItem evidenceItem(LocalDateTime occurredAt) {
        EvidenceItem item = new EvidenceItem();
        item.setOccurredAt(occurredAt);
        return item;
    }

    private LocalDateTime daysAgo(long days) {
        return LocalDate.now().minusDays(days).atTime(12, 0);
    }

    private void setDoubleField(String fieldName, double value) throws Exception {
        Field field = ConfidenceScorer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setDouble(scorer, value);
    }

    private org.assertj.core.data.Offset<Double> withinOneHundredth() {
        return org.assertj.core.data.Offset.offset(0.01);
    }
}
