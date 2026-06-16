package com.ling.linginnerflow.pattern.structure.service;

import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.structure.dto.EvidenceWindowDto;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityState;
import com.ling.linginnerflow.pattern.structure.dto.ModuleEmptyStateCode;
import com.ling.linginnerflow.pattern.structure.dto.ModuleEmptyStateDto;
import com.ling.linginnerflow.pattern.structure.dto.ModuleStatus;
import com.ling.linginnerflow.pattern.structure.dto.NeighborPatternsModuleDto;
import com.ling.linginnerflow.pattern.structure.dto.NeighborWindowPolicyDto;
import com.ling.linginnerflow.pattern.structure.dto.PatternStructureModulesDto;
import com.ling.linginnerflow.pattern.structure.dto.PatternStructureResponse;
import com.ling.linginnerflow.pattern.structure.dto.RecentDensityDto;
import com.ling.linginnerflow.pattern.structure.dto.RelationshipObjectsModuleDto;
import com.ling.linginnerflow.pattern.structure.dto.SceneDistributionModuleDto;
import com.ling.linginnerflow.pattern.structure.dto.StructureEligibilityDto;
import com.ling.linginnerflow.pattern.structure.dto.StructureSafetyDto;
import com.ling.linginnerflow.pattern.structure.dto.TemporalStructureModuleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatternStructureAggregateService {

    static final String MODULE_ALL = "all";
    static final String MODULE_SCENE_DISTRIBUTION = "scene_distribution";
    static final String MODULE_RELATIONSHIP_OBJECTS = "relationship_objects";
    static final String MODULE_TEMPORAL_STRUCTURE = "temporal_structure";
    static final String MODULE_NEIGHBOR_PATTERNS = "neighbor_patterns";

    private final PatternInstanceRepository patternInstanceRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final PatternStructureEligibilityService eligibilityService;

    public PatternStructureResponse getStructure(String userId,
                                                 String patternInstanceId,
                                                 String module,
                                                 boolean includeEvidenceSamples) {
        String requestedModule = normalizeModule(module);
        PatternInstance instance = patternInstanceRepository.findById(patternInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern instance not found"));
        if (!userId.equals(instance.getUserId())) {
            throw new IllegalArgumentException("Pattern instance not found");
        }

        StructureEligibilityDto eligibility = eligibilityService.getEligibility(userId, patternInstanceId);
        boolean canShowStructure = eligibility.getState() == EligibilityState.allowed;
        List<EvidenceItem> evidenceItems = canShowStructure ? evidenceItems(instance) : List.of();
        ModuleEmptyStateDto emptyState = emptyState(eligibility, evidenceItems.size());

        return PatternStructureResponse.builder()
                .apiVersion("v1")
                .patternInstanceId(instance.getId())
                .patternKey(instance.getPatternKey())
                .displayName(instance.getPatternKey())
                .patternStatus(instance.getStatus())
                .eligibility(eligibility)
                .generatedAt(null)
                .generatorVersion(null)
                .sourceChainIds(canShowStructure && instance.getEvidenceChainId() != null ? List.of(instance.getEvidenceChainId()) : List.of())
                .evidenceWindow(evidenceWindow(canShowStructure, evidenceItems))
                .modules(modules(requestedModule, emptyState))
                .safety(safety())
                .experimental(null)
                .build();
    }

    private String normalizeModule(String module) {
        if (module == null || module.isBlank()) {
            return MODULE_ALL;
        }
        String normalized = module.trim();
        if (List.of(MODULE_ALL, MODULE_SCENE_DISTRIBUTION, MODULE_RELATIONSHIP_OBJECTS,
                MODULE_TEMPORAL_STRUCTURE, MODULE_NEIGHBOR_PATTERNS).contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported structure module: " + module);
    }

    private List<EvidenceItem> evidenceItems(PatternInstance instance) {
        if (instance.getEvidenceChainId() == null || instance.getEvidenceChainId().isBlank()) {
            return List.of();
        }
        return evidenceItemRepository.findByEvidenceChainId(instance.getEvidenceChainId()).stream()
                .sorted(Comparator.comparing(EvidenceItem::getOccurredAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EvidenceItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private EvidenceWindowDto evidenceWindow(boolean canShowStructure, List<EvidenceItem> evidenceItems) {
        if (!canShowStructure) {
            return EvidenceWindowDto.builder()
                    .evidenceCount(0)
                    .firstObservedAt(null)
                    .lastObservedAt(null)
                    .evidenceItemIds(List.of())
                    .build();
        }
        return EvidenceWindowDto.builder()
                .evidenceCount(evidenceItems.size())
                .firstObservedAt(firstObservedAt(evidenceItems))
                .lastObservedAt(lastObservedAt(evidenceItems))
                .evidenceItemIds(evidenceItems.stream().map(EvidenceItem::getId).toList())
                .build();
    }

    private LocalDateTime firstObservedAt(List<EvidenceItem> evidenceItems) {
        return evidenceItems.stream()
                .map(EvidenceItem::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private LocalDateTime lastObservedAt(List<EvidenceItem> evidenceItems) {
        return evidenceItems.stream()
                .map(EvidenceItem::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private PatternStructureModulesDto modules(String requestedModule, ModuleEmptyStateDto emptyState) {
        return PatternStructureModulesDto.builder()
                .sceneDistribution(sceneDistribution(requestedModule, emptyState))
                .relationshipObjects(relationshipObjects(requestedModule, emptyState))
                .temporalStructure(temporalStructure(requestedModule, emptyState))
                .neighborPatterns(neighborPatterns(requestedModule, emptyState))
                .build();
    }

    private SceneDistributionModuleDto sceneDistribution(String requestedModule, ModuleEmptyStateDto emptyState) {
        return SceneDistributionModuleDto.builder()
                .module(MODULE_SCENE_DISTRIBUTION)
                .status(moduleStatusFor(MODULE_SCENE_DISTRIBUTION, requestedModule, emptyState))
                .scenes(List.of())
                .emptyState(emptyStateFor(MODULE_SCENE_DISTRIBUTION, requestedModule, emptyState))
                .build();
    }

    private RelationshipObjectsModuleDto relationshipObjects(String requestedModule, ModuleEmptyStateDto emptyState) {
        return RelationshipObjectsModuleDto.builder()
                .module(MODULE_RELATIONSHIP_OBJECTS)
                .status(moduleStatusFor(MODULE_RELATIONSHIP_OBJECTS, requestedModule, emptyState))
                .objects(List.of())
                .emptyState(emptyStateFor(MODULE_RELATIONSHIP_OBJECTS, requestedModule, emptyState))
                .build();
    }

    private TemporalStructureModuleDto temporalStructure(String requestedModule, ModuleEmptyStateDto emptyState) {
        return TemporalStructureModuleDto.builder()
                .module(MODULE_TEMPORAL_STRUCTURE)
                .status(moduleStatusFor(MODULE_TEMPORAL_STRUCTURE, requestedModule, emptyState))
                .firstObservedAt(null)
                .lastObservedAt(null)
                .recentDensity(RecentDensityDto.builder().last7Days(0).last30Days(0).last90Days(0).build())
                .concentrationPoints(List.of())
                .timelineEvidenceItemIds(List.of())
                .emptyState(emptyStateFor(MODULE_TEMPORAL_STRUCTURE, requestedModule, emptyState))
                .build();
    }

    private NeighborPatternsModuleDto neighborPatterns(String requestedModule, ModuleEmptyStateDto emptyState) {
        return NeighborPatternsModuleDto.builder()
                .module(MODULE_NEIGHBOR_PATTERNS)
                .status(moduleStatusFor(MODULE_NEIGHBOR_PATTERNS, requestedModule, emptyState))
                .windowPolicy(NeighborWindowPolicyDto.builder()
                        .sameSourceRef(true)
                        .sameThread(true)
                        .timeWindowHours(24)
                        .sameContextTag(true)
                        .build())
                .neighbors(List.of())
                .emptyState(emptyStateFor(MODULE_NEIGHBOR_PATTERNS, requestedModule, emptyState))
                .build();
    }

    private ModuleStatus moduleStatusFor(String module, String requestedModule, ModuleEmptyStateDto emptyState) {
        if (!MODULE_ALL.equals(requestedModule) && !module.equals(requestedModule)) {
            return ModuleStatus.not_requested;
        }
        if (emptyState.getCode() == ModuleEmptyStateCode.no_safe_evidence_to_show) {
            return ModuleStatus.hidden_for_safety;
        }
        if (emptyState.getCode() == ModuleEmptyStateCode.extraction_not_available) {
            return ModuleStatus.not_applicable;
        }
        return ModuleStatus.insufficient_data;
    }

    private ModuleEmptyStateDto emptyStateFor(String module, String requestedModule, ModuleEmptyStateDto emptyState) {
        if (!MODULE_ALL.equals(requestedModule) && !module.equals(requestedModule)) {
            return ModuleEmptyStateDto.builder()
                    .code(ModuleEmptyStateCode.not_requested)
                    .displayMessage("This section was not requested.")
                    .evidenceCount(0)
                    .minimumRequiredCount(null)
                    .build();
        }
        return emptyState;
    }

    private ModuleEmptyStateDto emptyState(StructureEligibilityDto eligibility, int evidenceCount) {
        if (eligibility.getState() == EligibilityState.insufficient_evidence) {
            return ModuleEmptyStateDto.builder()
                    .code(ModuleEmptyStateCode.not_enough_accepted_evidence)
                    .displayMessage("Not enough accepted evidence yet.")
                    .evidenceCount(eligibility.getEvidenceSummary().getEvidenceCount())
                    .minimumRequiredCount(eligibility.getEvidenceSummary().getMinimumRequiredCount())
                    .build();
        }
        return ModuleEmptyStateDto.builder()
                .code(ModuleEmptyStateCode.extraction_not_available)
                .displayMessage("This section is not available for the current evidence.")
                .evidenceCount(evidenceCount)
                .minimumRequiredCount(null)
                .build();
    }

    private StructureSafetyDto safety() {
        return StructureSafetyDto.builder()
                .rawExcerptFilterApplied(false)
                .hiddenEvidenceCount(0)
                .llmFieldsPresent(List.of())
                .deterministicFieldsPresent(List.of("eligibility", "evidence_window", "module_empty_states"))
                .build();
    }
}
