package com.ling.linginnerflow.pattern.eval;

import com.ling.linginnerflow.pattern.domain.Domain;

public record PredictedPattern(
        String patternKey,
        Domain domain
) {
}
