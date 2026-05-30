package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Baseline B1: lexical cue matching against YAML {@code lexical_cues}.
 *
 * <p>For each pattern, counts distinct corpus records whose text contains at least one
 * lexical cue (case-insensitive substring). Predicts the pattern iff the hit count
 * reaches {@code minCueHits}. Always assigns the pattern's YAML {@code primary_domain} —
 * V1 limit: cross-domain signals are not inferred from text.
 *
 * <p>This is the LLM-homophily-immune floor: no embeddings, no LLM calls.
 */
public class B1_LexicalBaseline implements Baseline {

    private final PatternDefinitionLoader defs;
    private final int minCueHits;

    public B1_LexicalBaseline(PatternDefinitionLoader defs, int minCueHits) {
        this.defs = defs;
        this.minCueHits = minCueHits;
    }

    @Override
    public Set<PredictedPattern> predict(GTPersona persona) {
        return defs.getAll().values().stream()
                .filter(def -> hitCount(persona, def) >= minCueHits)
                .map(def -> new PredictedPattern(def.getPatternKey(), Domain.valueOf(def.getPrimaryDomain())))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String name() {
        return "B1-lexical";
    }

    private int hitCount(GTPersona persona, PatternDefinition def) {
        List<String> cues = def.getLexicalCues();
        if (cues == null || cues.isEmpty()) {
            return 0;
        }
        return (int) persona.corpus().stream()
                .filter(record -> containsAnyCue(record, cues))
                .count();
    }

    private boolean containsAnyCue(CorpusRecord record, List<String> cues) {
        String lowerText = record.text().toLowerCase();
        return cues.stream().anyMatch(cue -> lowerText.contains(cue.toLowerCase()));
    }
}
