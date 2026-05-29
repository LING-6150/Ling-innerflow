package com.ling.linginnerflow.pattern.corpus;

import com.ling.linginnerflow.pattern.domain.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Normalized, retrievable unit representing a single user-authored record from any source.
 *
 * doc_id  = "{source_type}:{source_ref}"  — stable, content-addressed key used for
 * embedding caching, evidence dedup (§6.3), Jaccard chain comparison (§6.4), and
 * chunk-suffix extension "{source_type}:{source_ref}#chunk{n}" for records > 512 tokens.
 *
 * Only role="user" chat_message docs are evidence-eligible (§1.3). assistant turns may
 * appear in the corpus for context but are never promoted to EvidenceItem.
 *
 * crisis_flag is propagated from the source record's emotionLevel==5 sentinel and is
 * used by S8 to hard-filter evidence (§1.6 S8). The field is set at assembly time and
 * never modified afterward.
 *
 * embedding is populated lazily by CorpusAssemblyService.embed(); it is nullable until
 * that call succeeds. If the embedding service is unavailable the field stays null and
 * downstream stages that require it (S2 embedding recall, S3 vector retrieval) skip
 * gracefully.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorpusDoc {

    /**
     * Stable identifier: "{sourceType.name()}:{sourceRef}".
     * For chunked records the caller appends "#chunk{n}" before persisting.
     */
    private String docId;

    /** Owner of this record — all queries must filter by this value (§2.2 isolation). */
    private String userId;

    /** Provenance type. Determines evidence eligibility and deep-link icon (§9). */
    private SourceType sourceType;

    /**
     * Primary key of the originating row as a string.
     * chat_message → ChatMessage.id
     * checkin      → CheckIn.id
     * journal_entry / wiki_fact → respective entity ids (future sources)
     */
    private String sourceRef;

    /** Wall-clock timestamp of the original record; used for recency decay and window filtering. */
    private LocalDateTime occurredAt;

    /**
     * Normalized text content (≤ 512 tokens per chunk; longer records are chunked externally).
     * PII-stable — text is sourced directly from the DB and not transformed here.
     */
    private String text;

    /**
     * "user" or "assistant" for chat_message; null for all other source types.
     * Only role="user" docs are evidence-eligible (§1.3 / §10.2 assistant-turn leakage guard).
     */
    private String role;

    /**
     * True when the originating record carries an emotionLevel of 5 (crisis tier).
     * Crisis docs are indexed for retrieval coherence but hard-filtered at S4/S8 and
     * must never appear as EvidenceItems (product §13.3 / §17 ship-blocker).
     */
    private boolean crisisFlag;

    /**
     * ada-002 embedding vector (1536-dim). Null until CorpusAssemblyService.embed() succeeds.
     * When null, stages that need this field skip or log-warn — they must never throw.
     */
    private float[] embedding;
}
