package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class B0_PriorBaselineTest {
    private final PatternDefinitionLoader definitionLoader = loadedDefinitionLoader();

    @Test
    void sameSeedProducesDeterministicPredictionsAcrossRuns() {
        Map<String, Double> baseRates = baseRates(0.75);
        GTPersona persona = persona("fixed-persona");

        Set<PredictedPattern> first = new B0_PriorBaseline(42L, baseRates, definitionLoader).predict(persona);
        Set<PredictedPattern> second = new B0_PriorBaseline(42L, baseRates, definitionLoader).predict(persona);
        Set<PredictedPattern> third = new B0_PriorBaseline(42L, baseRates, definitionLoader).predict(persona);

        assertThat(first).isEqualTo(second).isEqualTo(third);
    }

    @Test
    void zeroBaseRatesPredictNothing() {
        B0_PriorBaseline baseline = new B0_PriorBaseline(42L, baseRates(0.0), definitionLoader);

        assertThat(baseline.predict(persona("zero"))).isEmpty();
    }

    @Test
    void saturatedBaseRatesPredictEveryTaxonomyPermittedPair() {
        B0_PriorBaseline baseline = new B0_PriorBaseline(42L, baseRates(1.0), definitionLoader);

        assertThat(baseline.predict(persona("all"))).containsExactlyInAnyOrderElementsOf(permittedPairs());
    }

    @Test
    void predictionsNeverUseDomainsOutsidePrimaryAndAlsoIn() {
        B0_PriorBaseline baseline = new B0_PriorBaseline(42L, baseRates(1.0), definitionLoader);

        assertThat(baseline.predict(persona("taxonomy"))).allSatisfy(prediction ->
                assertThat(permittedPairs()).contains(prediction));
    }

    private Map<String, Double> baseRates(double rate) {
        return definitionLoader.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, key -> rate));
    }

    private Set<PredictedPattern> permittedPairs() {
        return definitionLoader.getAll().values().stream()
                .flatMap(definition -> permittedDomains(definition).stream()
                        .map(domain -> new PredictedPattern(definition.getPatternKey(), domain)))
                .collect(Collectors.toSet());
    }

    private List<Domain> permittedDomains(PatternDefinition definition) {
        List<Domain> domains = new ArrayList<>();
        domains.add(Domain.valueOf(definition.getPrimaryDomain()));
        if (definition.getAlsoIn() != null) {
            domains.addAll(definition.getAlsoIn().stream().map(Domain::valueOf).toList());
        }
        return domains.stream().distinct().toList();
    }

    private GTPersona persona(String id) {
        return new GTPersona(id, "test", List.of(), List.of(), List.of(), List.of());
    }

    private static PatternDefinitionLoader loadedDefinitionLoader() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }
}
