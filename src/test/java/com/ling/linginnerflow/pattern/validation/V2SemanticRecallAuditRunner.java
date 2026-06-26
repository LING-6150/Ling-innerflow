package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.GroundTruthLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gated, eval-only runner for the V2.2 semantic candidate recall audit.
 *
 * Run with:
 * {@code ./mvnw -o test -Dpattern.eval.semantic-recall-audit=true -Dtest=V2SemanticRecallAuditRunner}
 *
 * <p>Writes {@code eval/RESULTS_V2_SEMANTIC_RECALL_AUDIT.md}. Two embedding modes:
 * <ul>
 *   <li><b>deterministic</b> (default): hashed-ngram, fully offline & reproducible.
 *       Validates the plumbing only — NOT a recall claim.</li>
 *   <li><b>openai</b> (real result): add {@code -Dpattern.eval.semantic-recall-embedding=openai}.
 *       Reuses the proven {@code StandalonePipeline.createCountingEmbeddingModel()}
 *       (text-embedding-3-small) which reads the key from env
 *       {@code MY_OPENAI_KEY} / {@code PERSONAL_OPENAI_KEY} / {@code OPENAI_API_KEY}.
 *       Run this on the machine where the key is configured (e.g. IntelliJ run config).</li>
 * </ul>
 *
 * <p>NOTE: the openai mode was authored here but NOT executed (no key on the
 * authoring machine); it compiles and mirrors the live-validated wiring, but its
 * real-embedding run still needs to be done where the key exists.
 *
 * <p>Boundaries: does NOT touch production {@code PatternRecallService}; full
 * decoys (ah-05/ah-06) are used for false-positive confirmation only, never for
 * tuning τ/topK (the seal is procedural — do not tune to suppress their FPs).
 */
@Tag("validation")
@EnabledIfSystemProperty(named = "pattern.eval.semantic-recall-audit", matches = "true")
class V2SemanticRecallAuditRunner {

    private static final Path OUTPUT = Path.of("eval/RESULTS_V2_SEMANTIC_RECALL_AUDIT.md");

    private static final int EMBED_DIM = 256;
    private static final int[] TOP_KS = {3, 5, 12};
    private static final double[] TAUS = {0.15, 0.25, 0.35, 0.45};

    /** Deterministic offline embedding: hashed character 3-grams (NOT semantic). */
    private static final SemanticCandidateRecallAudit.EmbeddingFn DETERMINISTIC_EMBED = text -> {
        float[] v = new float[EMBED_DIM];
        String s = text == null ? "" : text.toLowerCase();
        for (int i = 0; i + 3 <= s.length(); i++) {
            int h = (s.substring(i, i + 3).hashCode() & 0x7fffffff) % EMBED_DIM;
            v[h] += 1f;
        }
        return v;
    };

    private final GroundTruthLoader groundTruth = new GroundTruthLoader();
    private final SemanticCandidateRecallAudit audit = new SemanticCandidateRecallAudit();

    @Test
    void writeSemanticRecallAuditReport() throws IOException {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();

        Map<String, List<String>> exemplars = buildExemplars(loader);

        List<GTPersona> tierA = groundTruth.loadTierA();
        List<GTPersona> fullDecoys = groundTruth.loadTierAH().stream()
                .filter(p -> p.truePatterns().isEmpty())
                .toList();

        SemanticCandidateRecallAudit.EmbeddingFn embed;
        String embeddingLabel;
        if ("openai".equalsIgnoreCase(System.getProperty("pattern.eval.semantic-recall-embedding", "deterministic"))) {
            // Real embeddings via the live-validated helper; reads key from env.
            SemanticCandidateRecallAudit.EmbeddingFn real = StandalonePipeline.createCountingEmbeddingModel()::embed;
            embed = real;
            embeddingLabel = "OpenAI text-embedding-3-small (live)";
        } else {
            embed = DETERMINISTIC_EMBED;
            embeddingLabel = "deterministic-hashed-ngram (offline, harness validation only)";
        }

        String report = audit.sweepReport(
                tierA, fullDecoys, exemplars, embed, TOP_KS, TAUS, embeddingLabel);

        Files.writeString(OUTPUT, report);
    }

    /** Exemplar texts per pattern, mirroring PatternHyDEService.exemplarVectors(). */
    private Map<String, List<String>> buildExemplars(PatternDefinitionLoader loader) {
        Map<String, List<String>> exemplars = new LinkedHashMap<>();
        for (PatternDefinition def : loader.getAll().values()) {
            List<String> texts = new ArrayList<>();
            if (def.getEvidenceShapes() != null) {
                texts.addAll(def.getEvidenceShapes());
            }
            if (def.getLexicalCues() != null && !def.getLexicalCues().isEmpty()) {
                texts.add(String.join(" ", def.getLexicalCues()));
            }
            if (texts.isEmpty() && def.getNeutralDescription() != null) {
                texts.add(def.getNeutralDescription());
            }
            exemplars.put(def.getPatternKey(), texts);
        }
        return exemplars;
    }
}
