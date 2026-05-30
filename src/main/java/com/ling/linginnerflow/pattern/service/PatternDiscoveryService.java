package com.ling.linginnerflow.pattern.service;

import com.ling.linginnerflow.pattern.corpus.CorpusAssemblyService;
import com.ling.linginnerflow.pattern.corpus.CorpusDoc;
import com.ling.linginnerflow.pattern.dedup.PatternDeduplicator;
import com.ling.linginnerflow.pattern.definition.PatternDefinition;
import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.EvidenceChain;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceChainRepository;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.retrieval.EvidenceRetrievalService;
import com.ling.linginnerflow.pattern.retrieval.PatternRecallService;
import com.ling.linginnerflow.pattern.safety.LanguageFirewall;
import com.ling.linginnerflow.pattern.scoring.ConfidenceScorer;
import com.ling.linginnerflow.pattern.verify.EvidenceChainAssembler;
import com.ling.linginnerflow.pattern.verify.EvidenceVerifier;
import com.ling.linginnerflow.pattern.verify.VerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatternDiscoveryService {

    private static final int REJECT_COOLDOWN_DAYS = 90;

    private final CorpusAssemblyService corpusAssemblyService;
    private final PatternRecallService patternRecallService;
    private final EvidenceRetrievalService evidenceRetrievalService;
    private final EvidenceVerifier evidenceVerifier;
    private final EvidenceChainAssembler evidenceChainAssembler;
    private final ConfidenceScorer confidenceScorer;
    private final PatternDeduplicator patternDeduplicator;
    private final LanguageFirewall languageFirewall;
    private final PatternDefinitionLoader definitionLoader;
    private final PatternInstanceRepository patternInstanceRepository;
    private final EvidenceChainRepository evidenceChainRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final ChatClient.Builder chatClientBuilder;

    @Transactional
    public RefreshResult refresh(String userId) {
        List<CorpusDoc> corpus = corpusAssemblyService.assemble(userId);
        corpusAssemblyService.embed(corpus);

        if (!corpusAssemblyService.meetsGate(corpus)) {
            log.info("[PatternDiscovery] userId={} skipped: corpus gate not met, docs={}",
                    userId, corpus.size());
            return new RefreshResult(userId, corpus.size(), 0, 0);
        }

        Set<String> candidates = patternRecallService.recall(corpus);
        int persisted = 0;

        for (String patternKey : candidates) {
            try {
                if (processCandidate(userId, patternKey, corpus)) {
                    persisted++;
                }
            } catch (Exception e) {
                log.warn("[PatternDiscovery] candidate failed userId={}, patternKey={}, error={}",
                        userId, patternKey, e.getMessage());
            }
        }

        return new RefreshResult(userId, corpus.size(), candidates.size(), persisted);
    }

    private boolean processCandidate(String userId, String patternKey, List<CorpusDoc> corpus) {
        PatternDefinition definition = definitionLoader.get(patternKey);
        Domain primaryDomain = Domain.valueOf(definition.getPrimaryDomain());
        List<CorpusDoc> retrieved = evidenceRetrievalService.retrieve(patternKey, corpus);
        List<VerificationResult> verified = evidenceVerifier.verify(patternKey, definition, retrieved);

        PatternInstance provisional = new PatternInstance();
        provisional.setUserId(userId);
        provisional.setPatternKey(patternKey);
        provisional.setDomain(primaryDomain);
        provisional.setStatus(PatternStatus.candidate);
        provisional.prePersist();

        Optional<EvidenceChainAssembler.AssembledChain> assembled =
                evidenceChainAssembler.assemble(patternKey, provisional.getId(),
                        verified, retrieved, primaryDomain);
        if (assembled.isEmpty()) {
            return false;
        }

        List<EvidenceItem> items = assembled.get().items();
        double confidence = confidenceScorer.score(items);
        boolean surface = confidenceScorer.shouldSurface(confidence);
        Domain domain = assembled.get().domain();

        if (isInRejectedCooldown(userId, patternKey, domain, items)) {
            log.info("[PatternDiscovery] suppressed by rejected cooldown: userId={}, key={}, domain={}",
                    userId, patternKey, domain);
            return false;
        }

        Optional<PatternInstance> existing = patternInstanceRepository
                .findByUserIdAndPatternKeyAndDomainAndStatusNot(
                        userId, patternKey, domain, PatternStatus.rejected);

        PatternInstance instance = existing.orElse(provisional);
        if (existing.isEmpty()) {
            instance.setDomain(domain);
            instance.setStatus(PatternStatus.candidate);
        }

        String summary = generateCleanSummary(definition, domain, items);
        if (summary == null) {
            return false;
        }

        List<PatternInstance> activeInstances = patternInstanceRepository.findByUserId(userId).stream()
                .filter(item -> item.getStatus() != PatternStatus.rejected)
                .filter(item -> item.getStatus() != PatternStatus.archived)
                .toList();
        PatternInstance duplicate = patternDeduplicator.findDuplicate(summary, patternKey, activeInstances);
        if (duplicate != null) {
            log.info("[PatternDiscovery] semantic duplicate suppressed userId={}, key={}, duplicateId={}",
                    userId, patternKey, duplicate.getId());
            return false;
        }

        instance.setPatternKey(patternKey);
        instance.setUserId(userId);
        instance.setConfidence(confidence);
        instance.setPersonalizedSummary(summary);
        instance.setHidden(!surface);
        instance.setLastObservedAt(latestObservedAt(items));
        instance.setRefreshCount(instance.getRefreshCount() + 1);

        PatternInstance saved = patternInstanceRepository.save(instance);
        EvidenceChain chain = assembled.get().chain();
        chain.setPatternInstanceId(saved.getId());
        EvidenceChain savedChain = evidenceChainRepository.save(chain);
        for (EvidenceItem item : items) {
            item.setEvidenceChainId(savedChain.getId());
        }
        evidenceItemRepository.saveAll(items);
        saved.setEvidenceChainId(savedChain.getId());
        patternInstanceRepository.save(saved);

        log.info("[PatternDiscovery] persisted userId={}, key={}, domain={}, confidence={}, hidden={}",
                userId, patternKey, domain, confidence, !surface);
        return true;
    }

    private String generateCleanSummary(PatternDefinition definition, Domain domain, List<EvidenceItem> items) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String generated = generateSummary(definition, domain, items, attempt);
            if (generated != null && languageFirewall.isClean(generated)) {
                return generated;
            }
        }
        log.info("[PatternDiscovery] summary discarded after firewall failures: {}", definition.getPatternKey());
        return null;
    }

    private String generateSummary(PatternDefinition definition, Domain domain,
                                   List<EvidenceItem> items, int attempt) {
        String evidence = items.stream()
                .map(item -> "- " + item.getExcerpt() + " / " + item.getInterpretation())
                .collect(Collectors.joining("\n"));
        String prompt = """
                Write one non-clinical, user-facing pattern summary in Chinese.
                Use only the evidence below. Do not diagnose. Do not say "你有" or "你是".
                Follow this framing: %s

                Pattern display: %s
                Domain: %s
                Evidence:
                %s

                Return only the summary text.
                """.formatted(LanguageFirewall.SAFE_PHRASING_TEMPLATE,
                definition.getDisplayNameZh(), domain.name(), evidence);
        try {
            return chatClientBuilder.build().prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.warn("[PatternDiscovery] summary LLM failed attempt={}, key={}, error={}",
                    attempt, definition.getPatternKey(), e.getMessage());
            return fallbackSummary(definition, domain, items);
        }
    }

    private String fallbackSummary(PatternDefinition definition, Domain domain, List<EvidenceItem> items) {
        String excerpt = items.isEmpty() ? "一些反复出现的表达" : items.get(0).getExcerpt();
        return "我观察到在 " + domain.name() + " 场景里反复出现和「"
                + excerpt + "」相近的线索。这个可能属于 "
                + definition.getDisplayNameZh() + " 类的 pattern，你怎么看？";
    }

    private boolean isInRejectedCooldown(String userId, String patternKey,
                                         Domain domain, List<EvidenceItem> newItems) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(REJECT_COOLDOWN_DAYS);
        Set<String> newRefs = sourceRefs(newItems);
        return patternInstanceRepository.findByUserIdAndPatternKeyAndDomain(userId, patternKey, domain)
                .stream()
                .filter(instance -> instance.getStatus() == PatternStatus.rejected)
                .filter(instance -> instance.getLastReviewedAt() != null
                        && instance.getLastReviewedAt().isAfter(cutoff))
                .anyMatch(instance -> !isSubstantiallyDifferent(instance, newRefs));
    }

    private boolean isSubstantiallyDifferent(PatternInstance rejected, Set<String> newRefs) {
        if (rejected.getEvidenceChainId() == null) {
            return false;
        }
        List<EvidenceItem> oldItems = evidenceItemRepository.findByEvidenceChainId(rejected.getEvidenceChainId());
        Set<String> oldRefs = sourceRefs(oldItems);
        long newerItems = newRefs.stream()
                .filter(ref -> !oldRefs.contains(ref))
                .count();
        return jaccard(oldRefs, newRefs) < 0.5 && newerItems >= 2;
    }

    private Set<String> sourceRefs(List<EvidenceItem> items) {
        return items.stream()
                .map(EvidenceItem::getSourceRef)
                .collect(Collectors.toCollection(HashSet::new));
    }

    double jaccard(Set<String> left, Set<String> right) {
        if ((left == null || left.isEmpty()) && (right == null || right.isEmpty())) {
            return 1.0;
        }
        Set<String> union = new HashSet<>();
        if (left != null) union.addAll(left);
        if (right != null) union.addAll(right);
        Set<String> intersection = new HashSet<>();
        if (left != null) intersection.addAll(left);
        if (right != null) intersection.retainAll(right);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private LocalDateTime latestObservedAt(List<EvidenceItem> items) {
        return items.stream()
                .map(EvidenceItem::getOccurredAt)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
    }

    public record RefreshResult(String userId, int corpusDocs, int candidates, int persisted) {}
}
