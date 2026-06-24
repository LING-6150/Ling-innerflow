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
@EnabledIfSystemProperty(named = "pattern.eval.evidence-gated-expansion", matches = "true")
class V2EvidenceGatedExpansionRunner {

    private static final Path INPUT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path OUTPUT = Path.of("eval/RESULTS_V2_EVIDENCE_GATED_EXPANSION.md");

    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final CandidateGeneratorAudit candidateAudit = new CandidateGeneratorAudit();
    private final EvidenceGatedExpansionExperiment experiment = new EvidenceGatedExpansionExperiment();

    @Test
    void writeEvidenceGatedExpansionReport() throws IOException {
        List<GTPersona> fullDecoys = groundTruth.loadTierAH().stream()
                .filter(persona -> persona.truePatterns().isEmpty())
                .toList();
        EvidenceGatedExpansionExperiment.ExpansionResult result = experiment.run(
                candidateAudit.parseR15Candidates(INPUT),
                groundTruth.loadTierA(),
                fullDecoys);
        Files.writeString(OUTPUT, experiment.report(result));
    }
}
