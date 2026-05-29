package com.ling.linginnerflow.pattern.entity;

import com.ling.linginnerflow.pattern.domain.SourceType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single piece of evidence inside an EvidenceChain (§9).
 *
 * Immutability rule: once written, EvidenceItems are never updated in place.
 * Engine refreshes write a new EvidenceChain with new EvidenceItems; old items
 * are retained for audit and for the "growth over time" view.
 *
 * Safety rule: items whose source message triggered a crisis flag must NOT be
 * written here — filtered upstream in PatternDiscoveryService (§9, §13).
 *
 * V1 invariants (enforced in PatternDiscoveryService, not in this entity):
 *  - Every surfaced PatternInstance must have >= 3 EvidenceItems.
 *  - At least one EvidenceItem per chain must have isVerbatim = true.
 */
@Data
@Entity
@Table(name = "evidence_item")
public class EvidenceItem {

    /** UUID primary key — assigned in @PrePersist. */
    @Id
    private String id;

    /** FK to the containing EvidenceChain. */
    @Column(nullable = false)
    private String evidenceChainId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    /**
     * Stable reference to the originating record
     * (e.g. chat_message.id, journal entry id, wiki_change_id).
     * Used for deep-linking in the Insight Panel evidence drawer.
     */
    @Column(nullable = false)
    private String sourceRef;

    /** When the source event occurred — used for timeline ordering in the UI. */
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Verbatim quote or concise paraphrase of the user's own words, <= 280 chars.
     * The UI renders verbatim excerpts in quote marks.
     */
    @Column(length = 280, nullable = false)
    private String excerpt;

    /** True = direct quote from the source; false = paraphrase by the engine. */
    private boolean isVerbatim;

    /**
     * One neutral sentence linking this excerpt to the parent pattern.
     * Written by the LLM and subject to the language-firewall linter (§13).
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String interpretation;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
