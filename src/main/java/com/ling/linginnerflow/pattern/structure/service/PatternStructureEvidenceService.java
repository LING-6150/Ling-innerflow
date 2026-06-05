package com.ling.linginnerflow.pattern.structure.service;

import com.ling.linginnerflow.pattern.domain.SourceType;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.structure.dto.EvidenceExcerptVisibility;
import com.ling.linginnerflow.pattern.structure.dto.EvidenceItemDto;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityState;
import com.ling.linginnerflow.pattern.structure.dto.PatternStructureEvidenceResponse;
import com.ling.linginnerflow.pattern.structure.dto.StructureEligibilityDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PatternStructureEvidenceService {

    private final PatternInstanceRepository patternInstanceRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final PatternStructureEligibilityService eligibilityService;

    public PatternStructureEvidenceResponse getEvidence(String userId,
                                                        String patternInstanceId,
                                                        List<String> evidenceItemIds,
                                                        boolean includeHiddenCounts) {
        PatternInstance instance = patternInstanceRepository.findById(patternInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern instance not found"));
        if (!userId.equals(instance.getUserId())) {
            throw new IllegalArgumentException("Pattern instance not found");
        }

        StructureEligibilityDto eligibility = eligibilityService.getEligibility(userId, patternInstanceId);
        if (eligibility.getState() != EligibilityState.allowed) {
            return response(instance.getId(), List.of(), 0);
        }

        Set<String> requestedIds = evidenceItemIds == null ? Set.of() : new HashSet<>(evidenceItemIds);
        List<EvidenceItem> chainItems = instance.getEvidenceChainId() == null
                ? List.of()
                : evidenceItemRepository.findByEvidenceChainId(instance.getEvidenceChainId());
        List<EvidenceItemDto> items = chainItems.stream()
                .filter(item -> requestedIds.isEmpty() || requestedIds.contains(item.getId()))
                .sorted(Comparator.comparing(EvidenceItem::getOccurredAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(EvidenceItem::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toDto)
                .toList();

        return response(instance.getId(), items, 0);
    }

    private PatternStructureEvidenceResponse response(String patternInstanceId,
                                                      List<EvidenceItemDto> evidenceItems,
                                                      int hiddenEvidenceCount) {
        return PatternStructureEvidenceResponse.builder()
                .apiVersion("v1")
                .patternInstanceId(patternInstanceId)
                .evidenceItems(evidenceItems)
                .hiddenEvidenceCount(hiddenEvidenceCount)
                .build();
    }

    EvidenceItemDto toDto(EvidenceItem item) {
        return EvidenceItemDto.builder()
                .id(item.getId())
                .sourceType(sourceType(item.getSourceType()))
                .sourceRef(item.getSourceRef())
                .occurredAt(item.getOccurredAt())
                .excerpt(item.getExcerpt())
                .excerptVisibility(item.getExcerpt() == null ? EvidenceExcerptVisibility.unavailable : EvidenceExcerptVisibility.visible)
                .verbatim(item.isVerbatim())
                .interpretation(item.getInterpretation())
                .deepLink(null)
                .build();
    }

    private String sourceType(SourceType sourceType) {
        if (sourceType == null) {
            return "unknown";
        }
        return switch (sourceType) {
            case chat_message -> "chat";
            case journal_entry -> "journal";
            case checkin -> "checkin";
            case wiki_fact -> "imported_note";
        };
    }
}
