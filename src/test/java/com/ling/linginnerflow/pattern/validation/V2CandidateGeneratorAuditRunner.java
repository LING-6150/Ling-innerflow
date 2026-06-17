package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Tag("validation")
@EnabledIfSystemProperty(named = "pattern.eval.candidate-generator-audit", matches = "true")
class V2CandidateGeneratorAuditRunner {

    private static final Path INPUT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path OUTPUT = Path.of("eval/RESULTS_V2_CANDIDATE_GENERATOR_AUDIT.md");

    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final CandidateGeneratorAudit audit = new CandidateGeneratorAudit();

    @Test
    void writeCandidateGeneratorAuditReport() throws IOException {
        String report = audit.report(
                audit.parseR15Candidates(INPUT),
                groundTruth.loadTierA(),
                groundTruth.loadTierAH());
        Files.writeString(OUTPUT, report);
    }
}
