package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateGeneratorAuditTest {

    private final CandidateGeneratorAudit audit = new CandidateGeneratorAudit();
    private final GroundTruthLoader groundTruth = new GroundTruthLoader();

    @Test
    void parses_r1_5_prevented_and_surfaced_candidates() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(
                Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md"));

        assertThat(candidates).hasSize(28);
        assertThat(candidates.stream().filter(CandidateGeneratorAudit.AuditedCandidate::surfaced))
                .hasSize(18);
        assertThat(candidates.stream().filter(candidate -> !candidate.surfaced()))
                .hasSize(10);
        assertThat(candidates).anySatisfy(candidate -> {
            assertThat(candidate.personaId()).isEqualTo("ah-06");
            assertThat(candidate.prediction().patternKey()).isEqualTo("self_criticism");
            assertThat(candidate.prediction().domain().name()).isEqualTo("self");
            assertThat(candidate.surfaced()).isFalse();
        });
    }

    @Test
    void report_pins_candidate_generator_recall_failures() throws Exception {
        List<CandidateGeneratorAudit.AuditedCandidate> candidates = audit.parseR15Candidates(
                Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md"));

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
}
