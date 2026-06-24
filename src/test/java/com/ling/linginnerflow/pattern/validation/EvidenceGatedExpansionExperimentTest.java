package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceGatedExpansionExperimentTest {

    private static final Path SOURCE_REPORT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path EXPANSION_REPORT = Path.of("eval/RESULTS_V2_EVIDENCE_GATED_EXPANSION.md");

    private final CandidateGeneratorAudit candidateAudit = new CandidateGeneratorAudit();
    private final EvidenceGatedExpansionExperiment experiment = new EvidenceGatedExpansionExperiment();
    private final GroundTruthLoader groundTruth = new GroundTruthLoader();

    @Test
    void expansion_recovers_tier_a_missing_labels_with_evidence() throws Exception {
        EvidenceGatedExpansionExperiment.ExpansionResult result = runExperiment();

        assertThat(result.baselineTierA()).satisfies(metrics -> {
            assertThat(metrics.generatedCandidates()).isEqualTo(13);
            assertThat(metrics.trueLabels()).isEqualTo(12);
            assertThat(metrics.trueHits()).isEqualTo(4);
            assertThat(metrics.falsePositives()).isEqualTo(9);
        });
        assertThat(result.expandedTierA()).satisfies(metrics -> {
            assertThat(metrics.generatedCandidates()).isEqualTo(27);
            assertThat(metrics.trueLabels()).isEqualTo(12);
            assertThat(metrics.trueHits()).isEqualTo(12);
            assertThat(metrics.falsePositives()).isEqualTo(15);
        });
        assertThat(result.expansions()).hasSize(14);
        assertThat(result.expansions().stream().filter(EvidenceGatedExpansionExperiment.ExpandedCandidate::truePositive))
                .hasSize(8);
        assertThat(result.expansions().stream().filter(candidate -> !candidate.truePositive()))
                .hasSize(6);
        assertThat(result.expansions())
                .allSatisfy(candidate -> assertThat(candidate.evidence()).hasSizeGreaterThanOrEqualTo(2));
        assertThat(result.fullDecoyExpansions()).hasSize(3);
    }

    @Test
    void report_pins_experiment_headline_numbers() throws Exception {
        String report = experiment.report(runExperiment());

        assertThat(report).contains(
                "- Baseline R1.5 Tier A generated TP: `4/12`.",
                "- Expanded Tier A generated TP: `12/12`.",
                "- Missing Tier A labels recovered by expansion: `8/8`.",
                "- Added Tier A candidates: `14`; added Tier A false positives: `6`.",
                "- Full-decoy confirmation: generated FP changes from `13` to `14` with `1` net-new candidates and `3` probe-triggered candidates.",
                "| Tier A baseline R1.5 | 13 | 12 | 4 | 0.333 | 9 |",
                "| Tier A expanded | 27 | 12 | 12 | 1.000 | 15 |",
                "| Full decoy expanded confirmation | 14 | 0 | 0 | 0.000 | 14 |",
                "| a-06 | comparison_loop / social | yes |",
                "| ah-05 | emotional_suppression / self |");
    }

    @Test
    void committed_report_matches_generated_output() throws Exception {
        String generated = experiment.report(runExperiment());

        assertThat(Files.readString(EXPANSION_REPORT)).isEqualTo(generated);
    }

    private EvidenceGatedExpansionExperiment.ExpansionResult runExperiment() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> baseline = candidateAudit.parseR15Candidates(SOURCE_REPORT);
        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> fullDecoys = groundTruth.loadTierAH().stream()
                .filter(persona -> persona.truePatterns().isEmpty())
                .toList();
        return experiment.run(baseline, tierA, fullDecoys);
    }
}
