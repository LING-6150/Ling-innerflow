package com.ling.linginnerflow.pattern.retrieval;

import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatternRecallService {

    private final PatternDefinitionLoader definitionLoader;

    public Set<String> recall(List<CorpusDoc> corpus) {
        Set<String> recalled = new LinkedHashSet<>();
        String joined = corpus == null ? "" : corpus.stream()
                .map(CorpusDoc::getText)
                .filter(text -> text != null && !text.isBlank())
                .reduce("", (left, right) -> left + "\n" + right)
                .toLowerCase(Locale.ROOT);

        for (PatternDefinition definition : definitionLoader.getAll().values()) {
            if (matches(definition, joined)) {
                recalled.add(definition.getPatternKey());
            }
        }
        if (recalled.isEmpty()) {
            recalled.addAll(definitionLoader.keys());
        }
        return recalled;
    }

    private boolean matches(PatternDefinition definition, String corpusText) {
        if (definition.getLexicalCues() != null) {
            for (String cue : definition.getLexicalCues()) {
                if (cue != null && !cue.isBlank()
                        && corpusText.contains(cue.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        if (definition.getEvidenceShapes() != null) {
            for (String shape : definition.getEvidenceShapes()) {
                if (shape != null && !shape.isBlank()
                        && corpusText.contains(shape.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }
}
