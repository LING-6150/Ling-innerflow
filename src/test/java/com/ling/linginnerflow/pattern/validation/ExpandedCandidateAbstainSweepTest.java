package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpandedCandidateAbstainSweepTest {

    private static final Path SOURCE_REPORT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path OUTPUT = Path.of("eval/RESULTS_V2_EXPANDED_ABSTAIN_GATE.md");

    private final ThresholdSweepSimulator thresholdSweep = new ThresholdSweepSimulator();
    private final CandidateGeneratorAudit candidateAudit = new CandidateGeneratorAudit();
    private final EvidenceGatedExpansionExperiment expansion = new EvidenceGatedExpansionExperiment();
    private final ExpandedCandidateAbstainSweep sweep = new ExpandedCandidateAbstainSweep();
    private final GroundTruthLoader groundTruth = new GroundTruthLoader();

    @Test
    void sweep_pins_safety_recall_tradeoff() throws Exception {
        ExpandedCandidateAbstainSweep.SweepReport report = runSweep();

        assertThat(report.points()).hasSize(5);
        assertThat(report.points()).anySatisfy(point -> {
            assertThat(point.rule()).isEqualTo("baseline only");
            assertThat(point.tierATrueHits()).isEqualTo(4);
            assertThat(point.fullDecoyFalsePositives()).isEqualTo(2);
        });
        assertThat(report.points()).anySatisfy(point -> {
            assertThat(point.rule()).isEqualTo("evidence>=2 terms>=1");
            assertThat(point.tierATrueHits()).isEqualTo(12);
            assertThat(point.tierAFalsePositives()).isEqualTo(13);
            assertThat(point.fullDecoyFalsePositives()).isEqualTo(5);
        });
        assertThat(report.points()).anySatisfy(point -> {
            assertThat(point.rule()).isEqualTo("evidence>=3 terms>=3");
            assertThat(point.tierATrueHits()).isEqualTo(9);
            assertThat(point.fullDecoyFalsePositives()).isEqualTo(2);
        });
    }

    @Test
    void report_pins_decision_summary() throws Exception {
        String report = sweep.report(runSweep());

        assertThat(report).contains(
                "- Best safety-constrained point: `evidence>=2 terms>=2` has Tier A generated TP `12/12` and full-decoy FP `2`.",
                "- Best recall point: `evidence>=2 terms>=2` has Tier A generated TP `12/12` but full-decoy FP `2`.",
                "- Result: this offline evidence-count proxy can preserve the full PR #53 Tier A recall gain while keeping full-decoy FP at `2`, but Tier A false positives remain high (`11`).",
                "| baseline only | 13 | 4 | 0.333 | 7 | 2 | 0 | 0 | 0 |",
                "| evidence>=2 terms>=1 | 27 | 12 | 1.000 | 13 | 5 | 8 | 6 | 3 |",
                "| evidence>=2 terms>=2 | 25 | 12 | 1.000 | 11 | 2 | 8 | 4 | 0 |",
                "| evidence>=3 terms>=3 | 18 | 9 | 0.750 | 7 | 2 | 5 | 0 | 0 |");
    }

    @Test
    void committed_report_matches_generated_output() throws Exception {
        String generated = sweep.report(runSweep());

        assertThat(Files.readString(OUTPUT)).isEqualTo(generated);
    }

    private ExpandedCandidateAbstainSweep.SweepReport runSweep() throws Exception {
        List<ThresholdSweepSimulator.Candidate> surfacedBaseline = thresholdSweep.parseSurfacedCandidates(SOURCE_REPORT);
        List<CandidateGeneratorAudit.AuditedCandidate> baseline = candidateAudit.parseR15Candidates(SOURCE_REPORT);
        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> fullDecoys = groundTruth.loadTierAH().stream()
                .filter(persona -> persona.truePatterns().isEmpty())
                .toList();
        EvidenceGatedExpansionExperiment.ExpansionResult expansionResult = expansion.run(baseline, tierA, fullDecoys);
        return sweep.sweep(surfacedBaseline, expansionResult, tierA, fullDecoys);
    }
}
