package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import com.ling.linginnerflow.pattern.eval.MetricReport;
import com.ling.linginnerflow.pattern.eval.MetricsCalculator;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("validation")
@EnabledIfSystemProperty(named = "pattern.validation.run", matches = "true")
class V2AbstainValidationRunner {
    private static final String V2_RUNNER = "V2-abstain-r2";
    private static final String SANITY_RUNNER = "V2-abstain-r1.5-sanity";
    private static final String R3_RUNNER = "V2-abstain-r3-quote";
    private static final String R4_RUNNER = "V2-contrastive-r4";
    private static final Path REPORT = Path.of("eval/RESULTS_V2_ABSTAIN_R2.md");
    private static final Path SANITY_REPORT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path R3_REPORT = Path.of("eval/RESULTS_V2_ABSTAIN_R3.md");
    private static final Path R4_REPORT = Path.of("eval/RESULTS_V2_CONTRASTIVE_R4.md");

    private final PatternDefinitionLoader definitions = loadedDefinitions();
    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final MetricsCalculator metrics = new MetricsCalculator();

    @Test
    void runV2AbstainValidation() throws IOException {
        assertTrue(Boolean.getBoolean("pattern.eval.v2.live"),
                "V2 abstain live mode requires -Dpattern.eval.v2.live=true");

        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> tierAH = groundTruth.loadTierAH();
        List<GTPersona> all = concat(tierA, tierAH);

        StandalonePipeline full = StandalonePipeline.create(true);
        StandalonePipeline noVerify = StandalonePipeline.create(false);
        boolean r4 = Boolean.getBoolean("pattern.v2.contrastive.r4");
        if (r4) {
            StandalonePipeline.CountingEmbeddingModel contrastiveEmbedding = StandalonePipeline.createCountingEmbeddingModel();
            ContrastiveStrengthCalculator calculator = new ContrastiveStrengthCalculator(definitions, contrastiveEmbedding);
            List<RunnerResult> rows = new ArrayList<>();
            for (GTPersona persona : all) {
                rows.add(runPipeline("V1-full", persona, full, null));
                rows.add(runPipeline("V1-no-verify", persona, noVerify, null));
                rows.add(runContrastivePipeline(persona, noVerify, calculator, contrastiveEmbedding));
            }
            writeContrastiveReport(tierA, tierAH, rows);
            return;
        }

        boolean sanity = Boolean.getBoolean("pattern.eval.v2.sanity");
        boolean r3 = Boolean.getBoolean("pattern.eval.v2.r3");
        String v2Runner = r3 ? R3_RUNNER : sanity ? SANITY_RUNNER : V2_RUNNER;
        AbstainGate.Mode mode = r3 ? AbstainGate.Mode.QUOTE_VERIFY_R3
                : sanity ? AbstainGate.Mode.SANITY_LABEL_BIAS : AbstainGate.Mode.STRICT_R2;
        AbstainGate gate = new AbstainGate(definitions, StandalonePipeline.createCountingChatModel(), mode);

        List<RunnerResult> rows = new ArrayList<>();
        for (GTPersona persona : all) {
            rows.add(runPipeline("V1-full", persona, full, null));
            rows.add(runPipeline("V1-no-verify", persona, noVerify, null));
            rows.add(runPipeline(v2Runner, persona, noVerify, gate));
        }

        Path report = r3 ? R3_REPORT : sanity ? SANITY_REPORT : REPORT;
        writeReport(tierA, tierAH, rows, v2Runner, report, sanity, r3);
    }

    private RunnerResult runPipeline(String runner, GTPersona persona, StandalonePipeline pipeline, AbstainGate gate) {
        Instant start = Instant.now();
        StandalonePipeline.PipelineResult result = pipeline.predict(persona);
        StandalonePipeline.TokenUsage usage = result.tokenUsage();
        Map<String, AbstainGate.AbstainResult> abstentions = new LinkedHashMap<>();
        Set<PredictedPattern> predicted = result.predictions();

        if (gate != null) {
            StandalonePipeline.TokenUsage beforeGate = gateUsage(gate);
            Set<PredictedPattern> filtered = new LinkedHashSet<>();
            for (PredictedPattern prediction : result.predictions().stream().sorted(predictionComparator()).toList()) {
                StandalonePipeline.PatternTrace trace = result.trace().patterns().get(prediction.patternKey());
                AbstainGate.AbstainResult decision = gate.judge(persona, prediction.patternKey(), trace);
                decision = enforceQuoteVerification(prediction.patternKey(), trace, decision);
                abstentions.put(key(prediction), decision);
                if (decision.decision().surfaced()) {
                    filtered.add(prediction);
                }
            }
            predicted = Set.copyOf(filtered);
            StandalonePipeline.TokenUsage afterGate = gateUsage(gate);
            usage = StandalonePipeline.TokenUsage.sum(usage, diff(afterGate, beforeGate));
        }

        Duration wall = Duration.between(start, Instant.now());
        return new RunnerResult(runner, persona, predicted, metrics.score(predicted, persona), usage, wall,
                result.predictions(), abstentions, Map.of());
    }

    private RunnerResult runContrastivePipeline(GTPersona persona, StandalonePipeline pipeline,
                                                ContrastiveStrengthCalculator calculator,
                                                StandalonePipeline.CountingEmbeddingModel contrastiveEmbedding) {
        Instant start = Instant.now();
        StandalonePipeline.PipelineResult result = pipeline.predict(persona);
        StandalonePipeline.TokenUsage beforeContrastive = contrastiveEmbedding.usage();
        Map<String, ContrastiveStrengthCalculator.ContrastiveDecision> decisions = new LinkedHashMap<>();
        Set<PredictedPattern> filtered = new LinkedHashSet<>();

        for (PredictedPattern prediction : result.predictions().stream().sorted(predictionComparator()).toList()) {
            StandalonePipeline.PatternTrace trace = result.trace().patterns().get(prediction.patternKey());
            ContrastiveStrengthCalculator.ContrastiveDecision decision = calculator.decide(prediction.patternKey(), trace);
            decisions.put(key(prediction), decision);
            if (decision.surface()) {
                filtered.add(prediction);
            }
        }

        StandalonePipeline.TokenUsage afterContrastive = contrastiveEmbedding.usage();
        StandalonePipeline.TokenUsage usage = StandalonePipeline.TokenUsage.sum(
                result.tokenUsage(), diff(afterContrastive, beforeContrastive));
        Duration wall = Duration.between(start, Instant.now());
        Set<PredictedPattern> predicted = Set.copyOf(filtered);
        return new RunnerResult(R4_RUNNER, persona, predicted, metrics.score(predicted, persona), usage, wall,
                result.predictions(), Map.of(), decisions);
    }

    private AbstainGate.AbstainResult enforceQuoteVerification(String patternKey, StandalonePipeline.PatternTrace trace,
                                                               AbstainGate.AbstainResult decision) {
        if (decision.decision() != AbstainDecision.LABEL || decision.matchedEvidenceShape() == null) {
            return decision;
        }
        boolean validShape = definitions.get(patternKey).getEvidenceShapes().contains(decision.matchedEvidenceShape());
        String quote = decision.supportingQuote();
        boolean validQuote = quote != null && !quote.isBlank()
                && trace.evidenceItems().stream().anyMatch(item -> item.excerpt() != null && item.excerpt().contains(quote));
        if (validShape && validQuote) {
            return decision;
        }
        String reason = "R3 quote verification failed: validShape=" + validShape + ", validQuote=" + validQuote;
        return new AbstainGate.AbstainResult(AbstainDecision.INSUFFICIENT_POSITIVE_FIT,
                decision.fitScore(), decision.specificityScore(), reason,
                decision.matchedEvidenceShape(), decision.supportingQuote());
    }

    private void writeReport(List<GTPersona> tierA, List<GTPersona> tierAH, List<RunnerResult> rows,
                             String v2Runner, Path report, boolean sanity, boolean r3) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append(r3 ? "# Pattern Engine V2 Abstain Validation R3 Quote Gate\n\n"
                        : sanity ? "# Pattern Engine V2 Abstain Validation R1.5 Sanity\n\n" : "# Pattern Engine V2 Abstain Validation R2\n\n")
                .append("Generated: ").append(Instant.now()).append("\n\n")
                .append("## Purpose\n\n")
                .append(sanity
                        ? "This eval-only R1.5 sanity run intentionally biases the gate toward LABEL to prove the gate can enter the LABEL branch and to expose the upper bound of false positives.\n\n"
                        : r3
                        ? "This eval-only R3 run keeps the R1.5 label-biased posture but requires every LABEL to pass quote-level evidence verification. `ah-06` is now treated as dev-set diagnostic, not final held-out proof.\n\n"
                        : "This eval-only R2 run tests whether a less conservative OOD / abstain gate can preserve true positives while still reducing full-decoy false positives.\n\n")
                .append("Primary safety target: `ah-05 + ah-06 surfaced false positives <= 2` (stretch: `0`). Anti-cheat target: recover non-zero Tier A recall.\n\n")
                .append("## Summary Metrics\n\n")
                .append("### Tier A\n")
                .append(summaryTable(rows, ids(tierA), v2Runner))
                .append("\n### Tier A-H\n")
                .append(summaryTable(rows, ids(tierAH), v2Runner))
                .append("\n## Full-Decoy Safety\n\n")
                .append(decoyTable(rows, v2Runner))
                .append("\n## Abstain Reason Codes\n\n")
                .append(reasonTable(rows, v2Runner))
                .append("\n## Per-Persona Detail\n\n")
                .append("| runner | persona | true | before gate | surfaced | abstained | before true hits | after true hits | killed true hits | precision | recall | F1 | tokens | cost | wall |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");

        rows.stream().sorted(Comparator.comparing(RunnerResult::personaId).thenComparing(RunnerResult::runner))
                .forEach(row -> md.append("| ").append(row.runner()).append(" | ")
                        .append(row.persona().id()).append(" | ")
                        .append(row.persona().truePatterns().size()).append(" | ")
                        .append(row.beforeGate().size()).append(" | ")
                        .append(row.predicted().size()).append(" | ")
                        .append(row.abstainCount()).append(" | ")
                        .append(row.beforeTrueHits()).append(" | ")
                        .append(row.afterTrueHits()).append(" | ")
                        .append(row.killedTrueHits()).append(" | ")
                        .append(fmt(row.metric().precision())).append(" | ")
                        .append(fmt(row.metric().recall())).append(" | ")
                        .append(fmt(row.metric().f1())).append(" | ")
                        .append(row.usage().totalTokens()).append(" | $")
                        .append(cost(row.usage())).append(" | ")
                        .append(formatDuration(row.wallTime())).append(" |\n"));

        md.append("\n## Prevented Candidates\n\n")
                .append("| persona | candidate | reason | fit | specificity | rationale |\n")
                .append("|---|---|---|---:|---:|---|\n");
        rows.stream()
                .filter(row -> row.runner().equals(v2Runner))
                .sorted(Comparator.comparing(RunnerResult::personaId))
                .forEach(row -> row.abstentions().entrySet().stream()
                        .filter(entry -> !entry.getValue().decision().surfaced())
                        .forEach(entry -> md.append("| ").append(row.persona().id()).append(" | ")
                                .append(entry.getKey()).append(" | ")
                                .append(entry.getValue().decision()).append(" | ")
                                .append(fmt(entry.getValue().fitScore())).append(" | ")
                                .append(fmt(entry.getValue().specificityScore())).append(" | ")
                                .append(escape(entry.getValue().rationale())).append(" |\n")));

        md.append("\n## Surfaced Candidates\n\n")
                .append("| persona | candidate | true positive? | full-decoy FP? | fit | specificity | evidence shape | quote | rationale |\n")
                .append("|---|---|---|---|---:|---:|---|---|---|\n");
        rows.stream()
                .filter(row -> row.runner().equals(v2Runner))
                .sorted(Comparator.comparing(RunnerResult::personaId))
                .forEach(row -> row.predicted().stream()
                        .sorted(predictionComparator())
                        .forEach(candidate -> {
                            AbstainGate.AbstainResult decision = row.abstentions().get(key(candidate));
                            md.append("| ").append(row.persona().id()).append(" | ")
                                    .append(key(candidate)).append(" | ")
                                    .append(row.trueSet().contains(candidate) ? "yes" : "no").append(" | ")
                                    .append(row.persona().truePatterns().isEmpty() ? "yes" : "no").append(" | ")
                                    .append(decision == null ? "" : fmt(decision.fitScore())).append(" | ")
                                    .append(decision == null ? "" : fmt(decision.specificityScore())).append(" | ")
                                    .append(escape(decision == null ? "" : decision.matchedEvidenceShape())).append(" | ")
                                    .append(escape(decision == null ? "" : decision.supportingQuote())).append(" | ")
                                    .append(escape(decision == null ? "" : decision.rationale())).append(" |\n");
                        }));

        md.append("\n## Killed True Positives\n\n")
                .append("| persona | candidate | reason | rationale |\n")
                .append("|---|---|---|---|\n");
        rows.stream()
                .filter(row -> row.runner().equals(v2Runner))
                .sorted(Comparator.comparing(RunnerResult::personaId))
                .forEach(row -> row.beforeGate().stream()
                        .filter(row.trueSet()::contains)
                        .filter(candidate -> !row.predicted().contains(candidate))
                        .sorted(predictionComparator())
                        .forEach(candidate -> {
                            AbstainGate.AbstainResult decision = row.abstentions().get(key(candidate));
                            md.append("| ").append(row.persona().id()).append(" | ")
                                    .append(key(candidate)).append(" | ")
                                    .append(decision == null ? "" : decision.decision()).append(" | ")
                                    .append(escape(decision == null ? "" : decision.rationale())).append(" |\n");
                        }));

        Files.writeString(report, md.toString());
    }

    private void writeContrastiveReport(List<GTPersona> tierA, List<GTPersona> tierAH, List<RunnerResult> rows) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2.1 Contrastive Retrieval R4\n\n")
                .append("Generated: ").append(Instant.now()).append("\n\n")
                .append("## Purpose\n\n")
                .append("This eval-only R4 run tests contrastive retrieval / differential evidence scoring. `ah-06` is a dev-set diagnostic, not final held-out proof.\n\n")
                .append("Hard criteria: Tier A F1 >= 0.300; Tier A killed true positives = 0; total LABEL count >= 12. Soft: ah-05 + ah-06 <= 2.\n\n")
                .append("## Summary Metrics\n\n")
                .append("### Tier A\n")
                .append(summaryTable(rows, ids(tierA), R4_RUNNER))
                .append("\n### Tier A-H\n")
                .append(summaryTable(rows, ids(tierAH), R4_RUNNER))
                .append("\n## Full-Decoy Safety\n\n")
                .append(decoyTable(rows, R4_RUNNER))
                .append("\n## Per-Persona Detail\n\n")
                .append("| runner | persona | true | before gate | surfaced | filtered | before true hits | after true hits | killed true hits | precision | recall | F1 | tokens | cost | wall |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");

        rows.stream().sorted(Comparator.comparing(RunnerResult::personaId).thenComparing(RunnerResult::runner))
                .forEach(row -> md.append("| ").append(row.runner()).append(" | ")
                        .append(row.persona().id()).append(" | ")
                        .append(row.persona().truePatterns().size()).append(" | ")
                        .append(row.beforeGate().size()).append(" | ")
                        .append(row.predicted().size()).append(" | ")
                        .append(row.filteredCount()).append(" | ")
                        .append(row.beforeTrueHits()).append(" | ")
                        .append(row.afterTrueHits()).append(" | ")
                        .append(row.killedTrueHits()).append(" | ")
                        .append(fmt(row.metric().precision())).append(" | ")
                        .append(fmt(row.metric().recall())).append(" | ")
                        .append(fmt(row.metric().f1())).append(" | ")
                        .append(row.usage().totalTokens()).append(" | $")
                        .append(cost(row.usage())).append(" | ")
                        .append(formatDuration(row.wallTime())).append(" |\n"));

        md.append("\n## Surfaced Candidates\n\n")
                .append("| persona | candidate | true positive? | full-decoy FP? | supportive | max contrastive | margin | strongest confusable |\n")
                .append("|---|---|---|---|---:|---:|---:|---|\n");
        rows.stream().filter(row -> row.runner().equals(R4_RUNNER)).sorted(Comparator.comparing(RunnerResult::personaId))
                .forEach(row -> row.predicted().stream().sorted(predictionComparator()).forEach(candidate -> {
                    ContrastiveStrengthCalculator.ContrastiveDecision decision = row.contrastiveDecisions().get(key(candidate));
                    appendContrastiveCandidate(md, row, candidate, decision);
                }));

        md.append("\n## Filtered Candidates\n\n")
                .append("| persona | candidate | true positive? | full-decoy FP? | supportive | max contrastive | margin | strongest confusable | reason |\n")
                .append("|---|---|---|---|---:|---:|---:|---|---|\n");
        rows.stream().filter(row -> row.runner().equals(R4_RUNNER)).sorted(Comparator.comparing(RunnerResult::personaId))
                .forEach(row -> row.beforeGate().stream()
                        .filter(candidate -> !row.predicted().contains(candidate))
                        .sorted(predictionComparator())
                        .forEach(candidate -> {
                            ContrastiveStrengthCalculator.ContrastiveDecision decision = row.contrastiveDecisions().get(key(candidate));
                            appendFilteredContrastiveCandidate(md, row, candidate, decision);
                        }));

        List<RunnerResult> r4TierARows = filter(rows, ids(tierA), R4_RUNNER);
        int labelCount = rows.stream().filter(row -> row.runner().equals(R4_RUNNER)).mapToInt(row -> row.predicted().size()).sum();
        int fullDecoy = surfaced(rows, R4_RUNNER, "ah-05") + surfaced(rows, R4_RUNNER, "ah-06");
        md.append("\n## Conclusion\n\n")
                .append("- Tier A F1: ").append(fmt(avg(r4TierARows, row -> row.metric().f1()))).append("\n")
                .append("- LABEL count: ").append(labelCount).append("\n")
                .append("- Tier A killed true positives: ").append(r4TierARows.stream().mapToLong(RunnerResult::killedTrueHits).sum()).append("\n")
                .append("- Full-decoy surfaced count: ").append(fullDecoy).append("\n")
                .append("- `ah-06` is a dev-set metric in this report, not evidence that V2.1 has solved NPD-style over-labeling. Fresh held-out hard negatives are required for any final safety claim.\n");

        Files.writeString(R4_REPORT, md.toString());
    }

    private void appendContrastiveCandidate(StringBuilder md, RunnerResult row, PredictedPattern candidate,
                                            ContrastiveStrengthCalculator.ContrastiveDecision decision) {
        md.append("| ").append(row.persona().id()).append(" | ")
                .append(key(candidate)).append(" | ")
                .append(row.trueSet().contains(candidate) ? "yes" : "no").append(" | ")
                .append(row.persona().truePatterns().isEmpty() ? "yes" : "no").append(" | ")
                .append(fmt(decision.supportiveStrength())).append(" | ")
                .append(fmt(decision.maxContrastiveStrength())).append(" | ")
                .append(fmt(decision.margin())).append(" | ")
                .append(decision.strongestConfusable()).append(" |\n");
    }

    private void appendFilteredContrastiveCandidate(StringBuilder md, RunnerResult row, PredictedPattern candidate,
                                                    ContrastiveStrengthCalculator.ContrastiveDecision decision) {
        md.append("| ").append(row.persona().id()).append(" | ")
                .append(key(candidate)).append(" | ")
                .append(row.trueSet().contains(candidate) ? "yes" : "no").append(" | ")
                .append(row.persona().truePatterns().isEmpty() ? "yes" : "no").append(" | ")
                .append(fmt(decision.supportiveStrength())).append(" | ")
                .append(fmt(decision.maxContrastiveStrength())).append(" | ")
                .append(fmt(decision.margin())).append(" | ")
                .append(decision.strongestConfusable()).append(" | ")
                .append(decision.reason()).append(" |\n");
    }

    private String summaryTable(List<RunnerResult> rows, Set<String> personaIds, String v2Runner) {
        StringBuilder table = new StringBuilder();
        table.append("| runner | precision | recall | F1 | surfaced/persona | abstain rate | tokens | cost | wall |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (String runner : List.of("V1-full", "V1-no-verify", v2Runner)) {
            List<RunnerResult> selected = filter(rows, personaIds, runner);
            table.append("| ").append(runner).append(" | ")
                    .append(fmt(avg(selected, row -> row.metric().precision()))).append(" | ")
                    .append(fmt(avg(selected, row -> row.metric().recall()))).append(" | ")
                    .append(fmt(avg(selected, row -> row.metric().f1()))).append(" | ")
                    .append(fmt(avg(selected, row -> row.predicted().size()))).append(" | ")
                    .append(fmt(avg(selected, RunnerResult::abstainRate))).append(" | ")
                    .append(sumTokens(selected)).append(" | $")
                    .append(cost(sumUsage(selected))).append(" | ")
                    .append(formatDuration(sumWall(selected))).append(" |\n");
        }
        return table.toString();
    }

    private String decoyTable(List<RunnerResult> rows, String v2Runner) {
        StringBuilder table = new StringBuilder();
        table.append("| runner | ah-05 surfaced | ah-06 surfaced | total | target met? |\n")
                .append("|---|---:|---:|---:|---|\n");
        for (String runner : List.of("V1-full", "V1-no-verify", v2Runner)) {
            int ah05 = surfaced(rows, runner, "ah-05");
            int ah06 = surfaced(rows, runner, "ah-06");
            int total = ah05 + ah06;
            table.append("| ").append(runner).append(" | ")
                    .append(ah05).append(" | ")
                    .append(ah06).append(" | ")
                    .append(total).append(" | ")
                    .append(total <= 2 ? "yes" : "no")
                    .append(" |\n");
        }
        return table.toString();
    }

    private String reasonTable(List<RunnerResult> rows, String v2Runner) {
        Map<AbstainDecision, Integer> counts = new EnumMap<>(AbstainDecision.class);
        rows.stream().filter(row -> row.runner().equals(v2Runner))
                .flatMap(row -> row.abstentions().values().stream())
                .forEach(result -> counts.merge(result.decision(), 1, Integer::sum));
        StringBuilder table = new StringBuilder();
        table.append("| reason | count |\n|---|---:|\n");
        for (AbstainDecision decision : AbstainDecision.values()) {
            table.append("| ").append(decision).append(" | ")
                    .append(counts.getOrDefault(decision, 0)).append(" |\n");
        }
        return table.toString();
    }

    private List<RunnerResult> filter(List<RunnerResult> rows, Set<String> personaIds, String runner) {
        return rows.stream()
                .filter(row -> row.runner().equals(runner))
                .filter(row -> personaIds.contains(row.persona().id()))
                .toList();
    }

    private int surfaced(List<RunnerResult> rows, String runner, String personaId) {
        return rows.stream()
                .filter(row -> row.runner().equals(runner))
                .filter(row -> row.persona().id().equals(personaId))
                .findFirst()
                .map(row -> row.predicted().size())
                .orElse(0);
    }

    private double avg(List<RunnerResult> rows, Metric metric) {
        return rows.stream().mapToDouble(metric::value).average().orElse(0.0);
    }

    private long sumTokens(List<RunnerResult> rows) {
        return rows.stream().map(RunnerResult::usage).mapToLong(StandalonePipeline.TokenUsage::totalTokens).sum();
    }

    private StandalonePipeline.TokenUsage sumUsage(List<RunnerResult> rows) {
        StandalonePipeline.TokenUsage usage = StandalonePipeline.TokenUsage.zero();
        for (RunnerResult row : rows) {
            usage = StandalonePipeline.TokenUsage.sum(usage, row.usage());
        }
        return usage;
    }

    private Duration sumWall(List<RunnerResult> rows) {
        return rows.stream().map(RunnerResult::wallTime).reduce(Duration.ZERO, Duration::plus);
    }

    private StandalonePipeline.TokenUsage gateUsage(AbstainGate gate) {
        try {
            java.lang.reflect.Field field = AbstainGate.class.getDeclaredField("chatModel");
            field.setAccessible(true);
            StandalonePipeline.CountingChatModel model = (StandalonePipeline.CountingChatModel) field.get(gate);
            java.lang.reflect.Method method = StandalonePipeline.CountingChatModel.class.getDeclaredMethod("usage");
            method.setAccessible(true);
            return (StandalonePipeline.TokenUsage) method.invoke(model);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to read abstain gate usage", e);
        }
    }

    private StandalonePipeline.TokenUsage diff(StandalonePipeline.TokenUsage after, StandalonePipeline.TokenUsage before) {
        return new StandalonePipeline.TokenUsage(
                after.chatPromptTokens() - before.chatPromptTokens(),
                after.chatCompletionTokens() - before.chatCompletionTokens(),
                after.embeddingTokens() - before.embeddingTokens());
    }

    private static PatternDefinitionLoader loadedDefinitions() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }

    private List<GTPersona> concat(List<GTPersona> left, List<GTPersona> right) {
        List<GTPersona> all = new ArrayList<>(left);
        all.addAll(right);
        return all;
    }

    private Set<String> ids(List<GTPersona> personas) {
        return personas.stream().map(GTPersona::id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Comparator<PredictedPattern> predictionComparator() {
        return Comparator.comparing(PredictedPattern::patternKey).thenComparing(prediction -> prediction.domain().name());
    }

    private static String key(PredictedPattern prediction) {
        return prediction.patternKey() + " / " + prediction.domain();
    }

    private String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1000) {
            return millis + "ms";
        }
        return fmt(millis / 1000.0) + "s";
    }

    private String cost(StandalonePipeline.TokenUsage usage) {
        double dollars = usage.chatPromptTokens() / 1_000_000.0 * 0.15
                + usage.chatCompletionTokens() / 1_000_000.0 * 0.60
                + usage.embeddingTokens() / 1_000_000.0 * 0.02;
        return BigDecimal.valueOf(dollars).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private interface Metric {
        double value(RunnerResult row);
    }

    private record RunnerResult(
            String runner,
            GTPersona persona,
            Set<PredictedPattern> predicted,
            MetricReport metric,
            StandalonePipeline.TokenUsage usage,
            Duration wallTime,
            Set<PredictedPattern> beforeGate,
            Map<String, AbstainGate.AbstainResult> abstentions,
            Map<String, ContrastiveStrengthCalculator.ContrastiveDecision> contrastiveDecisions) {

        String personaId() {
            return persona.id();
        }

        int abstainCount() {
            return (int) abstentions.values().stream().filter(result -> !result.decision().surfaced()).count();
        }

        double abstainRate() {
            return abstentions.isEmpty() ? 0.0 : abstainCount() / (double) abstentions.size();
        }

        int filteredCount() {
            return Math.max(0, beforeGate.size() - predicted.size());
        }

        Set<PredictedPattern> trueSet() {
            return persona.truePatterns().stream()
                    .map(label -> new PredictedPattern(label.patternKey(), label.domain()))
                    .collect(Collectors.toSet());
        }

        long beforeTrueHits() {
            Set<PredictedPattern> truth = trueSet();
            return beforeGate.stream().filter(truth::contains).count();
        }

        long afterTrueHits() {
            Set<PredictedPattern> truth = trueSet();
            return predicted.stream().filter(truth::contains).count();
        }

        long killedTrueHits() {
            return beforeTrueHits() - afterTrueHits();
        }
    }
}
