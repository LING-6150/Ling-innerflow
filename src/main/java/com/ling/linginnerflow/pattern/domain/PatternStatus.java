package com.ling.linginnerflow.pattern.domain;

/**
 * Lifecycle states for a PatternInstance (§8.2, §10).
 *
 * candidate          — engine surfaced it; awaiting user review.
 * confirmed          — user affirmed the pattern in full.
 * partially_confirmed — user accepted with edits or partial scope.
 * rejected           — user denied; 90-day cooldown applies.
 * deferred           — user postponed review.
 * archived           — user retired a previously confirmed pattern ("this isn't me anymore").
 */
public enum PatternStatus {
    candidate,
    confirmed,
    partially_confirmed,
    rejected,
    deferred,
    archived
}
