package com.ling.linginnerflow.pattern.validation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineRuleSimulationTest {
    private static final Path INPUT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path OUTPUT = Path.of("eval/OFFLINE_RULE_SIMULATION.md");

    @Test
    void simulateNumericRulesFromR15Report() throws IOException {
        List<Candidate> candidates = parseSurfacedCandidates(Files.readString(INPUT));
        assertThat(candidates).isNotEmpty();

        List<RuleResult> results = new ArrayList<>();
        for (double threshold = 0.0; threshold <= 1.0001; threshold += 0.1) {
            double value = round(threshold);
            results.add(evaluate("fit >= " + fmt(value), candidates, c -> c.fit() >= value));
            results.add(evaluate("specificity >= " + fmt(value), candidates, c -> c.specificity() >= value));
        }
        for (double threshold = 0.0; threshold <= 1.0001; threshold += 0.05) {
            double value = round(threshold);
            results.add(evaluate("fit * specificity >= " + fmt(value), candidates, c -> c.fit() * c.specificity() >= value));
        }
        for (double threshold = 0.0; threshold <= 2.0001; threshold += 0.1) {
            double value = round(threshold);
            results.add(evaluate("fit + specificity >= " + fmt(value), candidates, c -> c.fit() + c.specificity() >= value));
        }

        writeReport(candidates, pareto(results));
    }

    private List<Candidate> parseSurfacedCandidates(String markdown) {
        int start = markdown.indexOf("## Surfaced Candidates");
        int end = markdown.indexOf("## Killed True Positives");
        if (start < 0 || end < start) {
            return List.of();
        }
        String section = markdown.substring(start, end);
        List<Candidate> candidates = new ArrayList<>();
        for (String line : section.split("\\R")) {
            if (!line.startsWith("| ") || line.startsWith("|---") || line.contains("persona | candidate")) {
                continue;
            }
            String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
            if (cells.length < 7) {
                continue;
            }
            candidates.add(new Candidate(
                    cells[0].trim(),
                    cells[1].trim(),
                    "yes".equalsIgnoreCase(cells[2].trim()),
                    "yes".equalsIgnoreCase(cells[3].trim()),
                    Double.parseDouble(cells[4].trim()),
                    Double.parseDouble(cells[5].trim())));
        }
        return List.copyOf(candidates);
    }

    private RuleResult evaluate(String name, List<Candidate> candidates, Rule rule) {
        int tierATrueBefore = (int) candidates.stream().filter(Candidate::tierA).filter(Candidate::truePositive).count();
        int tierATrueKept = (int) candidates.stream().filter(Candidate::tierA).filter(Candidate::truePositive).filter(rule::keep).count();
        int tierAKilled = tierATrueBefore - tierATrueKept;
        int tierALabelKept = (int) candidates.stream().filter(Candidate::tierA).filter(rule::keep).count();
        int fullDecoyFpKept = (int) candidates.stream().filter(Candidate::fullDecoyFp).filter(rule::keep).count();
        int totalKept = (int) candidates.stream().filter(rule::keep).count();
        return new RuleResult(name, tierATrueKept, tierATrueBefore, tierAKilled, tierALabelKept, fullDecoyFpKept, totalKept);
    }

    private List<RuleResult> pareto(List<RuleResult> results) {
        return results.stream()
                .filter(result -> result.tierATrueKept() == result.tierATrueBefore())
                .sorted(Comparator
                        .comparingInt(RuleResult::fullDecoyFpKept)
                        .thenComparing(Comparator.comparingInt(RuleResult::tierALabelKept).reversed())
                        .thenComparing(RuleResult::rule))
                .limit(30)
                .toList();
    }

    private void writeReport(List<Candidate> candidates, List<RuleResult> results) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# Offline Rule Simulation\n\n")
                .append("Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md`\n\n")
                .append("This report simulates pre-declared numeric gates over the R1.5 surfaced candidates. It does not call an LLM and must not be treated as held-out validation.\n\n")
                .append("## Candidate Summary\n\n")
                .append("- surfaced candidates: ").append(candidates.size()).append('\n')
                .append("- Tier A true positives before rule: ").append(candidates.stream().filter(Candidate::tierA).filter(Candidate::truePositive).count()).append('\n')
                .append("- full-decoy false positives before rule: ").append(candidates.stream().filter(Candidate::fullDecoyFp).count()).append("\n\n")
                .append("## Pareto Candidates\n\n")
                .append("Filtered to rules that keep all Tier A true positives.\n\n")
                .append("| rule | Tier A TP kept | Tier A killed TP | Tier A labels kept | full-decoy FP kept | total kept |\n")
                .append("|---|---:|---:|---:|---:|---:|\n");
        for (RuleResult result : results) {
            md.append("| ").append(result.rule()).append(" | ")
                    .append(result.tierATrueKept()).append('/').append(result.tierATrueBefore()).append(" | ")
                    .append(result.tierAKilled()).append(" | ")
                    .append(result.tierALabelKept()).append(" | ")
                    .append(result.fullDecoyFpKept()).append(" | ")
                    .append(result.totalKept()).append(" |\n");
        }
        Files.writeString(OUTPUT, md.toString());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private interface Rule {
        boolean keep(Candidate candidate);
    }

    private record Candidate(String persona, String candidate, boolean truePositive, boolean fullDecoyFp,
                             double fit, double specificity) {
        boolean tierA() {
            return persona.startsWith("a-");
        }
    }

    private record RuleResult(String rule, int tierATrueKept, int tierATrueBefore, int tierAKilled,
                              int tierALabelKept, int fullDecoyFpKept, int totalKept) {}
}
