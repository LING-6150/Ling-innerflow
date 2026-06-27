package com.ling.linginnerflow.pattern.validation;

import com.ling.linginnerflow.pattern.eval.GTPersona;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Offline semantic candidate-recall audit (V2.2, eval-only diagnostic).
 *
 * <p>Purpose: test the hypothesis that swapping the lexical substring candidate
 * gate ({@code PatternRecallService.recall()}) for SEMANTIC recall (corpus
 * chunks vs pattern exemplar vectors) raises Tier A generated recall — while
 * measuring how much it inflates full-decoy false positives. First, eval-only PR.
 *
 * <p><b>Embed once, sweep many.</b> Embeddings are computed exactly once per
 * exemplar and per corpus chunk and cached as max-cosine scores per
 * {@code (persona, pattern)}; the topK/τ sweep then re-thresholds those cached
 * scores with NO further embedding calls. (An earlier version re-embedded inside
 * every sweep cell — ~6k API calls — which is exactly the cost the audit exists
 * to surface.)
 *
 * <p><b>Boundaries:</b> does NOT modify production {@code PatternRecallService};
 * no live LLM in deterministic mode (embeddings come via {@link EmbeddingFn});
 * key-level recall only (domain assignment is downstream); NOT held-out proof —
 * τ/topK are calibrated on Tier A, decoys are FP confirmation only.
 */
final class SemanticCandidateRecallAudit {

    /** Embedding seam: deterministic fake in tests, real model in the runner. */
    interface EmbeddingFn {
        float[] embed(String text);
    }

    /** Cosine similarity; returns 0.0 when either vector is zero/empty. */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Embed every exemplar once. Returns pattern key → list of exemplar vectors. */
    Map<String, List<float[]>> embedExemplars(Map<String, List<String>> patternExemplars, EmbeddingFn embed) {
        Map<String, List<float[]>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : patternExemplars.entrySet()) {
            out.put(e.getKey(), e.getValue().stream().map(embed::embed).toList());
        }
        return out;
    }

    /**
     * Score every pattern for one persona: max cosine between any corpus chunk
     * and any exemplar of that pattern. Embeds the corpus chunks once.
     */
    Map<String, Double> scorePatterns(List<String> corpusChunks,
                                      Map<String, List<float[]>> exemplarVecs,
                                      EmbeddingFn embed) {
        List<float[]> chunkVecs = corpusChunks.stream().map(embed::embed).toList();
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Map.Entry<String, List<float[]>> e : exemplarVecs.entrySet()) {
            double best = 0.0;
            for (float[] ev : e.getValue()) {
                for (float[] cv : chunkVecs) {
                    best = Math.max(best, cosine(cv, ev));
                }
            }
            scores.put(e.getKey(), best);
        }
        return scores;
    }

    /** Pure thresholding over cached scores: keep keys ≥ tau, cap to topK by score. */
    Set<String> recallFromScores(Map<String, Double> scores, int topK, double tau) {
        return scores.entrySet().stream()
                .filter(en -> en.getValue() >= tau)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(0, topK))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Convenience recall used by unit tests: embeds then thresholds. The sweep
     * path does NOT call this (it caches scores via scorePatterns once).
     */
    Set<String> recall(List<String> corpusChunks,
                       Map<String, List<String>> patternExemplars,
                       EmbeddingFn embed,
                       int topK,
                       double tau) {
        Map<String, Double> scores = scorePatterns(corpusChunks, embedExemplars(patternExemplars, embed), embed);
        return recallFromScores(scores, topK, tau);
    }

    /** Key-level TP/FP for one persona slice given its true pattern keys. */
    SliceMetric scoreSlice(Set<String> recalledKeys, Set<String> trueKeys) {
        int tp = (int) recalledKeys.stream().filter(trueKeys::contains).count();
        int fp = recalledKeys.size() - tp;
        return new SliceMetric(tp, fp);
    }

    /**
     * Render the topK × τ sweep over Tier A (TP/FP/recall) and full decoys
     * (generated FP). Embeddings are computed once per persona/exemplar; the grid
     * only re-thresholds cached scores. Output mirrors {@code RESULTS_V2_*} and is
     * written to {@code eval/RESULTS_V2_SEMANTIC_RECALL_AUDIT.md}.
     */
    String sweepReport(List<GTPersona> tierA,
                       List<GTPersona> fullDecoys,
                       Map<String, List<String>> patternExemplars,
                       EmbeddingFn embed,
                       int[] topKs,
                       double[] taus,
                       String embeddingLabel) {
        // Embed once.
        Map<String, List<float[]>> exemplarVecs = embedExemplars(patternExemplars, embed);
        Map<String, Map<String, Double>> tierAScores = new LinkedHashMap<>();
        Map<String, Map<String, Double>> decoyScores = new LinkedHashMap<>();
        for (GTPersona p : tierA) {
            tierAScores.put(p.id(), scorePatterns(chunks(p), exemplarVecs, embed));
        }
        for (GTPersona p : fullDecoys) {
            decoyScores.put(p.id(), scorePatterns(chunks(p), exemplarVecs, embed));
        }

        int tierATrueKeys = tierA.stream().mapToInt(p -> trueKeys(p).size()).sum();
        Map<String, Set<String>> trueKeysByPersona = new LinkedHashMap<>();
        tierA.forEach(p -> trueKeysByPersona.put(p.id(), trueKeys(p)));

        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V2 Semantic Candidate Recall Audit\n\n")
                .append("Input: checked-in Tier A corpus + 12 pattern definitions (exemplars). ")
                .append("Embedding: `").append(embeddingLabel).append("`.\n\n")
                .append("⚠️ OFFLINE eval-only diagnostic. Does NOT modify `PatternRecallService`; ")
                .append("NOT held-out proof — τ/topK calibrated on Tier A, full decoys are ")
                .append("false-positive confirmation only. Recall is pattern_key level ")
                .append("(domain assignment is downstream). Embeddings computed once and cached; ")
                .append("the sweep only re-thresholds.\n\n")
                .append("## Sweep\n\n")
                .append("| topK | tau | Tier A gen TP | Tier A true keys | Tier A recall | Tier A gen FP | full-decoy gen FP |\n")
                .append("|---:|---:|---:|---:|---:|---:|---:|\n");

        for (int topK : topKs) {
            for (double tau : taus) {
                int tierATp = 0, tierAFp = 0, decoyFp = 0;
                for (Map.Entry<String, Map<String, Double>> e : tierAScores.entrySet()) {
                    Set<String> recalled = recallFromScores(e.getValue(), topK, tau);
                    SliceMetric m = scoreSlice(recalled, trueKeysByPersona.get(e.getKey()));
                    tierATp += m.generatedTruePositives();
                    tierAFp += m.generatedFalsePositives();
                }
                for (Map<String, Double> scores : decoyScores.values()) {
                    decoyFp += recallFromScores(scores, topK, tau).size();
                }
                double recall = tierATp / (double) Math.max(1, tierATrueKeys);
                md.append("| ").append(topK)
                        .append(" | ").append(fmt(tau))
                        .append(" | ").append(tierATp)
                        .append(" | ").append(tierATrueKeys)
                        .append(" | ").append(fmt(recall))
                        .append(" | ").append(tierAFp)
                        .append(" | ").append(decoyFp)
                        .append(" |\n");
            }
        }

        md.append("\n## Caveats\n\n")
                .append("- Recovery target stays full-decoy generated FP `<= 2` without Tier A recall collapse.\n")
                .append("- Deterministic fake embeddings only validate the harness; a real-embedding run is required for any recall claim.\n")
                .append("- Key-level recall only; does not change the `(pattern_key, domain)` headline metric.\n");
        return md.toString();
    }

    private List<String> chunks(GTPersona persona) {
        return persona.corpus().stream()
                .map(record -> record.text())
                .filter(t -> t != null && !t.isBlank())
                .toList();
    }

    private Set<String> trueKeys(GTPersona persona) {
        return persona.truePatterns().stream()
                .map(label -> label.patternKey())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String fmt(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    record SliceMetric(int generatedTruePositives, int generatedFalsePositives) {
    }
}
