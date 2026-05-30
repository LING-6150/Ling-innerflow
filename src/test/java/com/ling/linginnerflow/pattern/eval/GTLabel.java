package com.ling.linginnerflow.pattern.eval;

import com.ling.linginnerflow.pattern.domain.Domain;

public record GTLabel(
        String patternKey,
        Domain domain,
        String intendedLevel,
        String notes
) {
}
