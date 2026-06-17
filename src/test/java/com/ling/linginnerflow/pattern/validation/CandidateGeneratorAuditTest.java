package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateGeneratorAuditTest {

    private static final Path SOURCE_REPORT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path GENERATED_REPORT = Path.of("eval/RESULTS_V2_CANDIDATE_GENERATOR_AUDIT.md");
    private static final Path RECOVERABILITY_REPORT = Path.of("eval/RESULTS_V2_TIER_A_RECOVERABILITY.md");

    private final CandidateGeneratorAudit audit = new CandidateGeneratorAudit();
    private final GroundTruthLoader groundTruth = new GroundTruthLoader();

    @Test
    void parses_r1_5_prevented_and_surfaced_candidates() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(SOURCE_REPORT);
        List<CandidateGeneratorAudit.AuditedCandidate> surfaced = candidates.stream()
                .filter(CandidateGeneratorAudit.AuditedCandidate::surfaced)
                .toList();
        List<CandidateGeneratorAudit.AuditedCandidate> prevented = candidates.stream()
                .filter(candidate -> !candidate.surfaced())
                .toList();

        assertThat(candidates).hasSize(28);
        assertThat(prevented).hasSize(10);
        assertThat(surfaced).hasSize(18);
        assertThat(prevented).anySatisfy(candidate -> {
            assertThat(candidate.personaId()).isEqualTo("ah-06");
            assertThat(candidate.prediction().patternKey()).isEqualTo("self_criticism");
            assertThat(candidate.prediction().domain().name()).isEqualTo("self");
            assertThat(candidate.surfaced()).isFalse();
        });
        assertThat(surfaced).anySatisfy(candidate -> {
            assertThat(candidate.personaId()).isEqualTo("a-01");
            assertThat(candidate.prediction().patternKey()).isEqualTo("self_criticism");
            assertThat(candidate.prediction().domain().name()).isEqualTo("self");
            assertThat(candidate.surfaced()).isTrue();
        });
    }

    @Test
    void report_pins_candidate_generator_recall_failures() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(SOURCE_REPORT);

        String report = audit.report(candidates, groundTruth.loadTierA(), groundTruth.loadTierAH());

        assertThat(report).contains(
                "- Tier A generator recall ceiling: `4/12 = 0.333`; 8 true labels are absent before thresholding starts.",
                "- Tier A personas with zero generated true-positive candidates: `a-03`, `a-06`.",
                "- Tier A-H non-decoy human generator recall ceiling: `0/14 = 0.000`; no genuine human true positives are present in this candidate table.",
                "- Full-decoy false positives: generator produced `13`; the R1.5 gate prevented 8 and still surfaced 5.",
                "| Tier A | 6 | 12 | 13 | 13 | 4 | 4 | 8 | 0.333 | 9 | 9 |",
                "| Tier A-H non-decoy humans | 3 | 14 | 2 | 0 | 0 | 0 | 14 | 0.000 | 2 | 0 |",
                "| Full decoys | 2 | 0 | 13 | 5 | 0 | 0 | 0 | 0.000 | 13 | 5 |",
                "| a-06 | 3 | 2 | 2 | 0 | 0 | 3 | 0.000 | 2 | 2 |",
                "| a-01 | worth_through_achievement / work |",
                "| surfaced | ah-06 | worth_through_achievement / work |",
                "| prevented | ah-06 | self_criticism / self |");
    }

    @Test
    void committed_report_matches_generated_output() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(SOURCE_REPORT);

        String generated = audit.report(candidates, groundTruth.loadTierA(), groundTruth.loadTierAH());

        assertThat(Files.readString(GENERATED_REPORT)).isEqualTo(generated);
    }

    @Test
    void tier_a_recoverability_report_pins_relabel_ceiling() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(SOURCE_REPORT);

        String report = audit.tierARecoverabilityReport(candidates, groundTruth.loadTierA());

        assertThat(report).contains(
                "- Missing Tier A true labels: `8`.",
                "- Recoverable by pattern-key relabel only: `1`.",
                "- Requires new candidate generation: `7`.",
                "| a-06 | comparison_loop / social | comparison_loop / self<br>rumination / self | recoverable_by_relabel | same pattern key generated under a different domain |",
                "| a-03 | emotional_suppression / self | comparison_loop / self | needs_new_generation | pattern key absent from generated candidates |",
                "Domain-agnostic matching is diagnostic only. The headline metric remains strict `(pattern_key, domain)` recall.");
    }

    @Test
    void committed_recoverability_report_matches_generated_output() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(SOURCE_REPORT);

        String generated = audit.tierARecoverabilityReport(candidates, groundTruth.loadTierA());

        assertThat(Files.readString(RECOVERABILITY_REPORT)).isEqualTo(generated);
    }
}
