package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.verify.VerificationResult;

import java.util.List;

public class PassThroughVerifier {
    public List<VerificationResult> verify(String patternKey, PatternDefinition def, List<CorpusDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }
        return docs.stream()
                .map(doc -> result(def, doc))
                .toList();
    }

    private VerificationResult result(PatternDefinition def, CorpusDoc doc) {
        VerificationResult result = new VerificationResult();
        result.setDocId(doc.getDocId());
        result.setSupports(true);
        result.setVerbatimQuotable(true);
        result.setVerbatimSpan(truncate(doc.getText(), 280));
        result.setInterpretation("[verifier disabled - ablation run]");
        result.setInferredDomain(Domain.valueOf(def.getPrimaryDomain()));
        return result;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
