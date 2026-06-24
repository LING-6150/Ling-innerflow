package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Tag("validation")
@EnabledIfSystemProperty(named = "pattern.eval.expanded-abstain-gate", matches = "true")
class V2ExpandedAbstainGateRunner {

    private static final Path INPUT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path OUTPUT = Path.of("eval/RESULTS_V2_EXPANDED_ABSTAIN_GATE.md");

    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final ThresholdSweepSimulator thresholdSweep = new ThresholdSweepSimulator();
    private final CandidateGeneratorAudit candidateAudit = new CandidateGeneratorAudit();
    private final EvidenceGatedExpansionExperiment expansion = new EvidenceGatedExpansionExperiment();
    private final ExpandedCandidateAbstainSweep sweep = new ExpandedCandidateAbstainSweep();

    @Test
    void writeExpandedAbstainGateReport() throws IOException {
        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> fullDecoys = groundTruth.loadTierAH().stream()
                .filter(persona -> persona.truePatterns().isEmpty())
                .toList();
        EvidenceGatedExpansionExperiment.ExpansionResult expansionResult = expansion.run(
                candidateAudit.parseR15Candidates(INPUT),
                tierA,
                fullDecoys);
        ExpandedCandidateAbstainSweep.SweepReport report = sweep.sweep(
                thresholdSweep.parseSurfacedCandidates(INPUT),
                expansionResult,
                tierA,
                fullDecoys);
        Files.writeString(OUTPUT, sweep.report(report));
    }
}
