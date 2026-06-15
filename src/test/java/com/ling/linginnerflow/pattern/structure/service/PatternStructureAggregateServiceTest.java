package com.ling.linginnerflow.pattern.structure.service;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.domain.SourceType;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityState;
import com.ling.linginnerflow.pattern.structure.dto.ModuleEmptyStateCode;
import com.ling.linginnerflow.pattern.structure.dto.ModuleStatus;
import com.ling.linginnerflow.pattern.structure.dto.PatternStructureResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatternStructureAggregateServiceTest {

    private PatternInstance instance;
    private List<EvidenceItem> evidenceItems = List.of();
    private final List<String> loadedChainIds = new ArrayList<>();
    private final PatternInstanceRepository patternInstanceRepository = patternInstanceRepository();
    private final EvidenceItemRepository evidenceItemRepository = evidenceItemRepository();
    private final PatternStructureEligibilityService eligibilityService = new PatternStructureEligibilityService(
            patternInstanceRepository,
            evidenceItemRepository
    );
    private final PatternStructureAggregateService service = new PatternStructureAggregateService(
            patternInstanceRepository,
            evidenceItemRepository,
            eligibilityService
    );

    @Test
    void allowed_pattern_returns_aggregate_shell_with_evidence_window() {
        instance = instance(PatternStatus.confirmed);
        evidenceItems = List.of(
                evidenceItem("evidence-2", LocalDateTime.parse("2026-01-02T12:00:00")),
                evidenceItem("evidence-1", LocalDateTime.parse("2026-01-01T12:00:00")),
                evidenceItem("evidence-3", LocalDateTime.parse("2026-01-03T12:00:00"))
        );

        PatternStructureResponse result = service.getStructure("user-1", "pattern-1", "all", false);

        assertThat(result.getApiVersion()).isEqualTo("v1");
        assertThat(result.getPatternInstanceId()).isEqualTo("pattern-1");
        assertThat(result.getPatternKey()).isEqualTo("pattern_key");
        assertThat(result.getDisplayName()).isEqualTo("pattern_key");
        assertThat(result.getPatternStatus()).isEqualTo(PatternStatus.confirmed);
        assertThat(result.getEligibility().getState()).isEqualTo(EligibilityState.allowed);
        assertThat(result.getGeneratedAt()).isNull();
        assertThat(result.getGeneratorVersion()).isNull();
        assertThat(result.getSourceChainIds()).containsExactly("chain-1");
        assertThat(result.getEvidenceWindow().getEvidenceCount()).isEqualTo(3);
        assertThat(result.getEvidenceWindow().getEvidenceItemIds()).containsExactly("evidence-3", "evidence-2", "evidence-1");
        assertThat(result.getEvidenceWindow().getFirstObservedAt()).isEqualTo(LocalDateTime.parse("2026-01-01T12:00:00"));
        assertThat(result.getEvidenceWindow().getLastObservedAt()).isEqualTo(LocalDateTime.parse("2026-01-03T12:00:00"));
        assertAllModulesNotApplicable(result);
        assertThat(result.getModules().getSceneDistribution().getEmptyState().getCode()).isEqualTo(ModuleEmptyStateCode.extraction_not_available);
        assertThat(result.getModules().getSceneDistribution().getEmptyState().getEvidenceCount()).isEqualTo(3);
        assertThat(result.getSafety().isRawExcerptFilterApplied()).isFalse();
        assertThat(result.getSafety().getHiddenEvidenceCount()).isZero();
        assertThat(result.getSafety().getDeterministicFieldsPresent()).contains("eligibility", "evidence_window", "module_empty_states");
        assertThat(result.getExperimental()).isNull();
    }

    @Test
    void ineligible_pattern_returns_empty_evidence_window_and_unavailable_modules() {
        instance = instance(PatternStatus.rejected);
        evidenceItems = sufficientEvidenceItems();

        PatternStructureResponse result = service.getStructure("user-1", "pattern-1", "all", false);

        assertThat(result.getEligibility().getState()).isEqualTo(EligibilityState.rejected);
        assertThat(result.getSourceChainIds()).isEmpty();
        assertThat(result.getEvidenceWindow().getEvidenceCount()).isZero();
        assertThat(result.getEvidenceWindow().getEvidenceItemIds()).isEmpty();
        assertThat(result.getEvidenceWindow().getFirstObservedAt()).isNull();
        assertThat(result.getEvidenceWindow().getLastObservedAt()).isNull();
        assertAllModulesNotApplicable(result);
    }

    @Test
    void insufficient_evidence_uses_not_enough_empty_state() {
        instance = instance(PatternStatus.confirmed);
        evidenceItems = List.of(
                evidenceItem("evidence-1", LocalDateTime.parse("2026-01-01T12:00:00")),
                evidenceItem("evidence-2", LocalDateTime.parse("2026-01-02T12:00:00"))
        );

        PatternStructureResponse result = service.getStructure("user-1", "pattern-1", "all", false);

        assertThat(result.getEligibility().getState()).isEqualTo(EligibilityState.insufficient_evidence);
        assertThat(result.getEvidenceWindow().getEvidenceItemIds()).isEmpty();
        assertThat(result.getModules().getSceneDistribution().getEmptyState().getCode())
                .isEqualTo(ModuleEmptyStateCode.not_enough_accepted_evidence);
        assertThat(result.getModules().getSceneDistribution().getStatus()).isEqualTo(ModuleStatus.insufficient_data);
        assertThat(result.getModules().getSceneDistribution().getEmptyState().getEvidenceCount()).isEqualTo(2);
        assertThat(result.getModules().getSceneDistribution().getEmptyState().getMinimumRequiredCount()).isEqualTo(3);
    }

    @Test
    void module_filter_marks_other_modules_not_requested() {
        instance = instance(PatternStatus.confirmed);
        evidenceItems = sufficientEvidenceItems();

        PatternStructureResponse result = service.getStructure("user-1", "pattern-1", "scene_distribution", false);

        assertThat(result.getModules().getSceneDistribution().getEmptyState().getCode())
                .isEqualTo(ModuleEmptyStateCode.extraction_not_available);
        assertThat(result.getModules().getSceneDistribution().getStatus())
                .isEqualTo(ModuleStatus.not_applicable);
        assertThat(result.getModules().getRelationshipObjects().getEmptyState().getCode())
                .isEqualTo(ModuleEmptyStateCode.not_requested);
        assertThat(result.getModules().getRelationshipObjects().getStatus())
                .isEqualTo(ModuleStatus.not_requested);
        assertThat(result.getModules().getTemporalStructure().getEmptyState().getCode())
                .isEqualTo(ModuleEmptyStateCode.not_requested);
        assertThat(result.getModules().getTemporalStructure().getStatus())
                .isEqualTo(ModuleStatus.not_requested);
        assertThat(result.getModules().getNeighborPatterns().getEmptyState().getCode())
                .isEqualTo(ModuleEmptyStateCode.not_requested);
        assertThat(result.getModules().getNeighborPatterns().getStatus())
                .isEqualTo(ModuleStatus.not_requested);
    }

    @Test
    void unknown_module_throws_invalid_request_style_exception() {
        instance = instance(PatternStatus.confirmed);

        assertThatThrownBy(() -> service.getStructure("user-1", "pattern-1", "unknown_module", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported structure module: unknown_module");
    }

    @Test
    void unauthorized_pattern_throws_not_found_without_loading_evidence() {
        instance = instance(PatternStatus.confirmed);
        instance.setUserId("other-user");
        evidenceItems = sufficientEvidenceItems();

        assertThatThrownBy(() -> service.getStructure("user-1", "pattern-1", "all", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pattern instance not found");
        assertThat(loadedChainIds).isEmpty();
    }

    private void assertAllModulesNotApplicable(PatternStructureResponse result) {
        assertThat(result.getModules().getSceneDistribution().getStatus()).isEqualTo(ModuleStatus.not_applicable);
        assertThat(result.getModules().getRelationshipObjects().getStatus()).isEqualTo(ModuleStatus.not_applicable);
        assertThat(result.getModules().getTemporalStructure().getStatus()).isEqualTo(ModuleStatus.not_applicable);
        assertThat(result.getModules().getNeighborPatterns().getStatus()).isEqualTo(ModuleStatus.not_applicable);
        assertThat(result.getModules().getSceneDistribution().getScenes()).isEmpty();
        assertThat(result.getModules().getRelationshipObjects().getObjects()).isEmpty();
        assertThat(result.getModules().getTemporalStructure().getConcentrationPoints()).isEmpty();
        assertThat(result.getModules().getTemporalStructure().getTimelineEvidenceItemIds()).isEmpty();
        assertThat(result.getModules().getNeighborPatterns().getNeighbors()).isEmpty();
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

    private PatternInstance instance(PatternStatus status) {
        PatternInstance instance = new PatternInstance();
        instance.setId("pattern-1");
        instance.setUserId("user-1");
        instance.setPatternKey("pattern_key");
        instance.setDomain(Domain.work);
        instance.setStatus(status);
        instance.setEvidenceChainId("chain-1");
        return instance;
    }

    private List<EvidenceItem> sufficientEvidenceItems() {
        return List.of(
                evidenceItem("evidence-1", LocalDateTime.parse("2026-01-01T12:00:00")),
                evidenceItem("evidence-2", LocalDateTime.parse("2026-01-02T12:00:00")),
                evidenceItem("evidence-3", LocalDateTime.parse("2026-01-03T12:00:00"))
        );
    }

    private EvidenceItem evidenceItem(String id, LocalDateTime occurredAt) {
        EvidenceItem item = new EvidenceItem();
        item.setId(id);
        item.setEvidenceChainId("chain-1");
        item.setSourceType(SourceType.chat_message);
        item.setSourceRef("source-" + id);
        item.setOccurredAt(occurredAt);
        item.setExcerpt("excerpt " + id);
        item.setVerbatim(true);
        item.setInterpretation("interpretation " + id);
        return item;
    }
}
