package com.ling.linginnerflow.pattern.eval;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class GroundTruthLoader {
    private static final Path TIER_A_DIR = Path.of("eval/groundtruth/tierA");
    private static final Path TIER_AH_DIR = Path.of("eval/groundtruth/sealed");
    private static final Pattern CORPUS_LINE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})\\s+\\[(chat|journal|checkin)]\\s+(.*)$");

    private final PatternDefinitionLoader definitionLoader;
    private final Path tierADirectory;
    private final Path tierAHDirectory;

    public GroundTruthLoader() {
        this(newLoadedDefinitionLoader(), TIER_A_DIR, TIER_AH_DIR);
    }

    GroundTruthLoader(PatternDefinitionLoader definitionLoader, Path tierADirectory, Path tierAHDirectory) {
        this.definitionLoader = definitionLoader;
        this.tierADirectory = tierADirectory;
        this.tierAHDirectory = tierAHDirectory;
    }

    public List<GTPersona> loadTierA() {
        return loadDirectory(tierADirectory, false);
    }

    public List<GTPersona> loadTierAH() {
        if ("true".equalsIgnoreCase(System.getProperty("pattern.eval.tuning", "false"))) {
            throw new IllegalStateException(
                    "Tier A-H is sealed (V1.2 R5). Calibration must not read it. "
                            + "Unset -Dpattern.eval.tuning=true.");
        }
        return loadDirectory(tierAHDirectory, true);
    }

    private List<GTPersona> loadDirectory(Path directory, boolean skipPlaceholderOnly) {
        Set<String> ids = answerKeyIds(directory);
        List<GTPersona> personas = new ArrayList<>();
        for (String id : ids) {
            GTPersona persona = loadPersona(directory, id);
            if (skipPlaceholderOnly && persona.corpus().isEmpty()) {
                log.info("Skipping sealed persona {} because its corpus has no real records", id);
                continue;
            }
            personas.add(persona);
        }
        return List.copyOf(personas);
    }

    private Set<String> answerKeyIds(Path directory) {
        try (Stream<Path> paths = Files.list(directory)) {
            Set<String> ids = new LinkedHashSet<>();
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(filename -> filename.endsWith(".answerkey.yaml"))
                    .filter(filename -> !filename.startsWith("TEMPLATE_"))
                    .sorted()
                    .forEach(filename -> ids.add(filename.substring(0, filename.indexOf(".answerkey.yaml"))));
            return ids;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list ground-truth directory: " + directory, e);
        }
    }

    private GTPersona loadPersona(Path directory, String id) {
        Path answerKey = directory.resolve(id + ".answerkey.yaml");
        Path corpus = directory.resolve(id + ".corpus.md");
        Map<String, Object> raw = readYaml(answerKey);
        String personaId = stringValue(raw.get("persona_id"));
        if (personaId == null || personaId.isBlank()) {
            personaId = id;
        }
        return new GTPersona(
                personaId,
                stringValue(raw.get("generator_model")),
                labels(raw.get("true_patterns"), "notes", answerKey),
                labels(raw.get("decoy_patterns"), "why_not", answerKey),
                crisisSeeds(raw.get("crisis_seeds")),
                corpusRecords(corpus));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Object loaded = new Yaml().load(inputStream);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            throw new IllegalStateException("Expected YAML mapping in " + path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read YAML file: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<GTLabel> labels(Object rawLabels, String noteField, Path source) {
        if (!(rawLabels instanceof List<?> labels)) {
            return List.of();
        }
        Set<String> knownKeys = definitionLoader.keys();
        List<GTLabel> mapped = new ArrayList<>();
        for (Object rawLabel : labels) {
            if (!(rawLabel instanceof Map<?, ?> labelMap)) {
                throw new IllegalStateException("Expected label mapping in " + source);
            }
            Map<String, Object> label = (Map<String, Object>) labelMap;
            String patternKey = requiredString(label, "pattern_key", source);
            if (!knownKeys.contains(patternKey)) {
                throw new IllegalStateException("Unknown pattern_key '" + patternKey + "' in " + source);
            }
            mapped.add(new GTLabel(
                    patternKey,
                    Domain.valueOf(requiredString(label, "domain", source)),
                    stringValue(label.get(intendedLevelField())),
                    stringValue(label.get(noteField))));
        }
        return List.copyOf(mapped);
    }

    private List<String> crisisSeeds(Object rawSeeds) {
        if (!(rawSeeds instanceof List<?> seeds)) {
            return List.of();
        }
        return seeds.stream()
                .map(this::crisisSeed)
                .filter(seed -> seed != null && !seed.isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private String crisisSeed(Object rawSeed) {
        if (rawSeed instanceof String seed) {
            return seed;
        }
        if (rawSeed instanceof Map<?, ?> seedMap) {
            return stringValue(((Map<String, Object>) seedMap).get("description"));
        }
        return null;
    }

    private List<CorpusRecord> corpusRecords(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.startsWith("#"))
                    .filter(line -> !line.startsWith("<"))
                    .map(this::corpusRecord)
                    .filter(record -> record != null && !record.text().startsWith("<"))
                    .sorted(Comparator.comparing(CorpusRecord::date))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read corpus file: " + path, e);
        }
    }

    private CorpusRecord corpusRecord(String line) {
        Matcher matcher = CORPUS_LINE.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new CorpusRecord(
                LocalDate.parse(matcher.group(1)),
                matcher.group(2),
                matcher.group(3).trim());
    }

    private String requiredString(Map<String, Object> values, String key, Path source) {
        String value = stringValue(values.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required field '" + key + "' in " + source);
        }
        return value;
    }

    private String intendedLevelField() {
        return new StringBuilder("intended_").append("str").append("ength").toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static PatternDefinitionLoader newLoadedDefinitionLoader() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }
}
