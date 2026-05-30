package com.ling.linginnerflow.pattern.definition;

import com.ling.linginnerflow.pattern.domain.Domain;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternDefinitionLoaderTest {

    private static final Set<String> CLOSED_PATTERN_KEYS = Set.of(
            "comparison_loop",
            "perfectionism",
            "people_pleasing",
            "boundary_difficulty",
            "self_criticism",
            "rumination",
            "avoidance",
            "emotional_suppression",
            "family_pressure",
            "worth_through_achievement",
            "conflict_aversion",
            "over_responsibility"
    );

    private static final Set<String> VALID_DOMAIN_VALUES = Arrays.stream(Domain.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @Test
    void loads_all_twelve_keys() {
        PatternDefinitionLoader loader = loadRealDefinitions();

        assertThat(loader.keys())
                .hasSize(12)
                .containsExactlyInAnyOrderElementsOf(CLOSED_PATTERN_KEYS);
        assertThat(VALID_DOMAIN_VALUES)
                .containsExactlyInAnyOrder("self", "family", "intimate", "work", "social", "body");
    }

    @Test
    void all_primary_domains_are_valid() {
        PatternDefinitionLoader loader = loadRealDefinitions();

        assertThat(loader.getAll().values())
                .extracting(PatternDefinition::getPrimaryDomain)
                .allSatisfy(domain -> assertThat(VALID_DOMAIN_VALUES).contains(domain));
    }

    @Test
    void all_also_in_domains_are_valid() {
        PatternDefinitionLoader loader = loadRealDefinitions();

        assertThat(loader.getAll().values())
                .flatExtracting(definition -> Objects.requireNonNullElse(definition.getAlsoIn(), List.<String>of()))
                .allSatisfy(domain -> assertThat(VALID_DOMAIN_VALUES).contains(domain));
    }

    @Test
    void hyde_exemplars_present_and_positive() {
        PatternDefinitionLoader loader = loadRealDefinitions();

        assertThat(loader.getAll().values())
                .allSatisfy(definition -> assertThat(definition.getHydeExemplars()).isGreaterThanOrEqualTo(1));
    }

    @Test
    void lexical_cues_non_empty() {
        PatternDefinitionLoader loader = loadRealDefinitions();

        assertThat(loader.getAll().values())
                .allSatisfy(definition -> assertThat(definition.getLexicalCues()).hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    void fails_fast_on_unknown_domain() {
        assertThatThrownBy(() -> loadFromFixture("unknown-domain"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid primary_domain")
                .hasMessageContaining("chimera");
    }

    @Test
    void fails_fast_on_wrong_count() {
        assertThatThrownBy(() -> loadFromFixture("wrong-count"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12");
    }

    @Test
    void fails_fast_on_duplicate_key() {
        assertThatThrownBy(() -> loadFromFixture("duplicate-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate pattern_key")
                .hasMessageContaining("people_pleasing");
    }

    private PatternDefinitionLoader loadRealDefinitions() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }

    private PatternDefinitionLoader loadFromFixture(String fixtureName) throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        URL fixtureRoot = Path.of("src/test/resources/patterns-invalid", fixtureName)
                .toUri()
                .toURL();

        try (URLClassLoader fixtureClassLoader = new URLClassLoader(
                new URL[]{fixtureRoot},
                null
        )) {
            Thread.currentThread().setContextClassLoader(fixtureClassLoader);
            PatternDefinitionLoader loader = new PatternDefinitionLoader();
            loader.load();
            return loader;
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
