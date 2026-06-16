package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Tag("validation")
@EnabledIfSystemProperty(named = "pattern.eval.threshold-sweep", matches = "true")
class V2ThresholdSweepRunner {

    private static final Path INPUT = Path.of("eval/RESULTS_V2_ABSTAIN_R1_5_SANITY.md");
    private static final Path OUTPUT = Path.of("eval/RESULTS_V2_THRESHOLD_SWEEP.md");

    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final ThresholdSweepSimulator simulator = new ThresholdSweepSimulator();

    @Test
    void writeThresholdSweepReport() throws IOException {
        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> tierAH = groundTruth.loadTierAH();
        List<GTPersona> all = new ArrayList<>();
        all.addAll(tierA);
        all.addAll(tierAH);

        String report = simulator.report(simulator.parseSurfacedCandidates(INPUT), tierA, tierAH);
        Files.writeString(OUTPUT, report);
    }
}
