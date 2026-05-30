package com.ling.linginnerflow.pattern.retrieval;

import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EvidenceRetrievalService {

    private static final int TOP_K = 7;

    private final PatternDefinitionLoader definitionLoader;

    public List<CorpusDoc> retrieve(String patternKey, List<CorpusDoc> corpus) {
        PatternDefinition definition = definitionLoader.get(patternKey);
        if (corpus == null || corpus.isEmpty()) {
            return List.of();
        }
        return corpus.stream()
                .filter(doc -> doc.getText() != null && !doc.getText().isBlank())
                .sorted(Comparator
                        .comparingInt((CorpusDoc doc) -> score(definition, doc)).reversed()
                        .thenComparing(CorpusDoc::getOccurredAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CorpusDoc::getDocId))
                .limit(TOP_K)
                .toList();
    }

    private int score(PatternDefinition definition, CorpusDoc doc) {
        String text = doc.getText().toLowerCase(Locale.ROOT);
        int score = 0;
        if (definition.getLexicalCues() != null) {
            for (String cue : definition.getLexicalCues()) {
                if (cue != null && !cue.isBlank()
                        && text.contains(cue.toLowerCase(Locale.ROOT))) {
                    score += 3;
                }
            }
        }
        if (definition.getEvidenceShapes() != null) {
            for (String shape : definition.getEvidenceShapes()) {
                if (shape != null && !shape.isBlank()
                        && text.contains(shape.toLowerCase(Locale.ROOT))) {
                    score += 2;
                }
            }
        }
        return score;
    }
}
