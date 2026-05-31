package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class ContrastiveStrengthCalculator {
    static final double SUPPORT_THRESHOLD = 0.50;
    static final double MARGIN_THRESHOLD = 0.15;

    private static final Map<String, List<String>> CONFUSABLES = Map.ofEntries(
            Map.entry("avoidance", List.of("conflict_aversion", "emotional_suppression", "perfectionism", "rumination")),
            Map.entry("boundary_difficulty", List.of("people_pleasing", "over_responsibility", "family_pressure", "conflict_aversion")),
            Map.entry("comparison_loop", List.of("worth_through_achievement", "self_criticism", "rumination", "perfectionism")),
            Map.entry("conflict_aversion", List.of("boundary_difficulty", "people_pleasing", "avoidance", "emotional_suppression")),
            Map.entry("emotional_suppression", List.of("conflict_aversion", "avoidance", "self_criticism", "rumination")),
            Map.entry("family_pressure", List.of("boundary_difficulty", "over_responsibility", "people_pleasing", "worth_through_achievement")),
            Map.entry("over_responsibility", List.of("boundary_difficulty", "people_pleasing", "family_pressure", "self_criticism")),
            Map.entry("people_pleasing", List.of("boundary_difficulty", "conflict_aversion", "over_responsibility", "family_pressure")),
            Map.entry("perfectionism", List.of("worth_through_achievement", "avoidance", "self_criticism", "rumination")),
            Map.entry("rumination", List.of("self_criticism", "comparison_loop", "avoidance", "emotional_suppression")),
            Map.entry("self_criticism", List.of("rumination", "worth_through_achievement", "comparison_loop", "perfectionism")),
            Map.entry("worth_through_achievement", List.of("perfectionism", "comparison_loop", "self_criticism", "family_pressure")));

    private final PatternDefinitionLoader definitions;
    private final Vectorizer vectorizer;

    ContrastiveStrengthCalculator(PatternDefinitionLoader definitions, EmbeddingModel embeddingModel) {
        this(definitions, embeddingModel::embed);
    }

    ContrastiveStrengthCalculator(PatternDefinitionLoader definitions, Vectorizer vectorizer) {
        this.definitions = definitions;
        this.vectorizer = vectorizer;
    }

    ContrastiveDecision decide(String patternKey, StandalonePipeline.PatternTrace trace) {
        double supportive = supportiveStrength(trace);
        ContrastiveMatch match = contrastiveStrength(patternKey, trace.evidenceItems());
        return decide(supportive, match);
    }

    ContrastiveDecision decide(double supportive, ContrastiveMatch match) {
        double margin = supportive - match.strength();

        if (supportive < SUPPORT_THRESHOLD) {
            return new ContrastiveDecision(false, supportive, match.strength(), margin, match.patternKey(),
                    ContrastiveReason.INSUFFICIENT_SUPPORT);
        }
        if (margin + 1e-9 < MARGIN_THRESHOLD) {
            return new ContrastiveDecision(false, supportive, match.strength(), margin, match.patternKey(),
                    ContrastiveReason.LOW_MARGIN);
        }
        return new ContrastiveDecision(true, supportive, match.strength(), margin, match.patternKey(),
                ContrastiveReason.SURFACE);
    }

    double supportiveStrength(StandalonePipeline.PatternTrace trace) {
        return trace.confidence();
    }

    ContrastiveMatch contrastiveStrength(String patternKey, List<StandalonePipeline.EvidenceTrace> evidenceItems) {
        return confusables(patternKey).stream()
                .map(confusable -> new ContrastiveMatch(confusable, strengthAgainst(confusable, evidenceItems)))
                .max(Comparator.comparingDouble(ContrastiveMatch::strength))
                .orElse(new ContrastiveMatch("", 0.0));
    }

    private double strengthAgainst(String patternKey, List<StandalonePipeline.EvidenceTrace> evidenceItems) {
        List<String> evidence = evidenceItems.stream()
                .map(StandalonePipeline.EvidenceTrace::excerpt)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .toList();
        List<String> anchors = anchors(patternKey);
        double max = 0.0;
        for (String excerpt : evidence) {
            float[] evidenceVector = vectorizer.embed(excerpt);
            for (String anchor : anchors) {
                max = Math.max(max, cosine(evidenceVector, vectorizer.embed(anchor)));
            }
        }
        return max;
    }

    List<String> confusables(String patternKey) {
        return CONFUSABLES.getOrDefault(patternKey, List.of());
    }

    Map<String, List<String>> matrix() {
        return new LinkedHashMap<>(CONFUSABLES);
    }

    private List<String> anchors(String patternKey) {
        PatternDefinition definition = definitions.get(patternKey);
        List<String> anchors = new ArrayList<>();
        if (definition.getEvidenceShapes() != null) {
            anchors.addAll(definition.getEvidenceShapes());
        }
        if (definition.getLexicalCues() != null) {
            anchors.addAll(definition.getLexicalCues());
        }
        return anchors;
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0;
        }
        int length = Math.min(left.length, right.length);
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    interface Vectorizer {
        float[] embed(String text);
    }

    record ContrastiveDecision(boolean surface, double supportiveStrength, double maxContrastiveStrength,
                               double margin, String strongestConfusable, ContrastiveReason reason) {}

    record ContrastiveMatch(String patternKey, double strength) {}

    enum ContrastiveReason {
        SURFACE,
        INSUFFICIENT_SUPPORT,
        LOW_MARGIN
    }
}
