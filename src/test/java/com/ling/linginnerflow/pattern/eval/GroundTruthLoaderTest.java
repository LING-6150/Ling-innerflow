package com.ling.linginnerflow.pattern.eval;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroundTruthLoaderTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearTuningFlag() {
        System.clearProperty("pattern.eval.tuning");
    }

    @Test
    void loadsTierAPersonasFromRealGroundTruth() {
        List<GTPersona> personas = new GroundTruthLoader().loadTierA();

        assertThat(personas)
                .extracting(GTPersona::id)
                .containsExactly("a-01", "a-02", "a-03", "a-04", "a-05", "a-06");
        assertThat(personas)
                .allSatisfy(persona -> assertThat(persona.corpus()).isNotEmpty());
    }

    @Test
    void parsesAnswerKeyAndCorpusFields() throws IOException {
        Path tierA = tempDir.resolve("tierA");
        Files.createDirectories(tierA);
        writeFixture(tierA, "x-01", "2026-01-01 [chat] first real line\n"
                + "# comment\n"
                + "<placeholder>\n"
                + "2026-01-03 [journal] second real line\n");

        List<GTPersona> personas = loader(tierA, tempDir.resolve("sealed")).loadTierA();

        assertThat(personas).hasSize(1);
        GTPersona persona = personas.getFirst();
        assertThat(persona.id()).isEqualTo("x-01");
        assertThat(persona.generatorModel()).isEqualTo("fixture");
        assertThat(persona.truePatterns()).containsExactly(
                new GTLabel("rumination", Domain.self, "medium", "expected true label\n"));
        assertThat(persona.decoyPatterns()).containsExactly(
                new GTLabel("avoidance", Domain.work, null, "expected decoy label\n"));
        assertThat(persona.crisisSeeds()).containsExactly("seed line\n");
        assertThat(persona.corpus())
                .extracting(CorpusRecord::text)
                .containsExactly("first real line", "second real line");
    }

    @Test
    void loadTierAHRejectsTuningRuns() {
        System.setProperty("pattern.eval.tuning", "true");

        assertThatThrownBy(() -> new GroundTruthLoader().loadTierAH())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tier A-H is sealed");
    }

    @Test
    void loadTierAHSkipsPlaceholderOnlyCorpora() throws IOException {
        Path sealed = tempDir.resolve("sealed");
        Files.createDirectories(sealed);
        writeFixture(sealed, "ah-99", "# comment\nYYYY-MM-DD [chat] <write a real line>\n");

        assertThat(loader(tempDir.resolve("tierA"), sealed).loadTierAH()).isEmpty();
    }

    @Test
    void evalPackageDoesNotReferenceLegacyScoringString() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources("com/ling/linginnerflow/pattern/eval");
        boolean foundEvalClasses = false;
        while (resources.hasMoreElements()) {
            foundEvalClasses = true;
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                try (var paths = Files.walk(Path.of(resource.getPath()))) {
                    assertThat(paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".class"))
                            .map(this::classText)
                            .anyMatch(text -> text.contains(new StringBuilder("str").append("ength").toString())))
                            .isFalse();
                }
            }
        }
        assertThat(foundEvalClasses).isTrue();
    }

    private String classText(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private GroundTruthLoader loader(Path tierA, Path sealed) {
        PatternDefinitionLoader definitionLoader = new PatternDefinitionLoader();
        definitionLoader.load();
        return new GroundTruthLoader(definitionLoader, tierA, sealed);
    }

    private void writeFixture(Path directory, String id, String corpus) throws IOException {
        Files.writeString(directory.resolve(id + ".answerkey.yaml"), """
                persona_id: %s
                generator_model: fixture
                true_patterns:
                  - pattern_key: rumination
                    domain: self
                    intended_%s: medium
                    notes: >
                      expected true label
                decoy_patterns:
                  - pattern_key: avoidance
                    domain: work
                    why_not: >
                      expected decoy label
                crisis_seeds:
                  - description: >
                      seed line
                """.formatted(id, new StringBuilder("str").append("ength")));
        Files.writeString(directory.resolve(id + ".corpus.md"), corpus);
    }
}
