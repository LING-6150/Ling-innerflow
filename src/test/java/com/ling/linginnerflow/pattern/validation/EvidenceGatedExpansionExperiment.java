package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTLabel;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class EvidenceGatedExpansionExperiment {

    private static final List<Probe> PROBES = List.of(
            new Probe("worth_through_achievement", Domain.work, 2, List.of(
                    "价值", "成果", "没有资格休息", "占位符", "有什么意义", "功能")),
            new Probe("people_pleasing", Domain.family, 2, List.of(
                    "我说行", "心里清楚我根本不想", "说行比解释容易", "已经答应了爸", "爸", "妈")),
            new Probe("emotional_suppression", Domain.self, 2, List.of(
                    "说不出来", "没关系", "挺好的", "胸口", "跟谁都没说", "不对家里说")),
            new Probe("family_pressure", Domain.family, 2, List.of(
                    "考公", "稳定", "妈的逻辑", "北京太远", "父母", "家里")),
            new Probe("avoidance", Domain.self, 2, List.of(
                    "打开那个文件夹", "想清楚", "更急的事", "看手机", "往后推", "不想再动")),
            new Probe("boundary_difficulty", Domain.intimate, 2, List.of(
                    "男友", "我说了好", "我说好", "十一点", "一点才睡", "没有改口")),
            new Probe("comparison_loop", Domain.social, 2, List.of(
                    "创业圈", "第四家分店", "四家分店", "起步还晚", "进展这么快", "我在干什么")),
            new Probe("over_responsibility", Domain.family, 2, List.of(
                    "弟弟", "爸妈", "我来安排", "我来处理", "检查", "联系"))
    );

    ExpansionResult run(List<CandidateGeneratorAudit.AuditedCandidate> baselineCandidates,
                        List<GTPersona> tierA,
                        List<GTPersona> fullDecoys) {
        List<ExpandedCandidate> expansions = tierA.stream()
                .flatMap(persona -> expand(persona).stream())
                .toList();
        List<ExpandedCandidate> fullDecoyExpansions = fullDecoys.stream()
                .flatMap(persona -> expand(persona).stream())
                .toList();

        SliceMetrics baselineTierA = scoreBaseline(baselineCandidates, tierA);
        SliceMetrics expandedTierA = scoreExpanded(baselineCandidates, expansions, tierA);
        SliceMetrics baselineFullDecoys = scoreBaseline(baselineCandidates, fullDecoys);
        SliceMetrics expandedFullDecoys = scoreExpanded(baselineCandidates, fullDecoyExpansions, fullDecoys);
        List<RecoveryRow> recoveryRows = recoveryRows(baselineCandidates, expansions, tierA);

        return new ExpansionResult(
                baselineTierA,
                expandedTierA,
                baselineFullDecoys,
                expandedFullDecoys,
                expansions,
                fullDecoyExpansions,
                recoveryRows);
    }

    String report(ExpansionResult result) {
        long recoveredMissing = result.recoveryRows().stream()
                .filter(RecoveryRow::recoveredByExpansion)
                .count();
        long totalMissing = result.recoveryRows().size();
        int probeTriggeredFullDecoyCandidates = result.fullDecoyExpansions().size();
        int addedTierACandidates = result.expandedTierA().generatedCandidates()
                - result.baselineTierA().generatedCandidates();
        int addedFullDecoyCandidates = result.expandedFullDecoys().generatedCandidates()
                - result.baselineFullDecoys().generatedCandidates();

        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2 Evidence-Gated Expansion Experiment\n\n")
                .append("Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` plus Tier A corpus records.\n\n")
                .append("This is an offline Tier A dev experiment. It uses manually authored evidence probes from the Tier A failure forensics and must not be treated as held-out proof or production generator behavior.\n\n")
                .append("## Decision Summary\n\n")
                .append("- Baseline R1.5 Tier A generated TP: `")
                .append(result.baselineTierA().trueHits()).append("/")
                .append(result.baselineTierA().trueLabels()).append("`.\n")
                .append("- Expanded Tier A generated TP: `")
                .append(result.expandedTierA().trueHits()).append("/")
                .append(result.expandedTierA().trueLabels()).append("`.\n")
                .append("- Missing Tier A labels recovered by expansion: `")
                .append(recoveredMissing).append("/").append(totalMissing).append("`.\n")
                .append("- Added Tier A candidates: `").append(addedTierACandidates)
                .append("`; added Tier A false positives: `")
                .append(result.expandedTierA().falsePositives() - result.baselineTierA().falsePositives())
                .append("`.\n")
                .append("- Full-decoy confirmation: generated FP changes from `")
                .append(result.baselineFullDecoys().generatedCandidates()).append("` to `")
                .append(result.expandedFullDecoys().generatedCandidates()).append("` with `")
                .append(addedFullDecoyCandidates).append("` net-new candidates and `")
                .append(probeTriggeredFullDecoyCandidates).append("` probe-triggered candidates. This confirmation slice was not used to author probes.\n")
                .append("- Cost/latency: `$0.0000` and `0s`; this experiment does not call an LLM.\n\n")
                .append("Interpretation: evidence-gated expansion can recover missing Tier A labels in the dev slice, but the recall gain comes with additional false positives. This result is probe-authored from Tier A forensics and is not production generator behavior. The next real test is whether a less hand-authored generator plus an abstain gate can preserve useful recall gain while keeping full-decoy surfaced false positives within the `<=2` recovery target.\n\n")
                .append("## Slice Summary\n\n")
                .append("| slice | generated candidates | true labels | true hits | recall | false positives |\n")
                .append("|---|---:|---:|---:|---:|---:|\n")
                .append(sliceRow("Tier A baseline R1.5", result.baselineTierA()))
                .append(sliceRow("Tier A expanded", result.expandedTierA()))
                .append(sliceRow("Full decoy baseline R1.5", result.baselineFullDecoys()))
                .append(sliceRow("Full decoy expanded confirmation", result.expandedFullDecoys()))
                .append("\n")
                .append("## Missing Label Recovery\n\n")
                .append("| persona | missing label | recovered by expansion? | evidence excerpts |\n")
                .append("|---|---|---|---|\n");
        result.recoveryRows().forEach(row -> md.append("| ")
                .append(row.personaId()).append(" | ")
                .append(label(row.missingLabel())).append(" | ")
                .append(row.recoveredByExpansion() ? "yes" : "no").append(" | ")
                .append(row.evidenceSummary()).append(" |\n"));

        md.append("\n## Added Tier A Candidates\n\n")
                .append("| persona | candidate | true positive? | evidence excerpts |\n")
                .append("|---|---|---|---|\n");
        result.expansions().stream()
                .sorted(expandedComparator())
                .forEach(candidate -> md.append("| ")
                        .append(candidate.personaId()).append(" | ")
                        .append(label(candidate.prediction())).append(" | ")
                        .append(candidate.truePositive() ? "yes" : "no").append(" | ")
                        .append(evidenceSummary(candidate.evidence())).append(" |\n"));

        md.append("\n## Full-Decoy Probe-Triggered Candidates\n\n")
                .append("| persona | candidate | evidence excerpts |\n")
                .append("|---|---|---|\n");
        if (result.fullDecoyExpansions().isEmpty()) {
            md.append("| none | none | none |\n");
        } else {
            result.fullDecoyExpansions().stream()
                    .sorted(expandedComparator())
                    .forEach(candidate -> md.append("| ")
                            .append(candidate.personaId()).append(" | ")
                            .append(label(candidate.prediction())).append(" | ")
                            .append(evidenceSummary(candidate.evidence())).append(" |\n"));
        }

        md.append("\n## Caveats\n\n")
                .append("- This is a Tier A dev experiment, not a held-out result.\n")
                .append("- The probes are intentionally small and manually authored from the Tier A miss analysis; they are not production generator logic.\n")
                .append("- Added Tier A false positives are part of the result, not a bug in the report.\n")
                .append("- The experiment measures pre-gate generated candidates. A follow-up must pass broadened candidates through an abstain gate before claiming surfaced full-decoy safety.\n");

        return md.toString();
    }

    private List<ExpandedCandidate> expand(GTPersona persona) {
        List<ExpandedCandidate> candidates = new ArrayList<>();
        for (Probe probe : PROBES) {
            List<EvidenceHit> evidence = evidenceFor(persona, probe);
            if (evidence.size() >= probe.minEvidence()) {
                PredictedPattern prediction = new PredictedPattern(probe.patternKey(), probe.domain());
                boolean truePositive = trueSet(persona).contains(prediction);
                candidates.add(new ExpandedCandidate(persona.id(), prediction, truePositive, evidence));
            }
        }
        return List.copyOf(candidates);
    }

    private List<EvidenceHit> evidenceFor(GTPersona persona, Probe probe) {
        List<EvidenceHit> hits = new ArrayList<>();
        for (CorpusRecord record : persona.corpus()) {
            String text = record.text() == null ? "" : record.text();
            String lower = text.toLowerCase(Locale.ROOT);
            List<String> matched = probe.terms().stream()
                    .filter(term -> lower.contains(term.toLowerCase(Locale.ROOT)))
                    .toList();
            if (!matched.isEmpty()) {
                hits.add(new EvidenceHit(record, matched));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparing((EvidenceHit hit) -> hit.record().date()))
                .limit(3)
                .toList();
    }

    private SliceMetrics scoreBaseline(List<CandidateGeneratorAudit.AuditedCandidate> baselineCandidates,
                                       List<GTPersona> personas) {
        Set<String> personaIds = personas.stream()
                .map(GTPersona::id)
                .collect(Collectors.toSet());
        Set<PredictionKey> generated = baselineCandidates.stream()
                .filter(candidate -> personaIds.contains(candidate.personaId()))
                .map(candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return score(generated, personas);
    }

    private SliceMetrics scoreExpanded(List<CandidateGeneratorAudit.AuditedCandidate> baselineCandidates,
                                       List<ExpandedCandidate> expansions,
                                       List<GTPersona> personas) {
        Set<String> personaIds = personas.stream()
                .map(GTPersona::id)
                .collect(Collectors.toSet());
        Set<PredictionKey> generated = baselineCandidates.stream()
                .filter(candidate -> personaIds.contains(candidate.personaId()))
                .map(candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        expansions.stream()
                .filter(candidate -> personaIds.contains(candidate.personaId()))
                .map(candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()))
                .forEach(generated::add);
        return score(generated, personas);
    }

    private SliceMetrics score(Set<PredictionKey> generated, List<GTPersona> personas) {
        Map<String, Set<PredictedPattern>> trueByPersona = personas.stream()
                .collect(Collectors.toMap(GTPersona::id, this::trueSet, (left, right) -> left, LinkedHashMap::new));
        int trueLabels = trueByPersona.values().stream().mapToInt(Set::size).sum();
        int trueHits = (int) generated.stream()
                .filter(prediction -> trueByPersona.getOrDefault(prediction.personaId(), Set.of())
                        .contains(prediction.prediction()))
                .count();
        return new SliceMetrics(generated.size(), trueLabels, trueHits, generated.size() - trueHits);
    }

    private List<RecoveryRow> recoveryRows(List<CandidateGeneratorAudit.AuditedCandidate> baselineCandidates,
                                           List<ExpandedCandidate> expansions,
                                           List<GTPersona> tierA) {
        Map<String, Set<PredictedPattern>> baselineByPersona = baselineCandidates.stream()
                .collect(Collectors.groupingBy(
                        CandidateGeneratorAudit.AuditedCandidate::personaId,
                        LinkedHashMap::new,
                        Collectors.mapping(CandidateGeneratorAudit.AuditedCandidate::prediction, Collectors.toSet())));
        Map<PredictionKey, ExpandedCandidate> expansionByKey = expansions.stream()
                .collect(Collectors.toMap(
                        candidate -> new PredictionKey(candidate.personaId(), candidate.prediction()),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return tierA.stream()
                .flatMap(persona -> trueSet(persona).stream()
                        .filter(label -> !baselineByPersona.getOrDefault(persona.id(), Set.of()).contains(label))
                        .sorted(predictionComparator())
                        .map(label -> {
                            ExpandedCandidate recovered = expansionByKey.get(new PredictionKey(persona.id(), label));
                            return new RecoveryRow(
                                    persona.id(),
                                    label,
                                    recovered != null,
                                    recovered == null ? "none" : evidenceSummary(recovered.evidence()));
                        }))
                .sorted(Comparator.comparing(RecoveryRow::personaId)
                        .thenComparing(row -> row.missingLabel().patternKey())
                        .thenComparing(row -> row.missingLabel().domain().name()))
                .toList();
    }

    private String sliceRow(String label, SliceMetrics metrics) {
        return "| " + label + " | "
                + metrics.generatedCandidates() + " | "
                + metrics.trueLabels() + " | "
                + metrics.trueHits() + " | "
                + fmt(metrics.recall()) + " | "
                + metrics.falsePositives() + " |\n";
    }

    private String evidenceSummary(List<EvidenceHit> evidence) {
        return evidence.stream()
                .limit(2)
                .map(hit -> hit.record().date() + " [" + hit.record().type() + "] "
                        + escape(hit.record().text()))
                .collect(Collectors.joining("<br>"));
    }

    private Set<PredictedPattern> trueSet(GTPersona persona) {
        return persona.truePatterns().stream()
                .map(this::toPrediction)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private PredictedPattern toPrediction(GTLabel label) {
        return new PredictedPattern(label.patternKey(), label.domain());
    }

    private String label(PredictedPattern prediction) {
        return prediction.patternKey() + " / " + prediction.domain().name();
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private String escape(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private Comparator<ExpandedCandidate> expandedComparator() {
        return Comparator.comparing(ExpandedCandidate::personaId)
                .thenComparing(candidate -> candidate.prediction().patternKey())
                .thenComparing(candidate -> candidate.prediction().domain().name());
    }

    private Comparator<PredictedPattern> predictionComparator() {
        return Comparator.comparing(PredictedPattern::patternKey)
                .thenComparing(prediction -> prediction.domain().name());
    }

    record ExpansionResult(
            SliceMetrics baselineTierA,
            SliceMetrics expandedTierA,
            SliceMetrics baselineFullDecoys,
            SliceMetrics expandedFullDecoys,
            List<ExpandedCandidate> expansions,
            List<ExpandedCandidate> fullDecoyExpansions,
            List<RecoveryRow> recoveryRows
    ) {
    }

    record SliceMetrics(int generatedCandidates, int trueLabels, int trueHits, int falsePositives) {
        double recall() {
            return trueHits / (double) Math.max(1, trueLabels);
        }
    }

    record ExpandedCandidate(
            String personaId,
            PredictedPattern prediction,
            boolean truePositive,
            List<EvidenceHit> evidence
    ) {
        int evidenceCount() {
            return evidence.size();
        }

        int distinctMatchedTermCount() {
            return evidence.stream()
                    .flatMap(hit -> hit.matchedTerms().stream())
                    .collect(Collectors.toSet())
                    .size();
        }
    }

    private record RecoveryRow(
            String personaId,
            PredictedPattern missingLabel,
            boolean recoveredByExpansion,
            String evidenceSummary
    ) {
    }

    record EvidenceHit(CorpusRecord record, List<String> matchedTerms) {
    }

    private record Probe(String patternKey, Domain domain, int minEvidence, List<String> terms) {
    }

    private record PredictionKey(String personaId, PredictedPattern prediction) {
    }
}
