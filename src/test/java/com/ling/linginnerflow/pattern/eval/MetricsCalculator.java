package com.ling.linginnerflow.pattern.eval;

import java.util.Set;
import java.util.stream.Collectors;

public class MetricsCalculator {
    public MetricReport score(Set<PredictedPattern> predicted, GTPersona truth) {
        Set<PredictedPattern> trueSet = trueSet(truth);
        Set<PredictedPattern> decoySet = labelSet(truth.decoyPatterns());
        long trueHits = hits(predicted, trueSet);
        long decoyHits = hits(predicted, decoySet);

        double precision = trueHits / (double) Math.max(1, predicted.size());
        double recall = trueHits / (double) Math.max(1, trueSet.size());
        double f1 = precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
        double hardNegativeFPR = decoyHits / (double) Math.max(1, decoySet.size());

        return new MetricReport(precision, recall, f1, hardNegativeFPR);
    }

    public RecallRetention recallRetention(
            GTPersona truth,
            Set<PredictedPattern> afterRecallStage,
            Set<PredictedPattern> afterRetrievalGate,
            Set<PredictedPattern> afterVerifierChain
    ) {
        Set<PredictedPattern> trueSet = trueSet(truth);
        return new RecallRetention(
                1.0,
                fractionRetained(afterRecallStage, trueSet),
                fractionRetained(afterRetrievalGate, trueSet),
                fractionRetained(afterVerifierChain, trueSet));
    }

    private double fractionRetained(Set<PredictedPattern> predicted, Set<PredictedPattern> trueSet) {
        return hits(predicted, trueSet) / (double) Math.max(1, trueSet.size());
    }

    private long hits(Set<PredictedPattern> predicted, Set<PredictedPattern> target) {
        return predicted.stream()
                .filter(target::contains)
                .count();
    }

    private Set<PredictedPattern> trueSet(GTPersona truth) {
        return labelSet(truth.truePatterns());
    }

    private Set<PredictedPattern> labelSet(Iterable<GTLabel> labels) {
        return java.util.stream.StreamSupport.stream(labels.spliterator(), false)
                .map(label -> new PredictedPattern(label.patternKey(), label.domain()))
                .collect(Collectors.toSet());
    }
}
