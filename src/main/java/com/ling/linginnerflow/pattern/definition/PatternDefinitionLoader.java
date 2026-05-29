package com.ling.linginnerflow.pattern.definition;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads all 12 {@link PatternDefinition} YAMLs from {@code classpath:patterns/*.yaml}
 * at application startup.
 *
 * <p>Fail-fast validation (throws {@link IllegalStateException}) if:
 * <ul>
 *   <li>Not exactly 12 definitions are loaded.</li>
 *   <li>Any {@code pattern_key} is unknown (not in the closed taxonomy) or duplicated.</li>
 *   <li>Any {@code primary_domain} is not one of: self, family, intimate, work, social, body.</li>
 * </ul>
 *
 * <p>YAML field names use snake_case (matching the files); this loader maps them
 * manually to the camelCase {@link PatternDefinition} fields so no extra Jackson
 * dependency is required — SnakeYAML alone suffices (always on the classpath via
 * {@code spring-boot-starter}).
 */
@Slf4j
@Component
public class PatternDefinitionLoader {

    // ── Closed taxonomy ──────────────────────────────────────────────────────

    /** The 12 canonical keys, exactly as they appear in the YAML files. */
    static final Set<String> KNOWN_KEYS = Set.of(
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

    /** The 6 valid primary domain values (product §6). */
    static final Set<String> VALID_DOMAINS = Set.of(
            "self", "family", "intimate", "work", "social", "body"
    );

    private static final int EXPECTED_COUNT = 12;

    // ── State ��───────────────────────────────────────────────────────────────

    /** Key → definition; populated by {@link #load()} and then immutable. */
    private Map<String, PatternDefinition> definitions = Collections.emptyMap();

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void load() {
        log.info("PatternDefinitionLoader: scanning classpath:patterns/*.yaml");

        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();

        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:patterns/*.yaml");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: failed to scan classpath:patterns/*.yaml", e);
        }

        if (resources.length == 0) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: no YAML files found under classpath:patterns/");
        }

        Yaml yaml = new Yaml();
        Map<String, PatternDefinition> loaded = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            log.debug("PatternDefinitionLoader: loading {}", filename);

            Map<String, Object> raw;
            try (InputStream is = resource.getInputStream()) {
                raw = yaml.load(is);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "PatternDefinitionLoader: failed to parse YAML file: " + filename, e);
            }

            PatternDefinition def = mapToDefinition(raw, filename);
            validateDefinition(def, filename);

            if (loaded.containsKey(def.getPatternKey())) {
                throw new IllegalStateException(
                        "PatternDefinitionLoader: duplicate pattern_key '"
                                + def.getPatternKey()
                                + "' found in file: "
                                + filename);
            }

            loaded.put(def.getPatternKey(), def);
        }

        // Validate total count
        if (loaded.size() != EXPECTED_COUNT) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: expected exactly " + EXPECTED_COUNT
                            + " pattern definitions but loaded " + loaded.size()
                            + ". Check that all 12 YAML files are present under"
                            + " src/main/resources/patterns/.");
        }

        // Validate no known key is missing
        for (String knownKey : KNOWN_KEYS) {
            if (!loaded.containsKey(knownKey)) {
                throw new IllegalStateException(
                        "PatternDefinitionLoader: missing required pattern_key '" + knownKey + "'");
            }
        }

        this.definitions = Collections.unmodifiableMap(loaded);
        log.info("PatternDefinitionLoader: successfully loaded {} pattern definitions: {}",
                loaded.size(), loaded.keySet());
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns all loaded definitions, keyed by {@code pattern_key}.
     * The map is unmodifiable.
     */
    public Map<String, PatternDefinition> getAll() {
        return definitions;
    }

    /**
     * Returns the definition for the given key.
     *
     * @throws IllegalArgumentException if the key is not in the taxonomy.
     */
    public PatternDefinition get(String key) {
        PatternDefinition def = definitions.get(key);
        if (def == null) {
            throw new IllegalArgumentException(
                    "Unknown pattern_key: '" + key + "'. Valid keys: " + KNOWN_KEYS);
        }
        return def;
    }

    /**
     * Returns the set of all loaded {@code pattern_key} values.
     */
    public Set<String> keys() {
        return definitions.keySet();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Manually maps a raw SnakeYAML map (snake_case keys) to a
     * {@link PatternDefinition} (camelCase fields).
     */
    @SuppressWarnings("unchecked")
    private PatternDefinition mapToDefinition(Map<String, Object> raw, String filename) {
        PatternDefinition def = new PatternDefinition();

        def.setPatternKey(requireString(raw, "pattern_key", filename));
        def.setDisplayNameZh(requireString(raw, "display_name_zh", filename));
        def.setDisplayNameEn(requireString(raw, "display_name_en", filename));
        def.setPrimaryDomain(requireString(raw, "primary_domain", filename));

        Object alsoIn = raw.get("also_in");
        if (alsoIn instanceof List) {
            def.setAlsoIn((List<String>) alsoIn);
        }

        def.setNeutralDescription(requireString(raw, "neutral_description", filename));

        def.setWhatItIsNot(requireStringList(raw, "what_it_is_not", filename));
        def.setEvidenceShapes(requireStringList(raw, "evidence_shapes", filename));
        def.setReflectivePrompts(requireStringList(raw, "reflective_prompts", filename));

        Object version = raw.get("version");
        if (version instanceof Integer) {
            def.setVersion((Integer) version);
        } else {
            def.setVersion(1);
        }

        // Engine §3.5 additive fields (optional with defaults)
        Object lexicalCues = raw.get("lexical_cues");
        if (lexicalCues instanceof List) {
            def.setLexicalCues((List<String>) lexicalCues);
        }

        Object hydeExemplars = raw.get("hyde_exemplars");
        if (hydeExemplars instanceof Integer) {
            def.setHydeExemplars((Integer) hydeExemplars);
        } else {
            def.setHydeExemplars(3); // default per spec
        }

        return def;
    }

    private void validateDefinition(PatternDefinition def, String filename) {
        // pattern_key must be in the closed taxonomy
        if (!KNOWN_KEYS.contains(def.getPatternKey())) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: unknown pattern_key '"
                            + def.getPatternKey()
                            + "' in file " + filename
                            + ". Valid keys: " + KNOWN_KEYS);
        }

        // primary_domain must be one of the 6 valid domains
        if (!VALID_DOMAINS.contains(def.getPrimaryDomain())) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: invalid primary_domain '"
                            + def.getPrimaryDomain()
                            + "' in file " + filename
                            + ". Valid domains: " + VALID_DOMAINS);
        }

        // neutral_description must be non-blank
        if (def.getNeutralDescription() == null || def.getNeutralDescription().isBlank()) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: neutral_description is blank in file " + filename);
        }

        // what_it_is_not, evidence_shapes, reflective_prompts must each have >= 3 entries
        requireMinSize(def.getWhatItIsNot(), "what_it_is_not", 3, filename);
        requireMinSize(def.getEvidenceShapes(), "evidence_shapes", 3, filename);
        requireMinSize(def.getReflectivePrompts(), "reflective_prompts", 3, filename);
    }

    private String requireString(Map<String, Object> raw, String key, String filename) {
        Object value = raw.get(key);
        if (value == null) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: required field '" + key
                            + "' is missing in file " + filename);
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: required field '" + key
                            + "' is blank in file " + filename);
        }
        return str;
    }

    @SuppressWarnings("unchecked")
    private List<String> requireStringList(Map<String, Object> raw, String key, String filename) {
        Object value = raw.get(key);
        if (!(value instanceof List)) {
            throw new IllegalStateException(
                    "PatternDefinitionLoader: required list field '" + key
                            + "' is missing or not a list in file " + filename);
        }
        return (List<String>) value;
    }

    private void requireMinSize(List<?> list, String fieldName, int minSize, String filename) {
        if (list == null || list.size() < minSize) {
            int actual = list == null ? 0 : list.size();
            throw new IllegalStateException(
                    "PatternDefinitionLoader: field '" + fieldName
                            + "' must have at least " + minSize + " entries but has "
                            + actual + " in file " + filename);
        }
    }
}
