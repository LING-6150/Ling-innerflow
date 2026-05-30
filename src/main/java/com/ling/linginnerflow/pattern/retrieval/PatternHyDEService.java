package com.ling.linginnerflow.pattern.retrieval;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatternHyDEService {

    private final PatternDefinitionLoader definitionLoader;
    private final EmbeddingModel embeddingModel;

    public List<float[]> exemplarVectors(String patternKey) {
        PatternDefinition definition = definitionLoader.get(patternKey);
        List<String> exemplars = new ArrayList<>();
        if (definition.getEvidenceShapes() != null) {
            exemplars.addAll(definition.getEvidenceShapes());
        }
        if (definition.getLexicalCues() != null) {
            exemplars.add(String.join(" ", definition.getLexicalCues()));
        }
        if (exemplars.isEmpty()) {
            exemplars.add(definition.getNeutralDescription());
        }
        try {
            return embeddingModel.embed(exemplars);
        } catch (Exception e) {
            log.warn("[PatternHyDE] exemplar embedding unavailable for {}: {}", patternKey, e.getMessage());
            return List.of();
        }
    }
}
