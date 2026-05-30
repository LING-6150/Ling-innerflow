package com.ling.linginnerflow.pattern.eval;

import java.util.List;

public record GTPersona(
        String id,
        String generatorModel,
        List<GTLabel> truePatterns,
        List<GTLabel> decoyPatterns,
        List<String> crisisSeeds,
        List<CorpusRecord> corpus
) {
    public GTPersona {
        truePatterns = List.copyOf(truePatterns);
        decoyPatterns = List.copyOf(decoyPatterns);
        crisisSeeds = List.copyOf(crisisSeeds);
        corpus = List.copyOf(corpus);
    }
}
