package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.GTLabel;
import com.ling.linginnerflow.pattern.eval.GTPersona;
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
import java.util.stream.Collectors;

final class CandidateGeneratorAudit {

    List<AuditedCandidate> parseR15Candidates(Path report) throws IOException {
        List<String> lines = Files.readAllLines(report);
        List<AuditedCandidate> candidates = new ArrayList<>();
        Section section = Section.NONE;
        for (String line : lines) {
            if (line.equals("## Prevented Candidates")) {
                section = Section.PREVENTED;
                continue;
            }
            if (line.equals("## Surfaced Candidates")) {
                section = Section.SURFACED;
                continue;
            }
            if (line.startsWith("## ") && section != Section.NONE) {
                section = Section.NONE;
                continue;
            }
            if (!line.startsWith("| ") || line.startsWith("|---") || line.startsWith("| persona |")) {
                continue;
            }
            if (section == Section.PREVENTED) {
                candidates.add(parsePreventedCandidate(line));
            } else if (section == Section.SURFACED) {
                candidates.add(parseSurfacedCandidate(line));
            }
        }
        return List.copyOf(candidates);
    }

    String report(List<AuditedCandidate> candidates, List<GTPersona> tierA, List<GTPersona> tierAH) {
        List<GTPersona> humanNonDecoy = tierAH.stream()
                .filter(persona -> !isFullDecoy(persona))
                .toList();
        List<GTPersona> fullDecoys = tierAH.stream()
                .filter(this::isFullDecoy)
                .toList();
        List<GTPersona> truePositivePersonas = new ArrayList<>();
        truePositivePersonas.addAll(tierA);
        truePositivePersonas.addAll(humanNonDecoy);

        SliceSummary tierASummary = summarize("Tier A", tierA, candidates);
        SliceSummary humanSummary = summarize("Tier A-H non-decoy humans", humanNonDecoy, candidates);
        SliceSummary fullDecoySummary = summarize("Full decoys", fullDecoys, candidates);

        List<PersonaAudit> personas = new ArrayList<>();
        tierA.forEach(persona -> personas.add(auditPersona(persona, candidates)));
        tierAH.forEach(persona -> personas.add(auditPersona(persona, candidates)));

        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2 Candidate Generator Audit\n\n")
                .append("Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` prevented + surfaced candidates.\n\n")
                .append("This is an offline audit of the checked-in R1.5 candidate table. It does not call an LLM and must not be treated as held-out proof.\n\n")
                .append("## Decision Summary\n\n")
                .append("- Candidate source: R1.5 label-biased candidate table, before and after the abstain gate.\n")
                .append("- Tier A generator recall ceiling: `")
                .append(tierASummary.generatedTrueHits()).append("/").append(tierASummary.trueLabels())
                .append(" = ").append(fmt(tierASummary.recallCeiling())).append("`; ")
                .append(tierASummary.missingTrueLabels()).append(" true labels are absent before thresholding starts.\n")
                .append("- Tier A personas with zero generated true-positive candidates: `")
                .append(personasWithZeroGeneratedHits(tierA, candidates)).append("`.\n")
                .append("- Tier A-H non-decoy human generator recall ceiling: `")
                .append(humanSummary.generatedTrueHits()).append("/").append(humanSummary.trueLabels())
                .append(" = ").append(fmt(humanSummary.recallCeiling())).append("`; no genuine human true positives are present in this candidate table.\n")
                .append("- Full-decoy false positives: generator produced `")
                .append(fullDecoySummary.generatedCandidates()).append("`; the R1.5 gate prevented ")
                .append(fullDecoySummary.preventedCandidates()).append(" and still surfaced ")
                .append(fullDecoySummary.surfacedCandidates()).append(".\n")
                .append("- B2 comparison caveat: checked-in reports provide aggregate B2 metrics, not per-label B2 predictions, so this audit does not claim which missing labels B2 recovered.\n\n")
                .append("Interpretation: the next improvement should target candidate generation or an earlier abstain signal. The current gate can reduce decoy exposure, but it cannot recover the ")
                .append(tierASummary.missingTrueLabels())
                .append(" Tier A true labels that never entered the candidate table.\n\n")
                .append("## Slice Summary\n\n")
                .append("| slice | personas | true labels | generated candidates | surfaced candidates | generated TP | surfaced TP | missing true labels | recall ceiling | generated FP | surfaced FP |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n")
                .append(sliceRow(tierASummary))
                .append(sliceRow(humanSummary))
                .append(sliceRow(fullDecoySummary))
                .append("\n")
                .append("## Per-Persona Ceiling\n\n")
                .append("| persona | true labels | generated | surfaced | generated TP | surfaced TP | missing true labels | recall ceiling | generated FP | surfaced FP |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        personas.forEach(persona -> md.append("| ")
                .append(persona.personaId()).append(" | ")
                .append(persona.trueLabels()).append(" | ")
                .append(persona.generatedCandidates()).append(" | ")
                .append(persona.surfacedCandidates()).append(" | ")
                .append(persona.generatedTrueHits()).append(" | ")
                .append(persona.surfacedTrueHits()).append(" | ")
                .append(persona.missingTrueLabels()).append(" | ")
                .append(fmt(persona.recallCeiling())).append(" | ")
                .append(persona.generatedFalsePositives()).append(" | ")
                .append(persona.surfacedFalsePositives()).append(" |\n"));

        md.append("\n## Surfaced True Positives\n\n")
                .append("| persona | candidate |\n")
                .append("|---|---|\n");
        candidates.stream()
                .filter(AuditedCandidate::surfaced)
                .filter(candidate -> isTruePositive(candidate, truePositivePersonas))
                .sorted(candidateComparator())
                .forEach(candidate -> md.append("| ")
                        .append(candidate.personaId()).append(" | ")
                        .append(label(candidate.prediction())).append(" |\n"));

        md.append("\n## Missing True Labels\n\n")
                .append("| persona | missing label |\n")
                .append("|---|---|\n");
        personas.stream()
                .filter(persona -> !persona.fullDecoy())
                .flatMap(persona -> persona.missingPredictions().stream()
                        .map(prediction -> new CandidateRow(persona.personaId(), prediction)))
                .forEach(row -> md.append("| ")
                        .append(row.personaId()).append(" | ")
                        .append(label(row.prediction())).append(" |\n"));

        md.append("\n## Full-Decoy False Positives\n\n")
                .append("| disposition | persona | candidate |\n")
                .append("|---|---|---|\n");
        candidates.stream()
                .filter(candidate -> fullDecoys.stream().anyMatch(persona -> persona.id().equals(candidate.personaId())))
                .sorted(candidateComparator())
                .forEach(candidate -> md.append("| ")
                        .append(candidate.surfaced() ? "surfaced" : "prevented").append(" | ")
                        .append(candidate.personaId()).append(" | ")
                        .append(label(candidate.prediction())).append(" |\n"));

        md.append("\n## Tier A False-Positive Pressure\n\n")
                .append("| candidate | generated count | surfaced count |\n")
                .append("|---|---:|---:|\n");
        falsePositivePressure(candidates, tierA).forEach((prediction, counts) -> md.append("| ")
                .append(label(prediction)).append(" | ")
                .append(counts.generated()).append(" | ")
                .append(counts.surfaced()).append(" |\n"));

        return md.toString();
    }

    String tierARecoverabilityReport(List<AuditedCandidate> candidates, List<GTPersona> tierA) {
        List<RecoverabilityRow> rows = tierA.stream()
                .flatMap(persona -> recoverabilityRows(persona, candidates).stream())
                .toList();
        long recoverableByRelabel = rows.stream()
                .filter(row -> row.status() == RecoverabilityStatus.RECOVERABLE_BY_RELABEL)
                .count();
        long needsNewGeneration = rows.stream()
                .filter(row -> row.status() == RecoverabilityStatus.NEEDS_NEW_GENERATION)
                .count();

        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2 Tier A Recoverability Audit\n\n")
                .append("Input: `eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md` prevented + surfaced candidates, Tier A only.\n\n")
                .append("This is an offline diagnostic over the dev/calibration slice. It does not inspect Tier A-H sealed labels and must not be treated as held-out proof.\n\n")
                .append("## Decision Summary\n\n")
                .append("- Missing Tier A true labels: `").append(rows.size()).append("`.\n")
                .append("- Recoverable by pattern-key relabel only: `").append(recoverableByRelabel).append("`.\n")
                .append("- Requires new candidate generation: `").append(needsNewGeneration).append("`.\n")
                .append("- Domain-agnostic matching is diagnostic only. The headline metric remains strict `(pattern_key, domain)` recall.\n\n")
                .append("Interpretation: the cheap relabel/domain-assignment fix can recover at most one missing Tier A label on this candidate table. The remaining seven misses require the generator to propose additional evidence-grounded candidates before the abstain gate or threshold sweep can help.\n\n")
                .append("## Recoverability Split\n\n")
                .append("| persona | missing true label | generated labels for persona | status | diagnostic note |\n")
                .append("|---|---|---|---|---|\n");
        rows.forEach(row -> md.append("| ")
                .append(row.personaId()).append(" | ")
                .append(label(row.missingLabel())).append(" | ")
                .append(row.generatedLabels()).append(" | ")
                .append(row.status().reportValue()).append(" | ")
                .append(row.note()).append(" |\n"));

        md.append("\n## Design Implication\n\n")
                .append("- Treat `recoverable_by_relabel` as a bounded domain-assignment experiment, not a metric change.\n")
                .append("- Treat `needs_new_generation` as the main candidate-generator redesign budget.\n")
                .append("- Re-measure full-decoy generated and surfaced false positives after any broader generation change; PR #50 showed `13` generated and `5` surfaced full-decoy FPs, above the `<=2` recovery target.\n");

        return md.toString();
    }

    private AuditedCandidate parsePreventedCandidate(String line) {
        String[] columns = line.split("\\|", -1);
        if (columns.length < 7) {
            throw new IllegalArgumentException("Malformed prevented candidate row: " + line);
        }
        return new AuditedCandidate(columns[1].trim(), parsePrediction(columns[2].trim()), false);
    }

    private AuditedCandidate parseSurfacedCandidate(String line) {
        String[] columns = line.split("\\|", -1);
        if (columns.length < 8) {
            throw new IllegalArgumentException("Malformed surfaced candidate row: " + line);
        }
        return new AuditedCandidate(columns[1].trim(), parsePrediction(columns[2].trim()), true);
    }

    private PredictedPattern parsePrediction(String raw) {
        String[] candidateParts = raw.split(" / ");
        if (candidateParts.length != 2) {
            throw new IllegalArgumentException("Malformed candidate label: " + raw);
        }
        return new PredictedPattern(candidateParts[0].trim(), Domain.valueOf(candidateParts[1].trim()));
    }

    private List<RecoverabilityRow> recoverabilityRows(GTPersona persona, List<AuditedCandidate> candidates) {
        List<PredictedPattern> generated = candidates.stream()
                .filter(candidate -> candidate.personaId().equals(persona.id()))
                .map(AuditedCandidate::prediction)
                .sorted(predictionComparator())
                .toList();
        Set<PredictedPattern> generatedSet = new LinkedHashSet<>(generated);
        Set<String> generatedKeys = generated.stream()
                .map(PredictedPattern::patternKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String generatedLabels = generated.isEmpty()
                ? "`none`"
                : generated.stream().map(this::label).collect(Collectors.joining("<br>"));

        return trueSet(persona).stream()
                .filter(trueLabel -> !generatedSet.contains(trueLabel))
                .sorted(predictionComparator())
                .map(trueLabel -> {
                    boolean sameKeyGenerated = generatedKeys.contains(trueLabel.patternKey());
                    RecoverabilityStatus status = sameKeyGenerated
                            ? RecoverabilityStatus.RECOVERABLE_BY_RELABEL
                            : RecoverabilityStatus.NEEDS_NEW_GENERATION;
                    String note = sameKeyGenerated
                            ? "same pattern key generated under a different domain"
                            : "pattern key absent from generated candidates";
                    return new RecoverabilityRow(persona.id(), trueLabel, generatedLabels, status, note);
                })
                .toList();
    }

    private SliceSummary summarize(String name, List<GTPersona> personas, List<AuditedCandidate> candidates) {
        int trueLabels = personas.stream().mapToInt(persona -> persona.truePatterns().size()).sum();
        Set<String> personaIds = personas.stream()
                .map(GTPersona::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int generated = (int) candidates.stream()
                .filter(candidate -> personaIds.contains(candidate.personaId()))
                .count();
        int surfaced = (int) candidates.stream()
                .filter(AuditedCandidate::surfaced)
                .filter(candidate -> personaIds.contains(candidate.personaId()))
                .count();
        int generatedTrueHits = countTrueHits(candidates, personas, false);
        int surfacedTrueHits = countTrueHits(candidates, personas, true);
        return new SliceSummary(
                name,
                personas.size(),
                trueLabels,
                generated,
                surfaced,
                generatedTrueHits,
                surfacedTrueHits,
                trueLabels - generatedTrueHits,
                generated - generatedTrueHits,
                surfaced - surfacedTrueHits);
    }

    private int countTrueHits(List<AuditedCandidate> candidates, List<GTPersona> personas, boolean surfacedOnly) {
        return (int) candidates.stream()
                .filter(candidate -> !surfacedOnly || candidate.surfaced())
                .filter(candidate -> isTruePositive(candidate, personas))
                .count();
    }

    private PersonaAudit auditPersona(GTPersona persona, List<AuditedCandidate> candidates) {
        List<AuditedCandidate> personaCandidates = candidates.stream()
                .filter(candidate -> candidate.personaId().equals(persona.id()))
                .sorted(candidateComparator())
                .toList();
        Set<PredictedPattern> trueSet = trueSet(persona);
        Set<PredictedPattern> generatedSet = personaCandidates.stream()
                .map(AuditedCandidate::prediction)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PredictedPattern> missing = trueSet.stream()
                .filter(prediction -> !generatedSet.contains(prediction))
                .sorted(predictionComparator())
                .toList();
        int generatedTrueHits = (int) personaCandidates.stream()
                .filter(candidate -> trueSet.contains(candidate.prediction()))
                .count();
        int surfacedTrueHits = (int) personaCandidates.stream()
                .filter(AuditedCandidate::surfaced)
                .filter(candidate -> trueSet.contains(candidate.prediction()))
                .count();
        int surfaced = (int) personaCandidates.stream()
                .filter(AuditedCandidate::surfaced)
                .count();
        return new PersonaAudit(
                persona.id(),
                isFullDecoy(persona),
                trueSet.size(),
                personaCandidates.size(),
                surfaced,
                generatedTrueHits,
                surfacedTrueHits,
                missing,
                personaCandidates.size() - generatedTrueHits,
                surfaced - surfacedTrueHits);
    }

    private Map<PredictedPattern, Counts> falsePositivePressure(List<AuditedCandidate> candidates, List<GTPersona> personas) {
        Set<String> personaIds = personas.stream()
                .map(GTPersona::id)
                .collect(Collectors.toSet());
        Map<String, GTPersona> personaById = personas.stream()
                .collect(Collectors.toMap(GTPersona::id, persona -> persona));
        Map<PredictedPattern, Counts> counts = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> personaIds.contains(candidate.personaId()))
                .filter(candidate -> !trueSet(personaById.get(candidate.personaId())).contains(candidate.prediction()))
                .forEach(candidate -> counts.compute(candidate.prediction(), (prediction, existing) -> {
                    Counts current = existing == null ? new Counts(0, 0) : existing;
                    return new Counts(current.generated() + 1, current.surfaced() + (candidate.surfaced() ? 1 : 0));
                }));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<PredictedPattern, Counts>comparingByValue(
                                Comparator.comparing(Counts::generated).reversed()
                                        .thenComparing(Counts::surfaced, Comparator.reverseOrder()))
                        .thenComparing(entry -> label(entry.getKey())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private String personasWithZeroGeneratedHits(List<GTPersona> personas, List<AuditedCandidate> candidates) {
        return personas.stream()
                .filter(persona -> auditPersona(persona, candidates).generatedTrueHits() == 0)
                .map(GTPersona::id)
                .collect(Collectors.joining("`, `"));
    }

    private boolean isTruePositive(AuditedCandidate candidate, List<GTPersona> personas) {
        return personas.stream()
                .filter(persona -> persona.id().equals(candidate.personaId()))
                .findFirst()
                .map(persona -> trueSet(persona).contains(candidate.prediction()))
                .orElse(false);
    }

    private Set<PredictedPattern> trueSet(GTPersona persona) {
        return persona.truePatterns().stream()
                .map(this::toPrediction)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private PredictedPattern toPrediction(GTLabel label) {
        return new PredictedPattern(label.patternKey(), label.domain());
    }

    private Comparator<AuditedCandidate> candidateComparator() {
        return Comparator.comparing(AuditedCandidate::personaId)
                .thenComparing(candidate -> candidate.prediction().patternKey())
                .thenComparing(candidate -> candidate.prediction().domain().name())
                .thenComparing(AuditedCandidate::surfaced);
    }

    private Comparator<PredictedPattern> predictionComparator() {
        return Comparator.comparing(PredictedPattern::patternKey)
                .thenComparing(prediction -> prediction.domain().name());
    }

    private String sliceRow(SliceSummary summary) {
        return "| " + summary.name() + " | "
                + summary.personas() + " | "
                + summary.trueLabels() + " | "
                + summary.generatedCandidates() + " | "
                + summary.surfacedCandidates() + " | "
                + summary.generatedTrueHits() + " | "
                + summary.surfacedTrueHits() + " | "
                + summary.missingTrueLabels() + " | "
                + fmt(summary.recallCeiling()) + " | "
                + summary.generatedFalsePositives() + " | "
                + summary.surfacedFalsePositives() + " |\n";
    }

    private boolean isFullDecoy(GTPersona persona) {
        return persona.truePatterns().isEmpty();
    }

    private String label(PredictedPattern prediction) {
        return prediction.patternKey() + " / " + prediction.domain().name();
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    record AuditedCandidate(String personaId, PredictedPattern prediction, boolean surfaced) {
    }

    private enum Section {
        NONE,
        PREVENTED,
        SURFACED
    }

    private record SliceSummary(
            String name,
            int personas,
            int trueLabels,
            int generatedCandidates,
            int surfacedCandidates,
            int generatedTrueHits,
            int surfacedTrueHits,
            int missingTrueLabels,
            int generatedFalsePositives,
            int surfacedFalsePositives
    ) {
        int preventedCandidates() {
            return generatedCandidates - surfacedCandidates;
        }

        double recallCeiling() {
            return generatedTrueHits / (double) Math.max(1, trueLabels);
        }
    }

    private record PersonaAudit(
            String personaId,
            boolean fullDecoy,
            int trueLabels,
            int generatedCandidates,
            int surfacedCandidates,
            int generatedTrueHits,
            int surfacedTrueHits,
            List<PredictedPattern> missingPredictions,
            int generatedFalsePositives,
            int surfacedFalsePositives
    ) {
        int missingTrueLabels() {
            return missingPredictions.size();
        }

        double recallCeiling() {
            return generatedTrueHits / (double) Math.max(1, trueLabels);
        }
    }

    private record CandidateRow(String personaId, PredictedPattern prediction) {
    }

    private record Counts(int generated, int surfaced) {
    }

    private record RecoverabilityRow(
            String personaId,
            PredictedPattern missingLabel,
            String generatedLabels,
            RecoverabilityStatus status,
            String note
    ) {
    }

    private enum RecoverabilityStatus {
        RECOVERABLE_BY_RELABEL("recoverable_by_relabel"),
        NEEDS_NEW_GENERATION("needs_new_generation");

        private final String reportValue;

        RecoverabilityStatus(String reportValue) {
            this.reportValue = reportValue;
        }

        String reportValue() {
            return reportValue;
        }
    }
}
