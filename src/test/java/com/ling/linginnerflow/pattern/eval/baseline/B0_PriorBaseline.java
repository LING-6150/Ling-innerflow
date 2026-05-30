package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class B0_PriorBaseline implements Baseline {
    private static final int DOMAIN_COUNT = Domain.values().length;

    private final long seed;
    private final Map<String, Double> baseRatesPerKey;
    private final Map<String, Set<Domain>> permittedDomainsByKey;

    public B0_PriorBaseline(long seed, Map<String, Double> baseRatesPerKey) {
        this(seed, baseRatesPerKey, loadedDefinitionLoader());
    }

    B0_PriorBaseline(long seed, Map<String, Double> baseRatesPerKey, PatternDefinitionLoader definitionLoader) {
        this.seed = seed;
        this.baseRatesPerKey = Map.copyOf(Objects.requireNonNull(baseRatesPerKey, "baseRatesPerKey"));
        this.permittedDomainsByKey = permittedDomainsByKey(definitionLoader);
    }

    @Override
    public Set<PredictedPattern> predict(GTPersona persona) {
        Random random = new Random(seed ^ Objects.hashCode(persona == null ? null : persona.id()));
        Set<PredictedPattern> predictions = new LinkedHashSet<>();
        for (String patternKey : orderedPatternKeys()) {
            double probabilityPerDomain = probabilityPerDomain(baseRatesPerKey.getOrDefault(patternKey, 0.0));
            if (probabilityPerDomain <= 0.0) {
                continue;
            }
            for (Domain domain : Domain.values()) {
                if (!permittedDomainsByKey.getOrDefault(patternKey, Set.of()).contains(domain)) {
                    continue;
                }
                if (probabilityPerDomain >= 1.0 || random.nextDouble() < probabilityPerDomain) {
                    predictions.add(new PredictedPattern(patternKey, domain));
                }
            }
        }
        return Set.copyOf(predictions);
    }

    @Override
    public String name() {
        return "B0-prior";
    }

    private List<String> orderedPatternKeys() {
        return permittedDomainsByKey.keySet().stream()
                .sorted()
                .toList();
    }

    private double probabilityPerDomain(double baseRate) {
        if (baseRate <= 0.0) {
            return 0.0;
        }
        if (baseRate >= 1.0) {
            return 1.0;
        }
        return baseRate / DOMAIN_COUNT;
    }

    private Map<String, Set<Domain>> permittedDomainsByKey(PatternDefinitionLoader definitionLoader) {
        return definitionLoader.getAll().entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> permittedDomains(entry.getValue())));
    }

    private Set<Domain> permittedDomains(PatternDefinition definition) {
        List<String> domainNames = new ArrayList<>();
        domainNames.add(definition.getPrimaryDomain());
        if (definition.getAlsoIn() != null) {
            domainNames.addAll(definition.getAlsoIn());
        }
        return domainNames.stream()
                .map(Domain::valueOf)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static PatternDefinitionLoader loadedDefinitionLoader() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }
}
