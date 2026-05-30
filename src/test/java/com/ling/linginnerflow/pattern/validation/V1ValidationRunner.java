package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.GTLabel;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import com.ling.linginnerflow.pattern.eval.MetricReport;
import com.ling.linginnerflow.pattern.eval.MetricsCalculator;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import com.ling.linginnerflow.pattern.eval.baseline.B0_PriorBaseline;
import com.ling.linginnerflow.pattern.eval.baseline.B1_LexicalBaseline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("validation")
@EnabledIfSystemProperty(named = "pattern.validation.run", matches = "true")
class V1ValidationRunner {
    private static final Path LIVE_REPORT = Path.of("eval/RESULTS_LIVE.md");
    private static final Path DECOY_REPORT = Path.of("eval/RESULTS_DECOY.md");

    private final PatternDefinitionLoader definitions = loadedDefinitions();
    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final MetricsCalculator metrics = new MetricsCalculator();

    @Test
    void runLiveValidation() throws IOException {
        assertTrue(Boolean.getBoolean("pattern.eval.b2.live"), "B2 live mode requires -Dpattern.eval.b2.live=true");

        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> tierAH = groundTruth.loadTierAH();
        List<GTPersona> all = concat(tierA, tierAH);

        Map<String, Double> baseRates = empiricalBaseRates(tierA);
        List<Runner> runners = List.of(
                new BaselineRunner("B0", persona -> new B0_PriorBaseline(42L, baseRates).predict(persona)),
                new BaselineRunner("B1", persona -> new B1_LexicalBaseline(definitions, 3).predict(persona)),
                new LiveB2Runner(definitions),
                new PipelineRunner("full", StandalonePipeline.create(true)),
                new PipelineRunner("full-no-verify", StandalonePipeline.create(false)));

        Map<String, List<RunRow>> rowsByRunner = new LinkedHashMap<>();
        for (Runner runner : runners) {
            List<RunRow> rows = new ArrayList<>();
            for (GTPersona persona : all) {
                rows.add(runner.run(persona, metrics));
            }
            rowsByRunner.put(runner.name(), rows);
        }

        writeLiveReport(tierA, tierAH, rowsByRunner);
        writeDecoyReport(List.of(find(all, "ah-05"), find(all, "ah-06")));
    }

    private void writeLiveReport(List<GTPersona> tierA, List<GTPersona> tierAH, Map<String, List<RunRow>> rowsByRunner) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V1 LIVE Validation\n\n")
                .append("Generated: ").append(Instant.now()).append("\n\n")
                .append("## RQ1 — Full vs Baselines\n\n")
                .append("### Tier A (6 synthetic personas)\n")
                .append(summaryTable(rowsByRunner, ids(tierA), List.of("B0", "B1", "B2", "full"), "Baseline"))
                .append("\n### Tier A-H (5 human personas)\n")
                .append(summaryTable(rowsByRunner, ids(tierAH), List.of("B0", "B1", "B2", "full"), "Baseline"))
                .append("\n### Per-persona breakdown (for full pipeline)\n")
                .append("| persona | true patterns | predicted | matched | F1 |\n")
                .append("|---|---:|---:|---:|---:|\n");

        for (RunRow row : rowsByRunner.get("full")) {
            md.append("| ").append(row.persona().id()).append(" | ")
                    .append(row.trueCount()).append(" | ")
                    .append(row.predicted().size()).append(" | ")
                    .append(row.matched()).append(" | ")
                    .append(fmt(row.metric().f1())).append(" |\n");
        }

        md.append("\n## RQ2 — Verifier Ablation\n\n")
                .append("### Tier A\n")
                .append(ablationTable(rowsByRunner, ids(tierA)))
                .append("\n### Tier A-H\n")
                .append(ablationTable(rowsByRunner, ids(tierAH)))
                .append("\n## Raw cost summary\n\n");

        StandalonePipeline.TokenUsage total = totalUsage(rowsByRunner);
        md.append("Chat tokens total: ").append(total.chatTokens())
                .append("  Embedding tokens total: ").append(total.embeddingTokens()).append("\n\n")
                .append("Total USD (gpt-4o-mini @ $0.15/$0.60 per 1M, text-embedding-3-small @ $0.02 per 1M): $")
                .append(cost(total)).append("\n\n")
                .append("## Implementation notes\n\n")
                .append("- This LIVE validation bypasses Spring Boot and reads the OpenAI API key via `System.getenv` " +
                        "(preferring `MY_OPENAI_KEY`, then `PERSONAL_OPENAI_KEY`, then `OPENAI_API_KEY`).\n")
                .append("- `src/main/java` was not modified; the V1 pipeline is manually assembled in test validation code.\n")
                .append("- Token totals are API metadata totals captured from Spring AI chat/embedding responses.\n\n")
                .append("## Interpretation\n\n")
                .append(interpretation(rowsByRunner, ids(tierA), ids(tierAH)));

        Files.createDirectories(LIVE_REPORT.getParent());
        Files.writeString(LIVE_REPORT, md.toString());
    }

    private void writeDecoyReport(List<GTPersona> personas) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V1 FULL-DECOY Forensics\n\n")
                .append("Generated: ").append(Instant.now()).append("\n\n")
                .append("## RQ3 — FULL-DECOY surfaced patterns\n\n");

        boolean allZero = true;
        for (GTPersona persona : personas) {
            PipelineRunner runner = new PipelineRunner("full", StandalonePipeline.create(true));
            RunRow row = runner.run(persona, metrics);
            if (!row.predicted().isEmpty()) {
                allZero = false;
            }
            md.append("### ").append(persona.id()).append("\n\n")
                    .append(persona.id()).append(" surfaced ").append(row.predicted().size()).append(" patterns.\n\n")
                    .append("#### Trace summary\n")
                    .append("| pattern_key | recall hit | retrieved docs | verified supports | confidence | surfaced |\n")
                    .append("|---|---:|---:|---:|---:|---:|\n");
            row.trace().patterns().values().stream()
                    .filter(trace -> trace.recallHit() || !trace.retrievedDocIds().isEmpty() || !trace.verifierResults().isEmpty() || trace.surface())
                    .forEach(trace -> md.append("| ").append(trace.patternKey()).append(" | ")
                            .append(trace.recallHit()).append(" | ")
                            .append(trace.retrievedDocIds().size()).append(" | ")
                            .append(trace.verifierResults().size()).append(" | ")
                            .append(fmt(trace.confidence())).append(" | ")
                            .append(trace.surface()).append(" |\n"));
            md.append('\n');

            if (row.predicted().isEmpty()) {
                long recalled = row.trace().patterns().values().stream().filter(StandalonePipeline.PatternTrace::recallHit).count();
                md.append("No patterns surfaced. Recall produced ").append(recalled).append(" candidate(s); ")
                        .append(recalled == 0
                                ? "this is no-evidence abstention at recall, not downstream verifier filtering."
                                : "downstream retrieval/verifier/chain/confidence stages filtered the candidates.")
                        .append("\n\n");
            } else {
                for (PredictedPattern prediction : row.predicted().stream().sorted(Comparator.comparing(PredictedPattern::patternKey)).toList()) {
                    StandalonePipeline.PatternTrace trace = row.trace().patterns().get(prediction.patternKey());
                    md.append("#### ").append(prediction.patternKey()).append(" / ").append(prediction.domain()).append("\n\n")
                            .append("- confidence: ").append(fmt(trace.confidence())).append("\n")
                            .append("- decoy why_not: ").append(whyNot(persona, prediction)).append("\n")
                            .append("- judgment: false positive unless the evidence below shows repeated direct self-recognition rather than the answer-key decoy structure.\n\n")
                            .append("Evidence chain:\n");
                    for (StandalonePipeline.EvidenceTrace evidence : trace.evidenceItems()) {
                        md.append("- source `").append(evidence.sourceRef()).append("`: ")
                                .append(escape(evidence.excerpt())).append(" | verbatim=").append(evidence.verbatim())
                                .append(" | interpretation: ").append(escape(evidence.interpretation())).append("\n");
                    }
                    md.append('\n');
                }
            }
        }

        if (allZero) {
            md.append("## Conclusion\n\n")
                    .append("ah-05 surfaced 0 patterns. ah-06 surfaced 0 patterns. Full pipeline correctly abstained on both full-decoy personas. This is the V1.2 R30 + product §10 designed behavior.\n");
        }

        Files.createDirectories(DECOY_REPORT.getParent());
        Files.writeString(DECOY_REPORT, md.toString());
    }

    private String summaryTable(Map<String, List<RunRow>> rowsByRunner, Set<String> personaIds, List<String> names, String firstColumn) {
        StringBuilder md = new StringBuilder("| " + firstColumn + " | Avg Precision | Avg Recall | Avg F1 | Avg HardNegFPR | Tokens used | Wall time |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (String name : names) {
            List<RunRow> rows = filter(rowsByRunner.get(name), personaIds);
            md.append("| ").append(name).append(" | ")
                    .append(fmt(avg(rows, row -> row.metric().precision()))).append(" | ")
                    .append(fmt(avg(rows, row -> row.metric().recall()))).append(" | ")
                    .append(fmt(avg(rows, row -> row.metric().f1()))).append(" | ")
                    .append(fmt(avg(rows, row -> row.metric().hardNegativeFPR()))).append(" | ")
                    .append(rows.stream().map(RunRow::usage).mapToLong(StandalonePipeline.TokenUsage::totalTokens).sum()).append(" | ")
                    .append(formatDuration(rows.stream().map(RunRow::wallTime).reduce(Duration.ZERO, Duration::plus))).append(" |\n");
        }
        return md.toString();
    }

    private String ablationTable(Map<String, List<RunRow>> rowsByRunner, Set<String> personaIds) {
        List<RunRow> full = filter(rowsByRunner.get("full"), personaIds);
        List<RunRow> noVerify = filter(rowsByRunner.get("full-no-verify"), personaIds);
        double fullF1 = avg(full, row -> row.metric().f1());
        double noVerifyF1 = avg(noVerify, row -> row.metric().f1());
        long fullTokens = full.stream().map(RunRow::usage).mapToLong(StandalonePipeline.TokenUsage::totalTokens).sum();
        long noVerifyTokens = noVerify.stream().map(RunRow::usage).mapToLong(StandalonePipeline.TokenUsage::totalTokens).sum();
        Duration fullTime = full.stream().map(RunRow::wallTime).reduce(Duration.ZERO, Duration::plus);
        Duration noVerifyTime = noVerify.stream().map(RunRow::wallTime).reduce(Duration.ZERO, Duration::plus);
        return "| Variant | F1 | Tokens | Wall time |\n"
                + "|---|---:|---:|---:|\n"
                + "| full | " + fmt(fullF1) + " | " + fullTokens + " | " + formatDuration(fullTime) + " |\n"
                + "| full-no-verify | " + fmt(noVerifyF1) + " | " + noVerifyTokens + " | " + formatDuration(noVerifyTime) + " |\n"
                + "| Δ | " + fmt(noVerifyF1 - fullF1) + " | " + (noVerifyTokens - fullTokens) + " | " + formatDuration(noVerifyTime.minus(fullTime)) + " |\n";
    }

    private String interpretation(Map<String, List<RunRow>> rowsByRunner, Set<String> tierAIds, Set<String> tierAHIds) {
        double fullA = avg(filter(rowsByRunner.get("full"), tierAIds), row -> row.metric().f1());
        double b0A = avg(filter(rowsByRunner.get("B0"), tierAIds), row -> row.metric().f1());
        double b1A = avg(filter(rowsByRunner.get("B1"), tierAIds), row -> row.metric().f1());
        double b2A = avg(filter(rowsByRunner.get("B2"), tierAIds), row -> row.metric().f1());
        double fullAH = avg(filter(rowsByRunner.get("full"), tierAHIds), row -> row.metric().f1());
        double b2AH = avg(filter(rowsByRunner.get("B2"), tierAHIds), row -> row.metric().f1());
        double noVerifyA = avg(filter(rowsByRunner.get("full-no-verify"), tierAIds), row -> row.metric().f1());
        double noVerifyAH = avg(filter(rowsByRunner.get("full-no-verify"), tierAHIds), row -> row.metric().f1());
        long decoySurfaced = rowsByRunner.get("full").stream()
                .filter(row -> row.persona().id().equals("ah-05") || row.persona().id().equals("ah-06"))
                .mapToLong(row -> row.predicted().size())
                .sum();
        return "The raw Tier A averages put full at F1 " + fmt(fullA) + " versus B0 " + fmt(b0A)
                + ", B1 " + fmt(b1A) + ", and B2 " + fmt(b2A) + ". The data suggests the full pipeline "
                + comparative(fullA, Math.max(Math.max(b0A, b1A), b2A)) + " the simple baselines on the synthetic set.\n\n"
                + "On Tier A-H, full F1 is " + fmt(fullAH) + " versus B2 " + fmt(b2AH)
                + ". This is the more important read because the sealed personas include adversarial human writing; the data suggests "
                + comparative(fullAH, b2AH) + " B2 on this slice.\n\n"
                + "Removing §4 verification changes F1 from " + fmt(fullA) + " to " + fmt(noVerifyA)
                + " on Tier A and from " + fmt(fullAH) + " to " + fmt(noVerifyAH)
                + " on Tier A-H. That is consistent with verification being "
                + (noVerifyA + noVerifyAH < fullA + fullAH ? "protective for precision/decoys" : "less helpful than expected in this small run")
                + ".\n\n"
                + "For ah-05/ah-06, the full pipeline surfaced " + decoySurfaced
                + " total pattern(s). The separate decoy forensics report should be read as the qualitative safety check for whether abstention came from downstream evidence filtering or from no recall candidates.\n";
    }

    private String comparative(double left, double right) {
        double delta = left - right;
        if (delta > 0.10) return "is clearly above";
        if (delta > 0.02) return "is modestly above";
        if (delta < -0.10) return "is clearly below";
        if (delta < -0.02) return "is modestly below";
        return "is roughly tied with";
    }

    private String whyNot(GTPersona persona, PredictedPattern prediction) {
        return persona.decoyPatterns().stream()
                .filter(label -> label.patternKey().equals(prediction.patternKey()) && label.domain() == prediction.domain())
                .findFirst()
                .map(GTLabel::notes)
                .or(() -> persona.decoyPatterns().stream()
                        .filter(label -> label.patternKey().equals(prediction.patternKey()))
                        .findFirst()
                        .map(GTLabel::notes))
                .orElse("No decoy why_not entry found for this surfaced pair.");
    }

    private Map<String, Double> empiricalBaseRates(List<GTPersona> personas) {
        int denominator = Math.max(1, personas.size());
        return definitions.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key,
                        key -> personas.stream().filter(persona -> persona.truePatterns().stream()
                                .anyMatch(label -> label.patternKey().equals(key))).count() / (double) denominator));
    }

    private StandalonePipeline.TokenUsage totalUsage(Map<String, List<RunRow>> rowsByRunner) {
        return rowsByRunner.values().stream()
                .flatMap(List::stream)
                .map(RunRow::usage)
                .reduce(StandalonePipeline.TokenUsage.zero(), StandalonePipeline.TokenUsage::sum);
    }

    private String cost(StandalonePipeline.TokenUsage usage) {
        double dollars = usage.chatPromptTokens() / 1_000_000.0 * 0.15
                + usage.chatCompletionTokens() / 1_000_000.0 * 0.60
                + usage.embeddingTokens() / 1_000_000.0 * 0.02;
        return BigDecimal.valueOf(dollars).setScale(4, RoundingMode.HALF_UP).toPlainString();
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

    private GTPersona find(List<GTPersona> personas, String id) {
        return personas.stream().filter(persona -> persona.id().equals(id)).findFirst().orElseThrow();
    }

    private Set<String> ids(List<GTPersona> personas) {
        return personas.stream().map(GTPersona::id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<RunRow> filter(List<RunRow> rows, Set<String> ids) {
        return rows.stream().filter(row -> ids.contains(row.persona().id())).toList();
    }

    private int trueCount(GTPersona persona) {
        return persona.truePatterns().size();
    }

    private long matched(Set<PredictedPattern> predicted, GTPersona persona) {
        Set<PredictedPattern> trueSet = persona.truePatterns().stream()
                .map(label -> new PredictedPattern(label.patternKey(), label.domain()))
                .collect(Collectors.toSet());
        return predicted.stream().filter(trueSet::contains).count();
    }

    private String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        String sign = millis < 0 ? "-" : "";
        millis = Math.abs(millis);
        return sign + (millis / 1000) + "." + String.format(java.util.Locale.ROOT, "%03d", millis % 1000) + "s";
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\n", " ").replace("|", "\\|");
    }

    private double avg(List<RunRow> rows, Value value) {
        return rows.stream().mapToDouble(value::get).average().orElse(0.0);
    }

    @FunctionalInterface
    interface Value { double get(RunRow row); }

    interface Runner {
        String name();
        RunRow run(GTPersona persona, MetricsCalculator metrics);
    }

    interface Predictor { Set<PredictedPattern> predict(GTPersona persona); }

    class BaselineRunner implements Runner {
        private final String name;
        private final Predictor predictor;

        BaselineRunner(String name, Predictor predictor) {
            this.name = name;
            this.predictor = predictor;
        }

        @Override public String name() { return name; }

        @Override
        public RunRow run(GTPersona persona, MetricsCalculator metrics) {
            Instant start = Instant.now();
            Set<PredictedPattern> predicted = predictor.predict(persona);
            Duration wall = Duration.between(start, Instant.now());
            return new RunRow(persona, predicted, metrics.score(predicted, persona), StandalonePipeline.TokenUsage.zero(), wall, null,
                    trueCount(persona), matched(predicted, persona));
        }
    }

    class LiveB2Runner implements Runner {
        private final PatternDefinitionLoader definitions;
        private final StandalonePipeline.CountingChatModel chatModel;

        LiveB2Runner(PatternDefinitionLoader definitions) {
            this.definitions = definitions;
            this.chatModel = StandalonePipeline.createCountingChatModel();
        }

        @Override public String name() { return "B2"; }

        @Override
        public RunRow run(GTPersona persona, MetricsCalculator metrics) {
            chatModel.reset();
            Instant start = Instant.now();
            ChatResponse response = chatModel.call(new Prompt(buildPrompt(persona),
                    OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.0).build()));
            String raw = response.getResult() == null || response.getResult().getOutput() == null
                    ? ""
                    : response.getResult().getOutput().getText();
            Set<PredictedPattern> predicted = StandalonePipeline.parsePredictions(raw, definitions);
            Duration wall = Duration.between(start, Instant.now());
            return new RunRow(persona, predicted, metrics.score(predicted, persona), chatModel.usage(), wall, null,
                    trueCount(persona), matched(predicted, persona));
        }

        private String buildPrompt(GTPersona persona) {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are evaluating a fixed pattern taxonomy against one user's corpus.\n")
                    .append("Return only strict JSON with this schema: ")
                    .append("[{\"pattern_key\":\"...\",\"domain\":\"...\"}].\n")
                    .append("Use only these domain values: self, family, intimate, work, social, body.\n")
                    .append("The pattern_key must be one of this closed set; do not invent keys.\n\n")
                    .append("Closed taxonomy:\n");
            definitions.keys().stream().sorted().forEach(key -> appendDefinition(prompt, key));
            prompt.append("\nUser corpus for persona ").append(persona.id()).append(":\n");
            for (com.ling.linginnerflow.pattern.eval.CorpusRecord record : persona.corpus()) {
                prompt.append("- ").append(record.date().format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .append(" [").append(record.type()).append("] ").append(record.text()).append('\n');
            }
            prompt.append("\nIdentify present patterns from the closed taxonomy only. If no pattern is supported, return [].");
            return prompt.toString();
        }

        private void appendDefinition(StringBuilder prompt, String key) {
            PatternDefinition definition = definitions.get(key);
            prompt.append("- pattern_key: ").append(key)
                    .append("; primary_domain: ").append(definition.getPrimaryDomain())
                    .append("; description: ").append(definition.getNeutralDescription())
                    .append('\n');
        }
    }

    class PipelineRunner implements Runner {
        private final String name;
        private final StandalonePipeline pipeline;

        PipelineRunner(String name, StandalonePipeline pipeline) {
            this.name = name;
            this.pipeline = pipeline;
        }

        @Override public String name() { return name; }

        @Override
        public RunRow run(GTPersona persona, MetricsCalculator metrics) {
            Instant start = Instant.now();
            StandalonePipeline.PipelineResult result = pipeline.predict(persona);
            Duration wall = Duration.between(start, Instant.now());
            return new RunRow(persona, result.predictions(), metrics.score(result.predictions(), persona), result.tokenUsage(), wall, result.trace(),
                    trueCount(persona), matched(result.predictions(), persona));
        }
    }

    record RunRow(
            GTPersona persona,
            Set<PredictedPattern> predicted,
            MetricReport metric,
            StandalonePipeline.TokenUsage usage,
            Duration wallTime,
            StandalonePipeline.PipelineTrace trace,
            int trueCount,
            long matched) {}
}
