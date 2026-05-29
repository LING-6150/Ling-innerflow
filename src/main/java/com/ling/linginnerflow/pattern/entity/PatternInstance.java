package com.ling.linginnerflow.pattern.entity;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-user instance of a pattern (§8.2).
 *
 * Uniqueness invariant: at most one active (non-rejected) instance per
 * (user_id, pattern_key, domain). Enforced in PatternDiscoveryService; the
 * query helper findByUserIdAndPatternKeyAndDomainAndStatusNot supports this.
 *
 * hidden=true is used for sub-threshold instances that the engine tracked
 * internally but did not surface in the Insight Panel.
 */
@Data
@Entity
@Table(name = "pattern_instance")
public class PatternInstance {

    /** UUID primary key — assigned in @PrePersist, not auto-incremented. */
    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    /** FK to PatternDefinition (static YAML, loaded at startup). */
    @Column(nullable = false)
    private String patternKey;

    /** User-specific primary domain for this instance; may differ from the pattern's canonical default. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Domain domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatternStatus status;

    /** Model confidence at last refresh, in [0, 1]. */
    private double confidence;

    /**
     * LLM-written, user-editable narrative summary.
     * Subject to the language-firewall linter before being persisted (§13).
     */
    @Column(columnDefinition = "TEXT")
    private String personalizedSummary;

    /** User's own words after confirming or refining the pattern; nullable. */
    @Column(columnDefinition = "TEXT")
    private String userNote;

    /** FK to the most recent EvidenceChain for this instance. */
    private String evidenceChainId;

    private LocalDateTime firstObservedAt;

    private LocalDateTime lastObservedAt;

    /** Updated on any user review action (confirm / partial / reject / defer / archive); nullable. */
    private LocalDateTime lastReviewedAt;

    /** How many times the engine has re-evaluated this instance. */
    private int refreshCount;

    /** Schema version for forward-compatible migrations. */
    private int schemaVersion;

    /** True for sub-threshold instances tracked internally but not surfaced to the user. */
    private boolean hidden;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (firstObservedAt == null) {
            firstObservedAt = LocalDateTime.now();
        }
        if (lastObservedAt == null) {
            lastObservedAt = LocalDateTime.now();
        }
        if (schemaVersion == 0) {
            schemaVersion = 1;
        }
    }
}
