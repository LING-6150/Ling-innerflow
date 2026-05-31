package com.ling.linginnerflow.pattern.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

class AbstainGate {
    enum Mode {
        STRICT_R2,
        SANITY_LABEL_BIAS,
        QUOTE_VERIFY_R3
    }

    private final PatternDefinitionLoader definitions;
    private final StandalonePipeline.CountingChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Mode mode;

    AbstainGate(PatternDefinitionLoader definitions, StandalonePipeline.CountingChatModel chatModel) {
        this(definitions, chatModel, Mode.STRICT_R2);
    }

    AbstainGate(PatternDefinitionLoader definitions, StandalonePipeline.CountingChatModel chatModel, Mode mode) {
        this.definitions = definitions;
        this.chatModel = chatModel;
        this.mode = mode;
    }

    AbstainResult judge(GTPersona persona, String patternKey, StandalonePipeline.PatternTrace trace) {
        if (trace.evidenceItems().size() < 3) {
            return new AbstainResult(AbstainDecision.CHAIN_TOO_WEAK, 0.0, 0.0,
                    "Evidence chain has fewer than 3 items.");
        }

        try {
            ChatResponse response = chatModel.call(new Prompt(buildPrompt(persona, patternKey, trace),
                    OpenAiChatOptions.builder().model("gpt-4o-mini").temperature(0.0).build()));
            return parse(responseText(response));
        } catch (Exception e) {
            return new AbstainResult(AbstainDecision.SYSTEM_ERROR, 0.0, 0.0, e.getMessage());
        }
    }

    private String buildPrompt(GTPersona persona, String patternKey, StandalonePipeline.PatternTrace trace) {
        PatternDefinition definition = definitions.get(patternKey);
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the ").append(mode == Mode.SANITY_LABEL_BIAS ? "R1.5 sanity" : "R2")
                .append(" abstain gate for InnerFlow, a mirror product, not a therapist.\n")
                .append("Your job is to block unsafe false labels without becoming an all-reject classifier.\n")
                .append("Use the evidence shapes as the main positive-fit test.\n")
                .append("Return only strict JSON with this schema:\n")
                .append("{\"decision\":\"LABEL|ABSTAIN_NO_SAFE_V1_LABEL\",")
                .append("\"primary_reason\":\"INSUFFICIENT_POSITIVE_FIT|LOW_SPECIFICITY|DECOY_MATCH|MODEL_UNCERTAIN\",")
                .append("\"fit_score\":0.0,\"specificity_score\":0.0,")
                .append("\"matched_evidence_shape\":\"copy exact evidence_shape when LABEL\",")
                .append("\"supporting_quote\":\"copy exact substring from evidence when LABEL\",")
                .append("\"rationale\":\"short evidence-grounded explanation\"}\n\n")
                .append("Candidate pattern:\n")
                .append(definitionBlock(definition))
                .append("\nAdjacent/confusable patterns:\n");

        adjacentDefinitions(patternKey).forEach(adjacent -> prompt.append(definitionBlock(adjacent)).append('\n'));

        prompt.append("\nCandidate evidence for persona ").append(persona.id()).append(":\n");
        int index = 1;
        for (StandalonePipeline.EvidenceTrace item : trace.evidenceItems()) {
            prompt.append(index++).append(". excerpt: ").append(item.excerpt()).append('\n')
                    .append("   interpretation: ").append(item.interpretation()).append('\n')
                    .append("   verbatim: ").append(item.verbatim()).append('\n');
        }

        prompt.append("\nKnown hard-negative guidance for this persona/pattern, if any:\n")
                .append(decoyGuidance(persona, patternKey))
                .append("\nDecision rules:\n")
                .append(decisionRules())
                .append("- Do not reward eloquent interpretations; judge quote-level fit to observable evidence_shapes.\n");
        return prompt.toString();
    }

    private String decisionRules() {
        if (mode == Mode.SANITY_LABEL_BIAS) {
            return "- This is a sanity check, intentionally label-biased.\n"
                    + "- LABEL when there are at least 3 evidence excerpts and the candidate is not explicitly contradicted by hard-negative guidance.\n"
                    + "- ABSTAIN only when the known hard-negative guidance directly explains this exact candidate, or the evidence is obviously unrelated to the definition.\n"
                    + "- If unsure between LABEL and ABSTAIN, choose LABEL for this sanity run.\n"
                    + "- Use DECOY_MATCH only when the hard-negative guidance directly applies.\n"
                    + "- Set non-zero fit_score when any evidence shape is plausibly present.\n";
        }
        if (mode == Mode.QUOTE_VERIFY_R3) {
            return "- This is R3: use the R1.5 label-biased posture, but every LABEL must be mechanically verifiable.\n"
                    + "- LABEL when there are at least 3 evidence excerpts and the candidate is not explicitly contradicted by hard-negative guidance.\n"
                    + "- For every LABEL, matched_evidence_shape must exactly copy one evidence_shape from the candidate definition.\n"
                    + "- For every LABEL, supporting_quote must exactly copy a quote from one candidate evidence excerpt.\n"
                    + "- ABSTAIN when you cannot provide both an exact matched_evidence_shape and an exact supporting_quote.\n"
                    + "- ABSTAIN when the known hard-negative guidance directly explains this exact candidate.\n"
                    + "- If unsure between LABEL and ABSTAIN, choose LABEL only when quote-level support is exact.\n"
                    + "- Use DECOY_MATCH only when the hard-negative guidance directly applies.\n";
        }
        return "- LABEL when at least 2 evidence excerpts directly match the candidate evidence_shapes and the chain shows recurrence.\n"
                + "- LABEL does not require perfect specificity; adjacent-pattern overlap is acceptable when this label is still directly supported.\n"
                + "- ABSTAIN when the evidence is only mood, topic, diagnosis, causal speculation, or generic distress.\n"
                + "- ABSTAIN when known hard-negative guidance better explains the evidence than the candidate label.\n"
                + "- Use DECOY_MATCH only when the hard-negative guidance directly applies.\n"
                + "- Set fit_score and specificity_score from 0.0 to 1.0; do not leave them at 0 unless there is no fit.\n";
    }

    private String definitionBlock(PatternDefinition definition) {
        return "- pattern_key: " + definition.getPatternKey()
                + "; domain: " + definition.getPrimaryDomain()
                + "; description: " + nullSafe(definition.getNeutralDescription())
                + "; evidence_shapes: " + nullSafe(String.join(" | ", safeList(definition.getEvidenceShapes())))
                + "; what_it_is_not: " + nullSafe(String.join(" | ", safeList(definition.getWhatItIsNot())));
    }

    private List<PatternDefinition> adjacentDefinitions(String patternKey) {
        PatternDefinition target = definitions.get(patternKey);
        String domain = target.getPrimaryDomain();
        return definitions.keys().stream()
                .filter(key -> !key.equals(patternKey))
                .map(definitions::get)
                .sorted(Comparator
                        .comparing((PatternDefinition definition) -> !Objects.equals(definition.getPrimaryDomain(), domain))
                        .thenComparing(PatternDefinition::getPatternKey))
                .limit(3)
                .toList();
    }

    private String decoyGuidance(GTPersona persona, String patternKey) {
        return persona.decoyPatterns().stream()
                .filter(label -> label.patternKey().equals(patternKey))
                .findFirst()
                .map(label -> label.notes() == null || label.notes().isBlank() ? "None." : label.notes())
                .orElse("None.");
    }

    AbstainResult parse(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            return new AbstainResult(AbstainDecision.SYSTEM_ERROR, 0.0, 0.0, "No JSON object returned.");
        }
        try {
            Map<?, ?> row = objectMapper.readValue(json, Map.class);
            String decision = stringValue(row.get("decision"));
            String primaryReason = stringValue(row.get("primary_reason"));
            double fitScore = doubleValue(row.get("fit_score"));
            double specificityScore = doubleValue(row.get("specificity_score"));
            String rationale = stringValue(row.get("rationale"));
            String matchedEvidenceShape = stringValue(row.get("matched_evidence_shape"));
            String supportingQuote = stringValue(row.get("supporting_quote"));

            if ("LABEL".equalsIgnoreCase(decision)) {
                return new AbstainResult(AbstainDecision.LABEL, fitScore, specificityScore, rationale,
                        matchedEvidenceShape, supportingQuote);
            }
            return new AbstainResult(reason(primaryReason), fitScore, specificityScore, rationale,
                    matchedEvidenceShape, supportingQuote);
        } catch (Exception e) {
            return new AbstainResult(AbstainDecision.SYSTEM_ERROR, 0.0, 0.0, e.getMessage());
        }
    }

    private AbstainDecision reason(String primaryReason) {
        if (primaryReason == null) {
            return AbstainDecision.MODEL_UNCERTAIN;
        }
        try {
            return AbstainDecision.valueOf(primaryReason.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AbstainDecision.MODEL_UNCERTAIN;
        }
    }

    private String responseText(ChatResponse response) {
        return response == null || response.getResult() == null || response.getResult().getOutput() == null
                ? ""
                : response.getResult().getOutput().getText();
    }

    private String extractJsonObject(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return start >= 0 && end >= start ? raw.substring(start, end + 1) : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private List<String> safeList(List<String> value) {
        return value == null ? List.of() : value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    record AbstainResult(AbstainDecision decision, double fitScore, double specificityScore, String rationale,
                         String matchedEvidenceShape, String supportingQuote) {
        AbstainResult(AbstainDecision decision, double fitScore, double specificityScore, String rationale) {
            this(decision, fitScore, specificityScore, rationale, null, null);
        }
    }
}
