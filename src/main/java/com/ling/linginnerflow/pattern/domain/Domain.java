package com.ling.linginnerflow.pattern.domain;

/**
 * The six life-domain taxonomy used by Pattern Discovery V1 (§6).
 * Each PatternInstance is tagged with exactly one domain — the user-specific
 * primary domain for that instance, which may differ from the pattern's
 * canonical default.
 */
public enum Domain {
    self,
    family,
    intimate,
    work,
    social,
    body
}
