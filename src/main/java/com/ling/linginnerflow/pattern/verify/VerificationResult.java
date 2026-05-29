package com.ling.linginnerflow.pattern.verify;

import com.ling.linginnerflow.pattern.domain.Domain;
import lombok.Data;

/**
 * Outcome of verifying one {@link com.ling.linginnerflow.pattern.corpus.CorpusDoc}
 * against a single pattern by {@link EvidenceVerifier}.
 *
 * <p>Code-side invariants enforced by {@code EvidenceVerifier} before a result is
 * returned:
 * <ul>
 *   <li>{@code verbatimSpan}, if present, is an exact substring of the original
 *       {@code CorpusDoc.text}. If the LLM-returned span fails the contains-check,
 *       {@code isVerbatimQuotable} is forced to {@code false} and
 *       {@code verbatimSpan} is nulled out.</li>
 *   <li>The {@code interpretation} has passed the language firewall. Results
 *       with dirty interpretations are dropped before being returned.</li>
 *   <li>Results with {@code supports = false} are dropped before being returned.</li>
 * </ul>
 */
@Data
public class VerificationResult {

    /** The {@code CorpusDoc.docId} this result pertains to. */
    private String docId;

    /** True when the doc text entails / supports the target pattern. */
    private boolean supports;

    /**
     * True when a short verbatim span can be lifted directly from the source.
     * Only meaningful when {@code supports = true}.
     * Always {@code false} when {@code verbatimSpan == null}.
     */
    private boolean isVerbatimQuotable;

    /**
     * The exact quoted span (≤ 280 chars) extracted from the source text, or
     * {@code null} when no verbatim span is available / the LLM-suggested span
     * failed the substring assertion.
     */
    private String verbatimSpan;

    /**
     * A single neutral sentence linking this excerpt to the pattern.
     * Written by the verifier LLM and pre-validated by the language firewall.
     * Never null for a surviving result.
     */
    private String interpretation;

    /**
     * The domain this evidence item most closely belongs to, as inferred by
     * the verifier from the doc content. Used for majority-vote domain
     * assignment during chain assembly (§4.3).
     */
    private Domain inferredDomain;
}
