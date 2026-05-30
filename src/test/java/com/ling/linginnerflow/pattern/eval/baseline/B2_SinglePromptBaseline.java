package com.ling.linginnerflow.pattern.eval.baseline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class B2_SinglePromptBaseline implements Baseline {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> RESPONSE_TYPE = new TypeReference<>() {
    };
    private static final String OFFLINE_MESSAGE = "B2 requires -Dpattern.eval.b2.live=true (consumes network/tokens).";

    private final ChatClient chatClient;
    private final PatternDefinitionLoader defs;

    @Value("${pattern.eval.b2.live:false}")
    boolean live;

    public B2_SinglePromptBaseline(ChatClient.Builder builder, PatternDefinitionLoader defs) {
        this.chatClient = builder.build();
        this.defs = defs;
        this.live = Boolean.parseBoolean(System.getProperty("pattern.eval.b2.live", "false"));
    }

    @Override
    public Set<PredictedPattern> predict(GTPersona persona) {
        if (!live) {
            throw new IllegalStateException(OFFLINE_MESSAGE);
        }

        String raw = chatClient.prompt()
                .user(buildPrompt(persona))
                .call()
                .content();
        return parseResponse(raw);
    }

    String buildPrompt(GTPersona persona) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are evaluating a fixed pattern taxonomy against one user's corpus.\n")
                .append("Return only strict JSON with this schema: ")
                .append("[{\"pattern_key\":\"...\",\"domain\":\"...\"}].\n")
                .append("Use only these domain values: self, family, intimate, work, social, body.\n")
                .append("The pattern_key must be one of this closed set; do not invent keys.\n\n")
                .append("Closed taxonomy:\n");

        defs.keys().stream()
                .sorted()
                .forEach(key -> appendDefinition(prompt, key));

        prompt.append("\nUser corpus for persona ")
                .append(persona.id())
                .append(":\n");
        for (CorpusRecord record : persona.corpus()) {
            prompt.append("- ")
                    .append(record.date().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .append(" [")
                    .append(record.type())
                    .append("] ")
                    .append(record.text())
                    .append('\n');
        }

        prompt.append("\nIdentify present patterns from the closed taxonomy only. ")
                .append("If no pattern is supported, return [].");
        return prompt.toString();
    }

    Set<PredictedPattern> parseResponse(String llmRaw) {
        String json = extractJsonArray(llmRaw);
        if (json == null) {
            return Set.of();
        }

        List<Map<String, String>> rows;
        try {
            rows = OBJECT_MAPPER.readValue(json, RESPONSE_TYPE);
        } catch (JsonProcessingException e) {
            return Set.of();
        }

        Set<String> knownKeys = defs.keys();
        Set<PredictedPattern> parsed = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            String patternKey = normalized(row.get("pattern_key"));
            String domainRaw = normalized(row.get("domain"));
            if (patternKey == null || domainRaw == null || !knownKeys.contains(patternKey)) {
                continue;
            }

            try {
                parsed.add(new PredictedPattern(patternKey, Domain.valueOf(domainRaw.toLowerCase(Locale.ROOT))));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed domains from the untrusted LLM response.
            }
        }
        return Set.copyOf(parsed);
    }

    private void appendDefinition(StringBuilder prompt, String key) {
        PatternDefinition definition = defs.get(key);
        prompt.append("- pattern_key: ")
                .append(key)
                .append("; primary_domain: ")
                .append(definition.getPrimaryDomain())
                .append("; description: ")
                .append(definition.getNeutralDescription())
                .append('\n');
    }

    private String extractJsonArray(String raw) {
        if (raw == null) {
            return null;
        }

        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end < start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
