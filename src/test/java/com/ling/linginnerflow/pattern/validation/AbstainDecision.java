package com.ling.linginnerflow.pattern.validation;

enum AbstainDecision {
    LABEL,
    INSUFFICIENT_POSITIVE_FIT,
    LOW_SPECIFICITY,
    DECOY_MATCH,
    MODEL_UNCERTAIN,
    CHAIN_TOO_WEAK,
    SYSTEM_ERROR;

    boolean surfaced() {
        return this == LABEL;
    }
}
