package com.ling.linginnerflow.pattern.verify;

import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.entity.EvidenceChain;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * S5 — Chain assembly (§4.2 of PATTERN_ENGINE_V1.md).
 *
 * <p>Takes the verified results from {@link EvidenceVerifier} and, if all
 * invariants are satisfied, constructs an {@link AssembledChain} holding the
 * (unpersisted) {@link EvidenceChain} and its {@link EvidenceItem}s.
 *
 * <p>The orchestrator is responsible for persisting the chain inside a single
 * transaction (S9). Nothing here touches the database.
 *
 * <h2>Invariants (§4.2, product §9)</h2>
 * <p>All of the following must hold; if any fails the method returns
 * {@link Optional#empty()} and logs a structured reason code:
 * <ol>
 *   <li>≥ 3 supporting items after de-dup by {@code sourceRef}
 *       ({@code DROP_INSUFFICIENT_EVIDENCE})</li>
 *   <li>≥ 1 item with {@code isVerbatim=true}
 *       ({@code DROP_NO_VERBATIM})</li>
 *   <li>Evidence spans ≥ 2 distinct {@code occurredAt} calendar days
 *       ({@code DROP_SINGLE_DAY})</li>
 *   <li>0 crisis-flagged source docs ({@code DROP_CRISIS})</li>
 * </ol>
 *
 * <h2>Domain assignment (§4.3)</h2>
 * <p>Majority vote of the surviving results' {@code inferredDomain}; ties broken
 * toward the caller-supplied {@code primaryDomain}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceChainAssembler {

    /** Generator version written on every chain for eval slicing (§1.7). */
    static final String GENERATOR_VERSION = "gpt-4o-mini-v1.2";

    /** Maximum excerpt length (product §9). */
    private static final int MAX_EXCERPT_LEN = 280;

    // ── Result holder ─────────────────────────────────────────────────────────

    /**
     * Immutable holder for the assembled (but not yet persisted) chain objects.
     */
    public record AssembledChain(
            EvidenceChain chain,
            List<EvidenceItem> items,
            Domain domain
    ) {}

    // ── Reason codes ──────────────────────────────────────────────────────────

    enum DropReason {
        DROP_INSUFFICIENT_EVIDENCE,
        DROP_NO_VERBATIM,
        DROP_SINGLE_DAY,
        DROP_CRISIS
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attempt to assemble a valid {@link EvidenceChain} from verified results.
     *
     * @param patternKey       the pattern being assembled (for logging)
     * @param patternInstanceId FK to the owning PatternInstance (may be a
     *                         provisional id; the orchestrator owns this)
     * @param results          surviving results from {@link EvidenceVerifier#verify}
     * @param sourceDocs       the original CorpusDoc list (used for crisis-flag
     *                         cross-check and {@code sourceType}/{@code occurredAt}
     *                         lookup)
     * @param primaryDomain    the pattern's canonical default domain (tie-break)
     * @return an assembled chain, or {@link Optional#empty()} if any invariant fails
     */
    public Optional<AssembledChain> assemble(
            String patternKey,
            String patternInstanceId,
            List<VerificationResult> results,
            List<CorpusDoc> sourceDocs,
            Domain primaryDomain) {

        if (results == null || results.isEmpty()) {
            log.info("assemble [{}] {}: no results", patternKey, DropReason.DROP_INSUFFICIENT_EVIDENCE);
            return Optional.empty();
        }

        // Build a fast lookup of docId → CorpusDoc for cross-checks
        Map<String, CorpusDoc> docById = new HashMap<>();
        for (CorpusDoc doc : sourceDocs) {
            docById.put(doc.getDocId(), doc);
        }

        // ── De-dup by sourceRef (§6.3) ────────────────────────────────────────
        // Multiple HyDE probes can retrieve the same source record as different
        // chunks. Deduplicate by CorpusDoc.sourceRef (parent record id), keeping
        // the first occurrence in results order.
        Map<String, VerificationResult> bySourceRef = new LinkedHashMap<>();
        for (VerificationResult vr : results) {
            CorpusDoc doc = docById.get(vr.getDocId());
            if (doc == null) {
                // doc not found in sourceDocs — skip (defensive)
                log.warn("assemble [{}]: docId={} not found in sourceDocs, skipping",
                        patternKey, vr.getDocId());
                continue;
            }
            bySourceRef.putIfAbsent(doc.getSourceRef(), vr);
        }

        List<VerificationResult> deduped = new ArrayList<>(bySourceRef.values());

        // ── Invariant 1: ≥ 3 supporting items ────────────────────────────────
        if (deduped.size() < 3) {
            log.info("assemble [{}] {}: only {} unique source-refs after dedup (need ≥ 3)",
                    patternKey, DropReason.DROP_INSUFFICIENT_EVIDENCE, deduped.size());
            return Optional.empty();
        }

        // ── Invariant 2: ≥ 1 verbatim item ───────────────────────────────────
        boolean hasVerbatim = deduped.stream().anyMatch(VerificationResult::isVerbatimQuotable);
        if (!hasVerbatim) {
            log.info("assemble [{}] {}", patternKey, DropReason.DROP_NO_VERBATIM);
            return Optional.empty();
        }

        // ── Invariant 3: evidence spans ≥ 2 distinct days ────────────────────
        Set<LocalDate> distinctDays = new HashSet<>();
        for (VerificationResult vr : deduped) {
            CorpusDoc doc = docById.get(vr.getDocId());
            if (doc != null && doc.getOccurredAt() != null) {
                distinctDays.add(doc.getOccurredAt().toLocalDate());
            }
        }
        if (distinctDays.size() < 2) {
            log.info("assemble [{}] {}: only {} distinct day(s) (need ≥ 2)",
                    patternKey, DropReason.DROP_SINGLE_DAY, distinctDays.size());
            return Optional.empty();
        }

        // ── Invariant 4: 0 crisis-flagged source docs ─────────────────────────
        for (VerificationResult vr : deduped) {
            CorpusDoc doc = docById.get(vr.getDocId());
            if (doc != null && doc.isCrisisFlag()) {
                log.info("assemble [{}] {}: docId={} is crisis-flagged",
                        patternKey, DropReason.DROP_CRISIS, vr.getDocId());
                return Optional.empty();
            }
        }

        // ── Domain: majority vote with primaryDomain tie-break (§4.3) ─────────
        Domain resolvedDomain = resolveDomain(deduped, primaryDomain);

        // ── Build EvidenceChain ───────────────────────────────────────────────
        EvidenceChain chain = new EvidenceChain();
        chain.setPatternInstanceId(patternInstanceId);
        chain.setGeneratedAt(LocalDateTime.now());
        chain.setGeneratorVersion(GENERATOR_VERSION);
        // @PrePersist will assign the UUID id; we call it explicitly so the id is
        // available for EvidenceItem FKs before any JPA context is involved.
        chain.prePersist();

        // ── Build EvidenceItems ───────────────────────────────────────────────
        List<EvidenceItem> items = new ArrayList<>();
        for (VerificationResult vr : deduped) {
            CorpusDoc doc = docById.get(vr.getDocId());
            // doc must exist (crisis check would have caught null; defensive NPE guard)
            if (doc == null) continue;

            EvidenceItem item = new EvidenceItem();
            item.setEvidenceChainId(chain.getId());
            item.setSourceType(doc.getSourceType());
            item.setSourceRef(doc.getSourceRef());
            item.setOccurredAt(doc.getOccurredAt());
            item.setVerbatim(vr.isVerbatimQuotable());

            // Excerpt: verbatim span if available, else paraphrase from
            // interpretation / doc text, truncated to 280 chars.
            String excerpt;
            if (vr.isVerbatimQuotable() && vr.getVerbatimSpan() != null) {
                excerpt = vr.getVerbatimSpan();
            } else if (vr.getInterpretation() != null && !vr.getInterpretation().isBlank()) {
                excerpt = vr.getInterpretation();
            } else {
                excerpt = doc.getText();
            }
            item.setExcerpt(truncate(excerpt, MAX_EXCERPT_LEN));
            item.setInterpretation(vr.getInterpretation());

            // @PrePersist for the item's own UUID
            item.prePersist();
            items.add(item);
        }

        log.info("assemble [{}]: assembled chain id={} with {} items, domain={}",
                patternKey, chain.getId(), items.size(), resolvedDomain);

        return Optional.of(new AssembledChain(chain, items, resolvedDomain));
    }

    // ── Domain resolution ─────────────────────────────────────────────────────

    /**
     * Majority vote of surviving results' {@code inferredDomain}.
     * Ties broken toward {@code primaryDomain}; falls back to
     * {@code primaryDomain} if no result has a recognized domain.
     */
    private Domain resolveDomain(List<VerificationResult> results, Domain primaryDomain) {
        Map<Domain, Long> counts = results.stream()
                .filter(r -> r.getInferredDomain() != null)
                .collect(Collectors.groupingBy(VerificationResult::getInferredDomain,
                        Collectors.counting()));

        if (counts.isEmpty()) {
            return primaryDomain;
        }

        long maxCount = counts.values().stream().max(Long::compareTo).orElse(0L);

        // Collect all domains that share the maximum count (for tie-break)
        List<Domain> topDomains = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Prefer primaryDomain on tie
        if (topDomains.contains(primaryDomain)) {
            return primaryDomain;
        }

        // Otherwise return the first in natural enum order for determinism
        return topDomains.stream()
                .min(Comparator.comparingInt(Domain::ordinal))
                .orElse(primaryDomain);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
