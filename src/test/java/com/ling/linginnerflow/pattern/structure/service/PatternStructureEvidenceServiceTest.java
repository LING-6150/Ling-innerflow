package com.ling.linginnerflow.pattern.structure.service;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.domain.SourceType;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.structure.dto.EvidenceExcerptVisibility;
import com.ling.linginnerflow.pattern.structure.dto.EvidenceItemDto;
import com.ling.linginnerflow.pattern.structure.dto.PatternStructureEvidenceResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternStructureEvidenceServiceTest {

    private PatternInstance instance;
    private List<EvidenceItem> evidenceItems = List.of();
    private final List<String> loadedChainIds = new ArrayList<>();
    private final PatternInstanceRepository patternInstanceRepository = patternInstanceRepository();
    private final EvidenceItemRepository evidenceItemRepository = evidenceItemRepository();
    private final PatternStructureEligibilityService eligibilityService = new PatternStructureEligibilityService(
            patternInstanceRepository,
            evidenceItemRepository
    );
    private final PatternStructureEvidenceService service = new PatternStructureEvidenceService(
            patternInstanceRepository,
            evidenceItemRepository,
            eligibilityService
    );

    @Test
    void returns_all_chain_evidence_newest_first() {
        PatternInstance instance = instance("pattern-1", "user-1", "chain-1");
        EvidenceItem oldest = evidenceItem("evidence-1", SourceType.chat_message, LocalDateTime.parse("2026-01-01T12:00:00"));
        EvidenceItem newest = evidenceItem("evidence-2", SourceType.journal_entry, LocalDateTime.parse("2026-01-03T12:00:00"));
        EvidenceItem middle = evidenceItem("evidence-3", SourceType.checkin, LocalDateTime.parse("2026-01-02T12:00:00"));
        this.instance = instance;
        this.evidenceItems = List.of(oldest, newest, middle);

        PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of(), false);

        assertThat(result.getApiVersion()).isEqualTo("v1");
        assertThat(result.getPatternInstanceId()).isEqualTo("pattern-1");
        assertThat(result.getHiddenEvidenceCount()).isZero();
        assertThat(result.getEvidenceItems()).extracting(EvidenceItemDto::getId)
                .containsExactly("evidence-2", "evidence-3", "evidence-1");
        assertThat(result.getEvidenceItems().get(0).getSourceType()).isEqualTo("journal");
        assertThat(result.getEvidenceItems().get(0).getExcerptVisibility()).isEqualTo(EvidenceExcerptVisibility.visible);
    }

    @Test
    void filters_to_requested_evidence_ids() {
        PatternInstance instance = instance("pattern-1", "user-1", "chain-1");
        this.instance = instance;
        this.evidenceItems = List.of(
                evidenceItem("evidence-1", SourceType.chat_message, LocalDateTime.parse("2026-01-01T12:00:00")),
                evidenceItem("evidence-2", SourceType.checkin, LocalDateTime.parse("2026-01-02T12:00:00")),
                evidenceItem("evidence-3", SourceType.wiki_fact, LocalDateTime.parse("2026-01-03T12:00:00"))
        );

        PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of("evidence-1", "missing-id", "evidence-3"), true);

        assertThat(result.getEvidenceItems()).extracting(EvidenceItemDto::getId)
                .containsExactly("evidence-3", "evidence-1");
        assertThat(result.getEvidenceItems()).extracting(EvidenceItemDto::getSourceType)
                .containsExactly("imported_note", "chat");
    }

    @Test
    void missing_pattern_throws_not_found_style_exception() {
        instance = null;

        assertThatThrownBy(() -> service.getEvidence("user-1", "missing", List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pattern instance not found");
    }

    @Test
    void unauthorized_pattern_throws_not_found_style_exception_without_loading_evidence() {
        instance = instance("pattern-1", "other-user", "chain-1");

        assertThatThrownBy(() -> service.getEvidence("user-1", "pattern-1", List.of(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pattern instance not found");
        assertThat(loadedChainIds).isEmpty();
    }

    @Test
    void ineligible_pattern_returns_empty_evidence_after_eligibility_check() {
        PatternInstance instance = instance("pattern-1", "user-1", "chain-1");
        instance.setStatus(PatternStatus.rejected);
        this.instance = instance;
        this.evidenceItems = sufficientEvidenceItems();

        PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of(), false);

        assertThat(result.getEvidenceItems()).isEmpty();
        assertThat(result.getHiddenEvidenceCount()).isZero();
        assertThat(loadedChainIds).containsExactly("chain-1");
    }

    @Test
    void candidate_deferred_archived_and_insufficient_patterns_do_not_expose_evidence() {
        for (PatternStatus status : List.of(PatternStatus.candidate, PatternStatus.deferred, PatternStatus.archived)) {
            loadedChainIds.clear();
            instance = instance("pattern-1", "user-1", "chain-1");
            instance.setStatus(status);
            evidenceItems = sufficientEvidenceItems();

            PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of(), false);

            assertThat(result.getEvidenceItems()).as(status.name()).isEmpty();
            assertThat(loadedChainIds).as(status.name()).containsExactly("chain-1");
        }

        loadedChainIds.clear();
        instance = instance("pattern-1", "user-1", "chain-1");
        evidenceItems = List.of(
                evidenceItem("evidence-1", SourceType.chat_message, LocalDateTime.parse("2026-01-01T12:00:00")),
                evidenceItem("evidence-2", SourceType.checkin, LocalDateTime.parse("2026-01-02T12:00:00"))
        );

        PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of(), false);

        assertThat(result.getEvidenceItems()).isEmpty();
        assertThat(loadedChainIds).containsExactly("chain-1");
    }

    @Test
    void orders_same_timestamp_evidence_by_id() {
        instance = instance("pattern-1", "user-1", "chain-1");
        LocalDateTime occurredAt = LocalDateTime.parse("2026-01-01T12:00:00");
        evidenceItems = List.of(
                evidenceItem("evidence-c", SourceType.chat_message, occurredAt),
                evidenceItem("evidence-a", SourceType.chat_message, occurredAt),
                evidenceItem("evidence-b", SourceType.chat_message, occurredAt)
        );

        PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of(), false);

        assertThat(result.getEvidenceItems()).extracting(EvidenceItemDto::getId)
                .containsExactly("evidence-a", "evidence-b", "evidence-c");
    }

    @Test
    void maps_unavailable_excerpt_and_unknown_source_type() {
        EvidenceItem item = evidenceItem("evidence-1", null, LocalDateTime.parse("2026-01-01T12:00:00"));
        item.setExcerpt(null);

        EvidenceItemDto result = service.toDto(item);

        assertThat(result.getSourceType()).isEqualTo("unknown");
        assertThat(result.getExcerpt()).isNull();
        assertThat(result.getExcerptVisibility()).isEqualTo(EvidenceExcerptVisibility.unavailable);
        assertThat(result.getDeepLink()).isNull();
    }

    @Test
    void loads_only_the_current_evidence_chain() {
        PatternInstance instance = instance("pattern-1", "user-1", "chain-1");
        this.instance = instance;
        this.evidenceItems = sufficientEvidenceItems();

        service.getEvidence("user-1", "pattern-1", List.of(), false);

        assertThat(loadedChainIds).containsExactly("chain-1", "chain-1");
    }

    @Test
    void missing_evidence_chain_returns_empty_evidence_without_repository_lookup() {
        PatternInstance instance = instance("pattern-1", "user-1", null);
        this.instance = instance;

        PatternStructureEvidenceResponse result = service.getEvidence("user-1", "pattern-1", List.of(), false);

        assertThat(result.getEvidenceItems()).isEmpty();
        assertThat(loadedChainIds).isEmpty();
    }

    private PatternInstanceRepository patternInstanceRepository() {
        return (PatternInstanceRepository) Proxy.newProxyInstance(
                PatternInstanceRepository.class.getClassLoader(),
                new Class[]{PatternInstanceRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.ofNullable(instance);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private EvidenceItemRepository evidenceItemRepository() {
        return (EvidenceItemRepository) Proxy.newProxyInstance(
                EvidenceItemRepository.class.getClassLoader(),
                new Class[]{EvidenceItemRepository.class},
                (proxy, method, args) -> {
                    if ("findByEvidenceChainId".equals(method.getName())) {
                        loadedChainIds.add((String) args[0]);
                        return evidenceItems;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType.equals(boolean.class)) {
            return false;
        }
        if (returnType.equals(int.class) || returnType.equals(long.class)) {
            return 0;
        }
        return null;
    }

    private PatternInstance instance(String id, String userId, String evidenceChainId) {
        PatternInstance instance = new PatternInstance();
        instance.setId(id);
        instance.setUserId(userId);
        instance.setPatternKey("pattern_key");
        instance.setDomain(Domain.work);
        instance.setStatus(PatternStatus.confirmed);
        instance.setEvidenceChainId(evidenceChainId);
        return instance;
    }

    private List<EvidenceItem> sufficientEvidenceItems() {
        return List.of(
                evidenceItem("evidence-1", SourceType.chat_message, LocalDateTime.parse("2026-01-01T12:00:00")),
                evidenceItem("evidence-2", SourceType.checkin, LocalDateTime.parse("2026-01-02T12:00:00")),
                evidenceItem("evidence-3", SourceType.wiki_fact, LocalDateTime.parse("2026-01-03T12:00:00"))
        );
    }

    private EvidenceItem evidenceItem(String id, SourceType sourceType, LocalDateTime occurredAt) {
        EvidenceItem item = new EvidenceItem();
        item.setId(id);
        item.setEvidenceChainId("chain-1");
        item.setSourceType(sourceType);
        item.setSourceRef("source-" + id);
        item.setOccurredAt(occurredAt);
        item.setExcerpt("excerpt " + id);
        item.setVerbatim(true);
        item.setInterpretation("interpretation " + id);
        return item;
    }
}
