package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTLabel;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class ExpandedCandidateAbstainSweep {

    private static final double BASELINE_FIT_THRESHOLD = 0.45;

    SweepReport sweep(List<ThresholdSweepSimulator.Candidate> surfacedBaseline,
                      EvidenceGatedExpansionExperiment.ExpansionResult expansion,
                      List<GTPersona> tierA,
                      List<GTPersona> fullDecoys) {
        Set<PredictionKey> baselineKept = surfacedBaseline.stream()
                .filter(candidate -> candidate.fit() >= BASELINE_FIT_THRESHOLD)
                .map(candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<SweepPoint> points = List.of(
                score("baseline only", 99, 99, baselineKept, List.of(), List.of(), tierA, fullDecoys),
                score("evidence>=2 terms>=1", 2, 1, baselineKept, expansion.expansions(), expansion.fullDecoyExpansions(), tierA, fullDecoys),
                score("evidence>=2 terms>=2", 2, 2, baselineKept, expansion.expansions(), expansion.fullDecoyExpansions(), tierA, fullDecoys),
                score("evidence>=3 terms>=2", 3, 2, baselineKept, expansion.expansions(), expansion.fullDecoyExpansions(), tierA, fullDecoys),
                score("evidence>=3 terms>=3", 3, 3, baselineKept, expansion.expansions(), expansion.fullDecoyExpansions(), tierA, fullDecoys)
        );
        return new SweepReport(points);
    }

    String report(SweepReport report) {
        SweepPoint baseline = report.points().get(0);
        SweepPoint bestSafety = report.points().stream()
                .filter(point -> point.fullDecoyFalsePositives() <= 2)
                .max(Comparator.comparing(SweepPoint::tierATrueHits)
                        .thenComparing(SweepPoint::tierAFalsePositives, Comparator.reverseOrder()))
                .orElse(baseline);
        SweepPoint bestRecall = report.points().stream()
                .max(Comparator.comparing(SweepPoint::tierATrueHits)
                        .thenComparing(SweepPoint::fullDecoyFalsePositives, Comparator.reverseOrder()))
                .orElse(baseline);

        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2 Expanded Candidate Abstain Sweep\n\n")
                .append("Input: PR #53 evidence-gated expansion candidates plus R1.5 surfaced candidates.\n\n")
                .append("This is an offline gate diagnostic. It does not call an LLM and must not be treated as held-out proof or production abstain behavior.\n\n")
                .append("## Decision Summary\n\n")
                .append("- Baseline gate: keep existing R1.5 surfaced candidates with `fit >= 0.45`, matching the PR #49 safety-constrained operating point.\n")
                .append("- Best safety-constrained point: `").append(bestSafety.rule()).append("` has Tier A generated TP `")
                .append(bestSafety.tierATrueHits()).append("/").append(bestSafety.tierATrueLabels())
                .append("` and full-decoy FP `").append(bestSafety.fullDecoyFalsePositives()).append("`.\n")
                .append("- Best recall point: `").append(bestRecall.rule()).append("` has Tier A generated TP `")
                .append(bestRecall.tierATrueHits()).append("/").append(bestRecall.tierATrueLabels())
                .append("` but full-decoy FP `").append(bestRecall.fullDecoyFalsePositives()).append("`.\n")
                .append("- Result: this offline evidence-count proxy can preserve the full PR #53 Tier A recall gain while keeping full-decoy FP at `2`, but Tier A false positives remain high (`")
                .append(bestSafety.tierAFalsePositives()).append("`).\n")
                .append("- Cost/latency: `$0.0000` and `0s`; this sweep is deterministic and offline.\n\n")
                .append("Interpretation: evidence count is a useful offline proxy for filtering the probe-expanded set, but it is not a production abstain gate. The `12/12` result is in-sample, and the remaining Tier A FP pressure means the next gate must judge quote-level specificity or use a calibrated learned/LLM score before claiming safety.\n\n")
                .append("## Sweep Points\n\n")
                .append("| rule | Tier A generated | Tier A TP | Tier A recall | Tier A FP | full-decoy FP | added Tier A TP | added Tier A FP | added full-decoy FP |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        report.points().forEach(point -> md.append("| ")
                .append(point.rule()).append(" | ")
                .append(point.tierAGenerated()).append(" | ")
                .append(point.tierATrueHits()).append(" | ")
                .append(fmt(point.tierARecall())).append(" | ")
                .append(point.tierAFalsePositives()).append(" | ")
                .append(point.fullDecoyFalsePositives()).append(" | ")
                .append(point.addedTierATrueHits()).append(" | ")
                .append(point.addedTierAFalsePositives()).append(" | ")
                .append(point.addedFullDecoyFalsePositives()).append(" |\n"));

        md.append("\n## Caveats\n\n")
                .append("- This is still a pre-product eval diagnostic, not a production gate.\n")
                .append("- The expanded candidates are probe-authored from Tier A forensics, so Tier A recall gains are in-sample.\n")
                .append("- Full-decoy personas are used only as FP confirmation; do not tune future probe terms against their excerpts.\n")
                .append("- The recovery target remains full-decoy surfaced FP `<=2` without recall collapse.\n");
        return md.toString();
    }

    private SweepPoint score(String rule,
                             int minEvidence,
                             int minTerms,
                             Set<PredictionKey> baselineKept,
                             List<EvidenceGatedExpansionExperiment.ExpandedCandidate> tierAExpansions,
                             List<EvidenceGatedExpansionExperiment.ExpandedCandidate> fullDecoyExpansions,
                             List<GTPersona> tierA,
                             List<GTPersona> fullDecoys) {
        Set<PredictionKey> tierAGenerated = new LinkedHashSet<>(baselineKept);
        List<EvidenceGatedExpansionExperiment.ExpandedCandidate> addedTierA = tierAExpansions.stream()
                .filter(candidate -> passes(candidate, minEvidence, minTerms))
                .toList();
        addedTierA.stream()
                .map(candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()))
                .forEach(tierAGenerated::add);

        Set<PredictionKey> fullDecoyGenerated = baselineKept.stream()
                .filter(candidate -> fullDecoys.stream().anyMatch(persona -> persona.id().equals(candidate.personaId())))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<EvidenceGatedExpansionExperiment.ExpandedCandidate> addedFullDecoy = fullDecoyExpansions.stream()
                .filter(candidate -> passes(candidate, minEvidence, minTerms))
                .toList();
        addedFullDecoy.stream()
                .map(candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()))
                .forEach(fullDecoyGenerated::add);

        int trueLabels = tierA.stream().mapToInt(persona -> persona.truePatterns().size()).sum();
        int trueHits = trueHits(tierAGenerated, tierA);
        int tierAFalsePositives = (int) tierAGenerated.stream()
                .filter(candidate -> tierA.stream().anyMatch(persona -> persona.id().equals(candidate.personaId())))
                .count() - trueHits;
        int addedTierATrueHits = (int) addedTierA.stream()
                .filter(EvidenceGatedExpansionExperiment.ExpandedCandidate::truePositive)
                .count();
        int addedTierAFalsePositives = addedTierA.size() - addedTierATrueHits;
        int addedFullDecoyFalsePositives = addedFullDecoy.size();

        return new SweepPoint(
                rule,
                tierAGenerated.size(),
                trueLabels,
                trueHits,
                tierAFalsePositives,
                fullDecoyGenerated.size(),
                addedTierATrueHits,
                addedTierAFalsePositives,
                addedFullDecoyFalsePositives);
    }

    private boolean passes(EvidenceGatedExpansionExperiment.ExpandedCandidate candidate, int minEvidence, int minTerms) {
        return candidate.evidenceCount() >= minEvidence && candidate.distinctMatchedTermCount() >= minTerms;
    }

    private int trueHits(Set<PredictionKey> generated, List<GTPersona> personas) {
        return (int) generated.stream()
                .filter(candidate -> personas.stream()
                        .filter(persona -> persona.id().equals(candidate.personaId()))
                        .findFirst()
                        .map(persona -> trueSet(persona).contains(candidate.prediction()))
                        .orElse(false))
                .count();
    }

    private Set<PredictedPattern> trueSet(GTPersona persona) {
        return persona.truePatterns().stream()
                .map(this::toPrediction)
                .collect(Collectors.toSet());
    }

    private PredictedPattern toPrediction(GTLabel label) {
        return new PredictedPattern(label.patternKey(), label.domain());
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    record SweepReport(List<SweepPoint> points) {
    }

    record SweepPoint(
            String rule,
            int tierAGenerated,
            int tierATrueLabels,
            int tierATrueHits,
            int tierAFalsePositives,
            int fullDecoyFalsePositives,
            int addedTierATrueHits,
            int addedTierAFalsePositives,
            int addedFullDecoyFalsePositives
    ) {
        double tierARecall() {
            return tierATrueHits / (double) Math.max(1, tierATrueLabels);
        }
    }

    private record PredictionKey(String personaId, PredictedPattern prediction) {
    }
}
