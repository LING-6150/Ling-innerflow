package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GTLabel;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

final class ThresholdSweepSimulator {

    private static final double B2_TIER_A_F1 = 0.386;

    List<Candidate> parseSurfacedCandidates(Path report) throws IOException {
        List<String> lines = Files.readAllLines(report);
        List<Candidate> candidates = new ArrayList<>();
        boolean inTable = false;
        for (String line : lines) {
            if (line.equals("## Surfaced Candidates")) {
                inTable = true;
                continue;
            }
            if (!inTable) {
                continue;
            }
            if (line.startsWith("## ") && !line.equals("## Surfaced Candidates")) {
                break;
            }
            if (!line.startsWith("| ") || line.startsWith("|---") || line.startsWith("| persona |")) {
                continue;
            }
            candidates.add(parseCandidate(line));
        }
        return List.copyOf(candidates);
    }

    List<SweepResult> sweep(List<Candidate> candidates, List<GTPersona> personas) {
        Map<String, GTPersona> personaById = personas.stream()
                .collect(Collectors.toMap(GTPersona::id, persona -> persona, (left, right) -> left, LinkedHashMap::new));
        validateTruePositiveColumns(candidates, personaById);
        List<RuleFamily> families = List.of(
                new RuleFamily("fit", Candidate::fit),
                new RuleFamily("specificity", Candidate::specificity),
                new RuleFamily("fit + specificity", candidate -> candidate.fit() + candidate.specificity()),
                new RuleFamily("fit * specificity", candidate -> candidate.fit() * candidate.specificity()),
                new RuleFamily("min(fit, specificity)", candidate -> Math.min(candidate.fit(), candidate.specificity()))
        );

        List<SweepResult> results = new ArrayList<>();
        for (RuleFamily family : families) {
            for (double threshold : thresholdsFor(family.name())) {
                Set<Candidate> kept = candidates.stream()
                        .filter(candidate -> family.score().applyAsDouble(candidate) >= threshold)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                results.add(score(family.name(), threshold, candidates, kept, personaById));
            }
        }
        return results.stream()
                .sorted(Comparator.comparing(SweepResult::meetsRecoveryCriteria).reversed()
                        .thenComparing(SweepResult::fullDecoyFalsePositives)
                        .thenComparing(SweepResult::tierAF1, Comparator.reverseOrder())
                        .thenComparing(SweepResult::killedTierATruePositives)
                        .thenComparing(SweepResult::rule))
                .toList();
    }

    String report(List<Candidate> candidates, List<GTPersona> tierA, List<GTPersona> tierAH) {
        List<GTPersona> allPersonas = new ArrayList<>();
        allPersonas.addAll(tierA);
        allPersonas.addAll(tierAH);
        List<SweepResult> results = sweep(candidates, allPersonas);
        SweepResult noThreshold = results.stream()
                .filter(result -> result.rule().equals("fit") && result.threshold() == 0.0)
                .findFirst()
                .orElse(results.get(0));
        SweepResult bestSafety = results.stream()
                .filter(result -> result.fullDecoyFalsePositives() <= 2)
                .max(Comparator.comparing(SweepResult::tierAF1)
                        .thenComparing(SweepResult::tierARecall)
                        .thenComparing(SweepResult::keptCount))
                .orElse(results.get(0));
        SweepResult bestF1 = results.stream()
                .max(Comparator.comparing(SweepResult::tierAF1)
                        .thenComparing(result -> -result.fullDecoyFalsePositives()))
                .orElse(results.get(0));

        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2 Threshold Sweep\n\n")
                .append("Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` surfaced candidates.\n\n")
                .append("This is an offline calibration diagnostic. It does not call an LLM and must not be treated as held-out proof.\n\n")
                .append("## Decision Summary\n\n")
                .append("- Candidate source: R1.5 label-biased gate surfaced table.\n")
                .append("- Candidates swept: ").append(candidates.size()).append("\n")
                .append("- Recovery target: Tier A F1 comparable to B2 (`0.386`) while full-decoy false positives stay `<= 2`.\n")
                .append("- Tier A sample size: 6 personas, 12 true labels, 4 true positives present in the R1.5 surfaced set.\n")
                .append("- Recall ceiling: thresholding can only remove R1.5 candidates, so Tier A recall cannot exceed the no-threshold value `")
                .append(fmt(noThreshold.tierARecall())).append("`; this caps post-hoc threshold F1 before the sweep starts.\n")
                .append("- Result: no swept rule meets the full recovery target on this candidate table.\n")
                .append("- Best safety-constrained rule: `").append(bestSafety.rule()).append(" >= ")
                .append(fmt(bestSafety.threshold())).append("` keeps full-decoy FP at ")
                .append(bestSafety.fullDecoyFalsePositives()).append(" with Tier A F1 ")
                .append(fmt(bestSafety.tierAF1())).append(".\n")
                .append("- Best Tier A F1 rule: `").append(bestF1.rule()).append(" >= ")
                .append(fmt(bestF1.threshold())).append("` reaches Tier A F1 ")
                .append(fmt(bestF1.tierAF1())).append(" with full-decoy FP ")
                .append(bestF1.fullDecoyFalsePositives()).append(".\n")
                .append("- Threshold stage cost/latency: `$0.0000` and `0s`; candidate-generation cost/latency comes from the R1.5 source report.\n\n")
                .append("Interpretation: simple post-hoc numeric thresholds over R1.5 `fit` and `specificity` scores can reduce decoy false positives, but they cannot recover candidates that the generator never surfaced. The best point slightly improves over the same-candidate no-threshold baseline (Tier A F1 `")
                .append(fmt(noThreshold.tierAF1())).append("`) while cutting full-decoy FP from ")
                .append(noThreshold.fullDecoyFalsePositives()).append(" to ")
                .append(bestSafety.fullDecoyFalsePositives()).append(", but it remains below the cross-pipeline B2 bar (`0.386`). Differences below roughly `0.05` F1 should not be over-read on this small candidate table. The next step should improve the candidate generator or abstain score, not add Pattern Structure modules.\n\n")
                .append("## Key Operating Points\n\n")
                .append("| point | rule | threshold | Tier A precision | Tier A recall | Tier A F1 | overall abstain rate | full-decoy FP | killed Tier A TP |\n")
                .append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n")
                .append(keyPoint("best safety-constrained F1", bestSafety))
                .append(keyPoint("best Tier A F1", bestF1))
                .append(keyPoint("no threshold baseline", noThreshold))
                .append("\n")
                .append("## Pareto Slice\n\n")
                .append("| rule | threshold | Tier A precision | Tier A recall | Tier A F1 | overall abstain rate | full-decoy FP | killed Tier A TP | kept / candidates | recovery target met |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---|\n");

        results.stream()
                .filter(result -> result.fullDecoyFalsePositives() <= 2 || result.tierAF1() >= 0.333)
                .sorted(Comparator.comparing(SweepResult::tierAF1).reversed()
                        .thenComparing(SweepResult::fullDecoyFalsePositives)
                        .thenComparing(SweepResult::abstainRate)
                        .thenComparing(SweepResult::rule))
                .limit(24)
                .forEach(result -> md.append(row(result)));

        md.append("\n## Full Sweep\n\n")
                .append("Tier A-H F1 is structurally `0.000` in this offline sweep because the R1.5 surfaced set contains no true-positive candidates for the non-decoy human personas; the column is retained only as an audit signal.\n\n")
                .append("| rule | threshold | Tier A precision | Tier A recall | Tier A F1 | Tier A-H F1 | overall abstain rate | full-decoy FP | killed Tier A TP | kept |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        results.forEach(result -> md.append("| ")
                .append(result.rule()).append(" | ")
                .append(fmt(result.threshold())).append(" | ")
                .append(fmt(result.tierAPrecision())).append(" | ")
                .append(fmt(result.tierARecall())).append(" | ")
                .append(fmt(result.tierAF1())).append(" | ")
                .append(fmt(result.tierAHF1())).append(" | ")
                .append(fmt(result.abstainRate())).append(" | ")
                .append(result.fullDecoyFalsePositives()).append(" | ")
                .append(result.killedTierATruePositives()).append(" | ")
                .append(result.keptCount()).append(" |\n"));

        return md.toString();
    }

    private Candidate parseCandidate(String line) {
        String[] columns = line.split("\\|", -1);
        if (columns.length < 8) {
            throw new IllegalArgumentException("Malformed surfaced candidate row: " + line);
        }
        String personaId = columns[1].trim();
        String[] candidateParts = columns[2].trim().split(" / ");
        if (candidateParts.length != 2) {
            throw new IllegalArgumentException("Malformed candidate label: " + columns[2].trim());
        }
        return new Candidate(
                personaId,
                new PredictedPattern(candidateParts[0].trim(), Domain.valueOf(candidateParts[1].trim())),
                "yes".equalsIgnoreCase(columns[3].trim()),
                "yes".equalsIgnoreCase(columns[4].trim()),
                Double.parseDouble(columns[5].trim()),
                Double.parseDouble(columns[6].trim()));
    }

    private List<Double> thresholdsFor(String rule) {
        double max = rule.contains("*") ? 0.50 : 1.00;
        List<Double> thresholds = new ArrayList<>();
        for (int i = 0; i <= Math.round(max / 0.05); i++) {
            thresholds.add(round(i * 0.05));
        }
        return thresholds;
    }

    private SweepResult score(String rule,
                              double threshold,
                              List<Candidate> candidates,
                              Set<Candidate> kept,
                              Map<String, GTPersona> personas) {
        Metrics tierA = metrics(kept, personas.values().stream()
                .filter(persona -> persona.id().startsWith("a-"))
                .toList());
        Metrics tierAH = metrics(kept, personas.values().stream()
                .filter(persona -> persona.id().startsWith("ah-"))
                .toList());
        int fullDecoyFalsePositives = (int) kept.stream()
                .filter(Candidate::fullDecoyFalsePositive)
                .count();
        int tierATrueCandidates = (int) candidates.stream()
                .filter(candidate -> candidate.personaId().startsWith("a-"))
                .filter(candidate -> isTruePositive(candidate, personas))
                .count();
        int tierATrueKept = (int) kept.stream()
                .filter(candidate -> candidate.personaId().startsWith("a-"))
                .filter(candidate -> isTruePositive(candidate, personas))
                .count();
        int abstained = candidates.size() - kept.size();
        int killedTierATruePositives = tierATrueCandidates - tierATrueKept;
        return new SweepResult(rule, threshold, tierA.precision(), tierA.recall(), tierA.f1(),
                tierAH.precision(), tierAH.recall(), tierAH.f1(),
                abstained / (double) Math.max(1, candidates.size()),
                fullDecoyFalsePositives,
                killedTierATruePositives,
                kept.size(),
                candidates.size(),
                tierA.f1() >= B2_TIER_A_F1 && fullDecoyFalsePositives <= 2 && killedTierATruePositives == 0);
    }

    private Metrics metrics(Set<Candidate> kept, List<GTPersona> personas) {
        Map<String, GTPersona> personaById = personas.stream()
                .collect(Collectors.toMap(GTPersona::id, persona -> persona));
        int trueTotal = personas.stream().mapToInt(persona -> persona.truePatterns().size()).sum();
        int predicted = 0;
        int trueHits = 0;
        for (Candidate candidate : kept) {
            GTPersona persona = personaById.get(candidate.personaId());
            if (persona == null) {
                continue;
            }
            predicted++;
            if (trueSet(persona).contains(candidate.prediction())) {
                trueHits++;
            }
        }
        double precision = trueHits / (double) Math.max(1, predicted);
        double recall = trueHits / (double) Math.max(1, trueTotal);
        double f1 = precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
        return new Metrics(precision, recall, f1);
    }

    private Set<PredictedPattern> trueSet(GTPersona persona) {
        return persona.truePatterns().stream()
                .map(this::toPrediction)
                .collect(Collectors.toSet());
    }

    private PredictedPattern toPrediction(GTLabel label) {
        return new PredictedPattern(label.patternKey(), label.domain());
    }

    private void validateTruePositiveColumns(List<Candidate> candidates, Map<String, GTPersona> personaById) {
        for (Candidate candidate : candidates) {
            boolean groundTruthPositive = isTruePositive(candidate, personaById);
            if (candidate.truePositive() != groundTruthPositive) {
                throw new IllegalStateException("Surfaced-candidate true-positive column disagrees with ground truth for "
                        + candidate.personaId() + " / " + candidate.prediction());
            }
        }
    }

    private boolean isTruePositive(Candidate candidate, Map<String, GTPersona> personaById) {
        GTPersona persona = personaById.get(candidate.personaId());
        if (persona == null) {
            return false;
        }
        return trueSet(persona).contains(candidate.prediction());
    }

    private String row(SweepResult result) {
        return "| " + result.rule() + " | "
                + fmt(result.threshold()) + " | "
                + fmt(result.tierAPrecision()) + " | "
                + fmt(result.tierARecall()) + " | "
                + fmt(result.tierAF1()) + " | "
                + fmt(result.abstainRate()) + " | "
                + result.fullDecoyFalsePositives() + " | "
                + result.killedTierATruePositives() + " | "
                + result.keptCount() + "/" + result.candidateCount() + " | "
                + (result.meetsRecoveryCriteria() ? "yes" : "no") + " |\n";
    }

    private String keyPoint(String label, SweepResult result) {
        return "| " + label + " | "
                + result.rule() + " | "
                + fmt(result.threshold()) + " | "
                + fmt(result.tierAPrecision()) + " | "
                + fmt(result.tierARecall()) + " | "
                + fmt(result.tierAF1()) + " | "
                + fmt(result.abstainRate()) + " | "
                + result.fullDecoyFalsePositives() + " | "
                + result.killedTierATruePositives() + " |\n";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    record Candidate(
            String personaId,
            PredictedPattern prediction,
            boolean truePositive,
            boolean fullDecoyFalsePositive,
            double fit,
            double specificity
    ) {
    }

    record SweepResult(
            String rule,
            double threshold,
            double tierAPrecision,
            double tierARecall,
            double tierAF1,
            double tierAHPrecision,
            double tierAHRecall,
            double tierAHF1,
            double abstainRate,
            int fullDecoyFalsePositives,
            int killedTierATruePositives,
            int keptCount,
            int candidateCount,
            boolean meetsRecoveryCriteria
    ) {
    }

    private record RuleFamily(String name, ToDoubleFunction<Candidate> score) {
    }

    private record Metrics(double precision, double recall, double f1) {
    }
}
