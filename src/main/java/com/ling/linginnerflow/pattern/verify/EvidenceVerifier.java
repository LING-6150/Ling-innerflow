package com.ling.linginnerflow.pattern.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.safety.LanguageFirewall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * S4 — Evidence Verification (§4.1 of PATTERN_ENGINE_V1.md).
 *
 * <p>Takes a list of candidate {@link CorpusDoc}s retrieved for a given pattern and
 * returns the subset that genuinely <em>supports</em> the pattern, each enriched
 * with a verbatim span and a neutral interpretation sentence.
 *
 * <h2>Mode selection (R16′ of PATTERN_ENGINE_V1.2 §1)</h2>
 * <p>Position-bias in the batch verifier is handled as a <strong>binary, whole-mode
 * switch</strong>. The mode is configured via {@code pattern.verify.mode} (default
 * {@code BATCH}):
 * <ul>
 *   <li>{@code BATCH} — one structured LLM call judging all docs together. Lower
 *       cost; use when the batch flip-rate on the entailment ground-truth set is ≤
 *       the published tolerance bound.</li>
 *   <li>{@code SINGLE_ITEM} — one independent LLM call per doc. Position-bias
 *       cannot exist by construction; use when the flip-rate exceeds the bound.</li>
 * </ul>
 * <strong>Per-item shuffle-and-drop is FORBIDDEN (R16′-c).</strong> There is no
 * intermediate state. The mode is a whole-system switch only.
 *
 * <h2>Hard code-side invariants (not delegated to the LLM)</h2>
 * <ol>
 *   <li>Any LLM-returned {@code verbatimSpan} that is not an exact substring of
 *       {@code CorpusDoc.text} is rejected: {@code isVerbatimQuotable} is forced to
 *       {@code false} and the span is nulled.</li>
 *   <li>Every {@code interpretation} is run through
 *       {@link LanguageFirewall#isClean(String)}; results with dirty interpretations
 *       are dropped entirely.</li>
 *   <li>Results with {@code supports = false} are dropped before returning.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceVerifier {

    // ── Mode ──────────────────────────────────────────────────────────────────

    /**
     * Binary verifier mode. Configurable via {@code pattern.verify.mode}.
     * <ul>
     *   <li>{@code BATCH} — single LLM call for all docs.</li>
     *   <li>{@code SINGLE_ITEM} — independent LLM call per doc (no position bias).</li>
     * </ul>
     * No per-item shuffle-and-drop exists; the intermediate state is forbidden by R16′-c.
     */
    enum Mode {
        BATCH,
        SINGLE_ITEM
    }

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final LanguageFirewall languageFirewall;

    @Value("${pattern.verify.mode:BATCH}")
    private String modeStr;

    // ── Prompt templates ──────────────────────────────────────────────────────

    /**
     * Batch prompt: judge all docs in one call.
     * Returns a JSON array; one object per doc.
     */
    private static final String BATCH_PROMPT_TEMPLATE = """
            You are verifying whether each user document supports the psychological pattern
            described below. Respond ONLY with a valid JSON array — no markdown, no prose.

            PATTERN KEY: %s
            PATTERN DESCRIPTION: %s

            For each document below, produce one JSON object:
            {
              "docId": "<doc_id>",
              "supports": true|false,
              "isVerbatimQuotable": true|false,
              "verbatimSpan": "<exact substring ≤280 chars or null>",
              "interpretation": "<one neutral sentence connecting this excerpt to the pattern or null>",
              "inferredDomain": "<one of: self, family, intimate, work, social, body>"
            }

            Rules:
            - supports=true only when the text clearly evidences the pattern.
            - verbatimSpan MUST be an exact character-for-character substring of the document text.
            - If no short verbatim span is available, set isVerbatimQuotable=false and verbatimSpan=null.
            - interpretation must be one neutral, non-clinical sentence (no DSM/ICD/diagnosis language).
            - inferredDomain must be exactly one value from the enum above.

            DOCUMENTS (JSON array):
            %s
            """;

    /**
     * Single-item prompt: judge one doc in isolation.
     */
    private static final String SINGLE_ITEM_PROMPT_TEMPLATE = """
            You are verifying whether the user document below supports the psychological pattern
            described. Respond ONLY with a single valid JSON object — no markdown, no prose.

            PATTERN KEY: %s
            PATTERN DESCRIPTION: %s

            {
              "docId": "<doc_id>",
              "supports": true|false,
              "isVerbatimQuotable": true|false,
              "verbatimSpan": "<exact substring ≤280 chars or null>",
              "interpretation": "<one neutral sentence connecting this excerpt to the pattern or null>",
              "inferredDomain": "<one of: self, family, intimate, work, social, body>"
            }

            DOCUMENT:
            docId: %s
            text: %s
            """;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Verify a list of candidate docs against a pattern.
     *
     * <p>Returns only the surviving {@link VerificationResult}s — i.e. docs
     * where {@code supports=true}, interpretation passed the firewall, and
     * (when claimed verbatim) the span is an actual substring of the source text.
     *
     * @param patternKey  the stable pattern identifier
     * @param def         the full {@link PatternDefinition} (provides the description)
     * @param docs        candidate docs from S3 retrieval
     * @return surviving, firewall-clean, supports=true results
     */
    public List<VerificationResult> verify(
            String patternKey,
            PatternDefinition def,
            List<CorpusDoc> docs) {

        if (docs == null || docs.isEmpty()) {
            log.debug("verify [{}]: empty doc list, returning empty", patternKey);
            return List.of();
        }

        Mode mode;
        try {
            mode = Mode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("verify [{}]: unknown mode '{}', defaulting to BATCH", patternKey, modeStr);
            mode = Mode.BATCH;
        }

        log.debug("verify [{}]: mode={}, docCount={}", patternKey, mode, docs.size());

        List<VerificationResult> rawResults = switch (mode) {
            case BATCH -> verifyBatch(patternKey, def, docs);
            case SINGLE_ITEM -> verifySingleItem(patternKey, def, docs);
        };

        // Build a fast lookup of docId → source text for the verbatim check
        Map<String, String> textByDocId = new java.util.HashMap<>();
        for (CorpusDoc doc : docs) {
            textByDocId.put(doc.getDocId(), doc.getText());
        }

        return applyHardInvariants(patternKey, rawResults, textByDocId);
    }

    // ── Batch mode ────────────────────────────────────────────────────────────

    private List<VerificationResult> verifyBatch(
            String patternKey,
            PatternDefinition def,
            List<CorpusDoc> docs) {

        // Serialize the docs as a compact JSON array for the prompt
        String docsJson;
        try {
            List<Map<String, String>> compact = new ArrayList<>();
            for (CorpusDoc doc : docs) {
                compact.add(Map.of("docId", doc.getDocId(), "text", doc.getText()));
            }
            docsJson = objectMapper.writeValueAsString(compact);
        } catch (Exception e) {
            log.error("verify [{}] BATCH: failed to serialize docs — {}", patternKey, e.getMessage());
            return List.of();
        }

        String prompt = String.format(BATCH_PROMPT_TEMPLATE,
                patternKey,
                def.getNeutralDescription(),
                docsJson);

        String raw;
        try {
            raw = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("verify [{}] BATCH: LLM call failed — {}", patternKey, e.getMessage());
            return List.of();
        }

        return parseBatchResponse(patternKey, raw);
    }

    private List<VerificationResult> parseBatchResponse(String patternKey, String raw) {
        try {
            List<LlmVerificationItem> items = objectMapper.readValue(
                    raw, new TypeReference<List<LlmVerificationItem>>() {});
            List<VerificationResult> results = new ArrayList<>();
            for (LlmVerificationItem item : items) {
                VerificationResult r = toVerificationResult(item);
                if (r != null) {
                    results.add(r);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("verify [{}] BATCH PARSE_FAIL: could not parse LLM JSON — {}. Raw={}",
                    patternKey, e.getMessage(), truncate(raw, 400));
            return List.of();
        }
    }

    // ── Single-item mode ──────────────────────────────────────────────────────

    private List<VerificationResult> verifySingleItem(
            String patternKey,
            PatternDefinition def,
            List<CorpusDoc> docs) {

        List<VerificationResult> results = new ArrayList<>();
        for (CorpusDoc doc : docs) {
            String prompt = String.format(SINGLE_ITEM_PROMPT_TEMPLATE,
                    patternKey,
                    def.getNeutralDescription(),
                    doc.getDocId(),
                    doc.getText());

            String raw;
            try {
                raw = chatClientBuilder.build()
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();
            } catch (Exception e) {
                log.error("verify [{}] SINGLE_ITEM: LLM call failed for docId={} — {}",
                        patternKey, doc.getDocId(), e.getMessage());
                continue;
            }

            try {
                LlmVerificationItem item = objectMapper.readValue(raw, LlmVerificationItem.class);
                VerificationResult r = toVerificationResult(item);
                if (r != null) {
                    results.add(r);
                }
            } catch (Exception e) {
                log.error("verify [{}] SINGLE_ITEM PARSE_FAIL docId={}: {} — raw={}",
                        patternKey, doc.getDocId(), e.getMessage(), truncate(raw, 400));
                // drop this candidate; do not fabricate
            }
        }
        return results;
    }

    // ── Hard invariants (§4.1, R16′) — applied identically for both modes ────

    /**
     * Apply code-side checks that are never delegated to the LLM.
     *
     * <ol>
     *   <li>Verbatim span substring assertion — forces isVerbatimQuotable=false
     *       and nulls the span if the text does not literally contain it.</li>
     *   <li>Language firewall on interpretation — drops the whole result if dirty.</li>
     *   <li>Drop supports=false results.</li>
     * </ol>
     *
     * <strong>There is no shuffle-and-drop logic here (forbidden by R16′-c).</strong>
     */
    private List<VerificationResult> applyHardInvariants(
            String patternKey,
            List<VerificationResult> rawResults,
            Map<String, String> textByDocId) {

        List<VerificationResult> survivors = new ArrayList<>();

        for (VerificationResult r : rawResults) {

            // 1. Drop supports=false immediately
            if (!r.isSupports()) {
                log.trace("verify [{}] DROP supports=false docId={}", patternKey, r.getDocId());
                continue;
            }

            // 2. Verbatim span assertion (non-hallucinatable quotes — §4.1)
            if (r.getVerbatimSpan() != null) {
                String sourceText = textByDocId.get(r.getDocId());
                if (sourceText == null || !sourceText.contains(r.getVerbatimSpan())) {
                    log.warn("verify [{}] VERBATIM_MISMATCH docId={}: span not a substring of source — "
                            + "forcing isVerbatimQuotable=false", patternKey, r.getDocId());
                    r.setVerbatimQuotable(false);
                    r.setVerbatimSpan(null);
                }
            }

            // 3. Language firewall on interpretation
            String interp = r.getInterpretation();
            if (interp == null || interp.isBlank()) {
                log.warn("verify [{}] DROP blank interpretation docId={}", patternKey, r.getDocId());
                continue;
            }
            if (!languageFirewall.isClean(interp)) {
                log.warn("verify [{}] DROP firewall_fail interpretation docId={}", patternKey, r.getDocId());
                continue;
            }

            survivors.add(r);
        }

        log.debug("verify [{}]: {} raw → {} survivors after hard invariants",
                patternKey, rawResults.size(), survivors.size());
        return survivors;
    }

    // ── Internal LLM response DTO ─────────────────────────────────────────────

    /**
     * Matches the JSON object the LLM is asked to produce for each doc.
     * Jackson-deserialized; unknown fields are ignored.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlmVerificationItem {
        public String docId;
        public boolean supports;
        public boolean isVerbatimQuotable;
        public String verbatimSpan;
        public String interpretation;
        public String inferredDomain;
    }

    /** Convert an LLM DTO to a {@link VerificationResult}, returning null on bad data. */
    private VerificationResult toVerificationResult(LlmVerificationItem item) {
        if (item == null || item.docId == null) {
            return null;
        }

        Domain domain = null;
        if (item.inferredDomain != null) {
            try {
                domain = Domain.valueOf(item.inferredDomain.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warn("verify: unrecognized inferredDomain '{}' for docId={}, will be null",
                        item.inferredDomain, item.docId);
            }
        }

        VerificationResult r = new VerificationResult();
        r.setDocId(item.docId);
        r.setSupports(item.supports);
        r.setVerbatimQuotable(item.isVerbatimQuotable);
        r.setVerbatimSpan(item.verbatimSpan);
        r.setInterpretation(item.interpretation);
        r.setInferredDomain(domain);
        return r;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String truncate(String s, int maxLen) {
        if (s == null) return "<null>";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}
