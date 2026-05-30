package com.ling.linginnerflow.pattern.dedup;

import com.ling.linginnerflow.pattern.entity.PatternInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic near-duplicate deduplication for PatternInstances (§6.2).
 *
 * <p>Two different {@code pattern_key}s can produce near-identical
 * {@code personalized_summary} text for one user (e.g. {@code self_criticism}
 * and {@code worth_through_achievement} both narrating the same experience).
 * Before surfacing a new instance this service checks it against all existing
 * active instances and returns any semantic duplicate found.
 *
 * <p>Reuses the exact embedding model ({@code text-embedding-ada-002} via
 * Spring AI) and the DEDUP_THRESHOLD=0.88 constant already tuned in
 * {@code memory.MemoryService} for trigger deduplication (P3-11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatternDeduplicator {

    /**
     * Cosine-similarity threshold above which two summaries are considered
     * semantic near-duplicates. Reused verbatim from MemoryService P3-11.
     */
    static final double DEDUP_THRESHOLD = 0.88;

    private final EmbeddingModel embeddingModel;

    /**
     * Finds a semantic duplicate of {@code newSummary} among the given active
     * instances, considering only instances with a <em>different</em>
     * {@code patternKey} (same-key dedup is handled structurally by the
     * {@code (user_id, pattern_key, domain)} uniqueness invariant).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Batch-embed {@code newSummary} plus every
     *       {@code existing.personalizedSummary} in a single API call.</li>
     *   <li>Compute cosine similarity between the new vector and each existing
     *       vector whose {@code patternKey} differs from the new one being
     *       evaluated (caller must pass the new instance's key via
     *       {@code newPatternKey} — or use the overload without that parameter
     *       to compare against all keys).</li>
     *   <li>Return the existing instance with the highest similarity if it is
     *       &ge; {@link #DEDUP_THRESHOLD}, otherwise {@code null}.</li>
     * </ol>
     *
     * <p>Graceful degradation: if the embedding service throws any exception the
     * method logs a warning at WARN level and returns {@code null} (treat as
     * new, no deduplication; no data is lost).
     *
     * @param newSummary               the {@code personalized_summary} of the
     *                                 candidate instance about to be surfaced
     * @param newPatternKey            the {@code pattern_key} of the candidate
     *                                 (used to exclude same-key comparisons)
     * @param existingActiveInstances  active PatternInstances for this user
     *                                 (may be empty but must not be {@code null})
     * @return the most-similar existing instance if its similarity
     *         &ge; 0.88, or {@code null} if no duplicate found
     */
    public PatternInstance findDuplicate(String newSummary,
                                         String newPatternKey,
                                         List<PatternInstance> existingActiveInstances) {
        if (existingActiveInstances == null || existingActiveInstances.isEmpty()) {
            return null;
        }
        if (newSummary == null || newSummary.isBlank()) {
            return null;
        }

        // Filter to only different-key instances that have a non-blank summary.
        List<PatternInstance> candidates = existingActiveInstances.stream()
                .filter(p -> !p.getPatternKey().equals(newPatternKey))
                .filter(p -> p.getPersonalizedSummary() != null
                        && !p.getPersonalizedSummary().isBlank())
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        try {
            // Build the text batch: position 0 = newSummary, positions 1..N = existing summaries.
            List<String> texts = new ArrayList<>(candidates.size() + 1);
            texts.add(newSummary);
            candidates.forEach(p -> texts.add(p.getPersonalizedSummary()));

            List<float[]> vectors = embeddingModel.embed(texts);
            float[] newVec = vectors.get(0);

            PatternInstance best = null;
            double bestSim = DEDUP_THRESHOLD; // use threshold as the floor; strictly greater wins

            for (int i = 0; i < candidates.size(); i++) {
                double sim = cosineSimilarity(newVec, vectors.get(i + 1));
                if (sim >= bestSim) {
                    bestSim = sim;
                    best = candidates.get(i);
                }
            }

            if (best != null) {
                log.info("[PatternDedup] Semantic duplicate found: newKey='{}' matches existingKey='{}' "
                                + "instanceId='{}' similarity={:.4f}",
                        newPatternKey, best.getPatternKey(), best.getId(), bestSim);
            }
            return best;

        } catch (Exception e) {
            log.warn("[PatternDedup] Embedding unavailable, skipping semantic dedup: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Convenience overload for callers that do not supply the new pattern key;
     * compares against ALL existing summaries regardless of key.
     *
     * @param newSummary              the candidate {@code personalized_summary}
     * @param existingActiveInstances active PatternInstances for this user
     * @return the most-similar existing instance at or above threshold, or {@code null}
     */
    public PatternInstance findDuplicate(String newSummary,
                                         List<PatternInstance> existingActiveInstances) {
        if (existingActiveInstances == null || existingActiveInstances.isEmpty()) {
            return null;
        }
        if (newSummary == null || newSummary.isBlank()) {
            return null;
        }

        List<PatternInstance> candidates = existingActiveInstances.stream()
                .filter(p -> p.getPersonalizedSummary() != null
                        && !p.getPersonalizedSummary().isBlank())
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        try {
            List<String> texts = new ArrayList<>(candidates.size() + 1);
            texts.add(newSummary);
            candidates.forEach(p -> texts.add(p.getPersonalizedSummary()));

            List<float[]> vectors = embeddingModel.embed(texts);
            float[] newVec = vectors.get(0);

            PatternInstance best = null;
            double bestSim = DEDUP_THRESHOLD;

            for (int i = 0; i < candidates.size(); i++) {
                double sim = cosineSimilarity(newVec, vectors.get(i + 1));
                if (sim >= bestSim) {
                    bestSim = sim;
                    best = candidates.get(i);
                }
            }

            if (best != null) {
                log.info("[PatternDedup] Semantic duplicate found: existingKey='{}' instanceId='{}' similarity={:.4f}",
                        best.getPatternKey(), best.getId(), bestSim);
            }
            return best;

        } catch (Exception e) {
            log.warn("[PatternDedup] Embedding unavailable, skipping semantic dedup: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Computes cosine similarity between two float vectors.
     *
     * <p>Identical implementation to {@code MemoryService#cosineSimilarity}
     * (copied rather than extracted to avoid a cross-package dependency on an
     * internal helper). The {@code +1e-10} guard prevents division by zero on
     * zero-norm vectors.
     *
     * @param a first embedding vector
     * @param b second embedding vector
     * @return cosine similarity in [-1, 1]
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }
}
