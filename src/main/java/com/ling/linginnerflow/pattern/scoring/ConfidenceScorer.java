package com.ling.linginnerflow.pattern.scoring;

import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Confidence scorer for a {@link com.ling.linginnerflow.pattern.entity.PatternInstance}
 * — V1.2, R13 compliant.
 *
 * <p>The score is a weighted sum of three observable, count/time factors:</p>
 * <pre>
 *   Evidence    = min(1.0, items.size() / 5.0)
 *   Recurrence  = min(1.0, distinctOccurredAtDays(items) / 4.0)
 *   Recency     = exp(−daysSinceLatest / 90.0)
 *
 *   confidence  = wEvidence·Evidence + wRecurrence·Recurrence + wRecency·Recency
 * </pre>
 *
 * <p><strong>V1.2 R13 invariant:</strong> EVERY term in {@link #score} is a count
 * function or a time function derived from the EvidenceChain.  No LLM-subjective
 * scalar (the V1.0 {@code strength} field) is used or referenced here.  The
 * {@code strength} term has been removed from the formula entirely (V1.2 R13).</p>
 *
 * <p><strong>Calibration note (V1.2 R37):</strong> the default weights
 * ({@code wEvidence=0.50}, {@code wRecurrence=0.30}, {@code wRecency=0.20}) and the
 * surface threshold ({@code surfaceThreshold=0.6}) are calibration outputs fit on
 * the Tier A synthetic evaluation set.  They are exposed as {@code @Value}-injected
 * config properties so the eval harness can sweep them without recompilation.</p>
 *
 * <p>Rounding follows the same convention as
 * {@code memory.MemoryService#computeScore}:
 * {@code Math.round(x * 100.0) / 100.0}.</p>
 */
@Service
public class ConfidenceScorer {

    /** Fraction weight for the Evidence term (default 0.50). */
    @Value("${pattern.confidence.w-evidence:0.50}")
    private double wEvidence;

    /** Fraction weight for the Recurrence term (default 0.30). */
    @Value("${pattern.confidence.w-recurrence:0.30}")
    private double wRecurrence;

    /** Fraction weight for the Recency term (default 0.20). */
    @Value("${pattern.confidence.w-recency:0.20}")
    private double wRecency;

    /**
     * Minimum confidence required to surface a candidate to the user
     * (product §10, §5.2).  Default 0.6; swept during calibration (V1.2 R37).
     */
    @Value("${pattern.confidence.surface-threshold:0.6}")
    private double surfaceThreshold;

    /**
     * Computes the confidence score for an EvidenceChain given its items.
     *
     * <p><strong>Formula (all terms are count or time functions — V1.2 R13):</strong>
     * <pre>
     *   Evidence    = min(1.0, items.size() / 5.0)
     *   Recurrence  = min(1.0, distinctOccurredAtDays(items) / 4.0)
     *   Recency     = exp(−daysSinceLatest / 90.0)
     *                 where daysSinceLatest = days between max(occurredAt) and today, >= 0
     *
     *   confidence  = wEvidence * Evidence
     *               + wRecurrence * Recurrence
     *               + wRecency * Recency
     * </pre>
     *
     * <p>IMPORTANT: there is no {@code Strength} term.  The LLM-emitted
     * {@code strength} scalar (V1.0 §4.1) is excluded by design (V1.2 R13 fix).
     * The LLM does not contribute to this score.  The weights and threshold are
     * calibration outputs fit on Tier A (V1.2 R37).</p>
     *
     * @param items the verified EvidenceItems for a single EvidenceChain;
     *              must not be null.  All items must have a non-null
     *              {@code occurredAt}.
     * @return confidence ∈ [0.0, 1.0] rounded to 2 decimal places,
     *         or {@code 0.0} if {@code items} is empty.
     */
    public double score(List<EvidenceItem> items) {
        if (items == null || items.isEmpty()) {
            return 0.0;
        }

        // Evidence: saturates at 5 verified items (mirrors MemoryService "/5 → full" shape).
        double evidenceTerm = Math.min(1.0, items.size() / 5.0);

        // Recurrence: count of distinct calendar days covered by occurredAt.
        // Saturates at 4 distinct days — a pattern needs temporal spread, not
        // one bad evening (see also product §9 chain-assembly invariants).
        long distinctDays = items.stream()
                .map(item -> item.getOccurredAt().toLocalDate())
                .collect(Collectors.toSet())
                .size();
        double recurrenceTerm = Math.min(1.0, distinctDays / 4.0);

        // Recency: 90-day exponential decay from the most-recent item.
        // Reuses the same half-life constant as MemoryService#computeScore.
        LocalDate latestDay = items.stream()
                .map(item -> item.getOccurredAt().toLocalDate())
                .max(LocalDate::compareTo)
                .orElseThrow(); // safe: items is non-empty
        long daysSinceLatest = Math.max(0L, ChronoUnit.DAYS.between(latestDay, LocalDate.now()));
        double recencyTerm = Math.exp(-daysSinceLatest / 90.0);

        double raw = wEvidence * evidenceTerm
                + wRecurrence * recurrenceTerm
                + wRecency * recencyTerm;

        // Round to 2 decimal places — same convention as MemoryService#computeScore.
        return Math.round(raw * 100.0) / 100.0;
    }

    /**
     * Returns {@code true} when {@code confidence} meets the surfacing threshold,
     * meaning the PatternInstance should be shown to the user as a candidate
     * (product §10, §5.2).
     *
     * @param confidence a value previously returned by {@link #score}.
     * @return {@code true} iff {@code confidence >= surfaceThreshold}.
     */
    public boolean shouldSurface(double confidence) {
        return confidence >= surfaceThreshold;
    }
}
