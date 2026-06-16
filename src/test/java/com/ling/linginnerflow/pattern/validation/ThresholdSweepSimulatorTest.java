package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTPersona;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThresholdSweepSimulatorTest {

    private final ThresholdSweepSimulator simulator = new ThresholdSweepSimulator();

    @Test
    void parses_r1_5_surfaced_candidates() throws Exception {
        List<ThresholdSweepSimulator.Candidate> candidates = simulator.parseSurfacedCandidates(
                Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md"));

        assertThat(candidates).hasSize(18);
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.personaId()).isEqualTo("a-01");
            assertThat(candidate.prediction().patternKey()).isEqualTo("self_criticism");
            assertThat(candidate.prediction().domain().name()).isEqualTo("self");
            assertThat(candidate.truePositive()).isTrue();
            assertThat(candidate.fullDecoyFalsePositive()).isFalse();
            assertThat(candidate.fit()).isEqualTo(0.500);
            assertThat(candidate.specificity()).isEqualTo(0.300);
        });
        assertThat(candidates.stream().filter(ThresholdSweepSimulator.Candidate::fullDecoyFalsePositive))
                .hasSize(5);
    }

    @Test
    void threshold_sweep_identifies_no_recovery_rule_on_existing_candidates() throws Exception {
        List<ThresholdSweepSimulator.Candidate> candidates = simulator.parseSurfacedCandidates(
                Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md"));
        List<GTPersona> personas = new com.ling.linginnerflow.pattern.eval.GroundTruthLoader().loadTierA();

        List<ThresholdSweepSimulator.SweepResult> results = simulator.sweep(candidates, personas);

        assertThat(results).isNotEmpty();
        assertThat(results).noneMatch(ThresholdSweepSimulator.SweepResult::meetsRecoveryCriteria);
        assertThat(results).anySatisfy(result -> {
            assertThat(result.rule()).isEqualTo("fit");
            assertThat(result.threshold()).isEqualTo(0.5);
            assertThat(result.fullDecoyFalsePositives()).isEqualTo(2);
            assertThat(result.killedTierATruePositives()).isZero();
        });
    }

    @Test
    void report_pins_decision_summary_values() throws Exception {
        List<ThresholdSweepSimulator.Candidate> candidates = simulator.parseSurfacedCandidates(
                Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md"));
        com.ling.linginnerflow.pattern.eval.GroundTruthLoader loader = new com.ling.linginnerflow.pattern.eval.GroundTruthLoader();

        String report = simulator.report(candidates, loader.loadTierA(), loader.loadTierAH());

        assertThat(report).contains(
                "- Candidates swept: 18",
                "- Recall ceiling: thresholding can only remove R1.5 candidates, so Tier A recall cannot exceed the no-threshold value `0.333`",
                "- Result: no swept rule meets the full recovery target on this candidate table.",
                "- Best safety-constrained rule: `fit >= 0.450` keeps full-decoy FP at 2 with Tier A F1 0.348.",
                "| best safety-constrained F1 | fit | 0.450 | 0.364 | 0.333 | 0.348 | 0.278 | 2 | 0 |",
                "Tier A-H F1 is structurally `0.000` in this offline sweep");
    }

    @Test
    void sweep_rejects_markdown_true_positive_column_drift() throws Exception {
        Path source = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
        Path drifted = Files.createTempFile("threshold-sweep-drift", ".md");
        String content = Files.readString(source)
                .replace("| a-01 | self_criticism / self | yes | no | 0.500 | 0.300 |",
                        "| a-01 | self_criticism / self | no | no | 0.500 | 0.300 |");
        Files.writeString(drifted, content);
        List<ThresholdSweepSimulator.Candidate> candidates = simulator.parseSurfacedCandidates(drifted);
        List<GTPersona> personas = new com.ling.linginnerflow.pattern.eval.GroundTruthLoader().loadTierA();

        assertThatThrownBy(() -> simulator.sweep(candidates, personas))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("true-positive column disagrees with ground truth");
    }
}
