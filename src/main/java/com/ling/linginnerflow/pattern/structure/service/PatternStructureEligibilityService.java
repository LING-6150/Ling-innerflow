package com.ling.linginnerflow.pattern.structure.service;

import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityAction;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityBlockedReason;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityEvidenceSummary;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityReasonCode;
import com.ling.linginnerflow.pattern.structure.dto.EligibilitySafetySummary;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityState;
import com.ling.linginnerflow.pattern.structure.dto.StructureEligibilityDto;
import com.ling.linginnerflow.pattern.structure.dto.TrustTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatternStructureEligibilityService {

    static final int MINIMUM_REQUIRED_EVIDENCE_COUNT = 3;

    private final PatternInstanceRepository patternInstanceRepository;
    private final EvidenceItemRepository evidenceItemRepository;

    public StructureEligibilityDto getEligibility(String userId, String patternInstanceId) {
        PatternInstance instance = patternInstanceRepository.findById(patternInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern instance not found"));
        if (!userId.equals(instance.getUserId())) {
            throw new IllegalArgumentException("Pattern instance not found");
        }

        List<EvidenceItem> evidenceItems = evidenceItems(instance);
        return mapEligibility(instance, evidenceItems, false);
    }

    StructureEligibilityDto mapEligibility(PatternInstance instance,
                                           List<EvidenceItem> evidenceItems,
                                           boolean crisisSafetyBlocked) {
        EligibilityEvidenceSummary evidenceSummary = evidenceSummary(instance, evidenceItems);
        EligibilitySafetySummary safetySummary = safetySummary(crisisSafetyBlocked);

        if (safetySummary.isSafetyBlocked()) {
            return response(instance, EligibilityState.crisis_safety_blocked, false, null,
                    EligibilityReasonCode.safety_blocked_crisis,
                    "Structure is hidden for safety.",
                    List.of(EligibilityAction.no_action_available),
                    evidenceSummary, safetySummary);
        }

        if (instance.isHidden()) {
            return response(instance, EligibilityState.unsupported, false, null,
                    EligibilityReasonCode.structure_not_enabled,
                    "Structure is not available for this pattern.",
                    List.of(EligibilityAction.no_action_available),
                    evidenceSummary, safetySummary);
        }

        PatternStatus status = instance.getStatus();
        if (status == PatternStatus.confirmed || status == PatternStatus.partially_confirmed) {
            if (evidenceItems.size() < MINIMUM_REQUIRED_EVIDENCE_COUNT) {
                return response(instance, EligibilityState.insufficient_evidence, false, null,
                        EligibilityReasonCode.too_few_evidence_items,
                        "Not enough accepted evidence yet.",
                        List.of(EligibilityAction.add_or_wait_for_evidence),
                        evidenceSummary, safetySummary);
            }
            EligibilityReasonCode reasonCode = status == PatternStatus.confirmed
                    ? EligibilityReasonCode.confirmed_pattern
                    : EligibilityReasonCode.partially_confirmed_pattern;
            return response(instance, EligibilityState.allowed, true, TrustTier.confirmed_by_user,
                    reasonCode,
                    "Structure is available for this reviewed pattern.",
                    List.of(EligibilityAction.view_evidence),
                    evidenceSummary, safetySummary);
        }

        if (status == PatternStatus.candidate) {
            return response(instance, EligibilityState.unreviewed, false, null,
                    EligibilityReasonCode.awaiting_user_review,
                    "Review this pattern before viewing structure.",
                    List.of(EligibilityAction.open_review),
                    evidenceSummary, safetySummary);
        }

        if (status == PatternStatus.rejected) {
            return response(instance, EligibilityState.rejected, false, null,
                    EligibilityReasonCode.rejected_by_user,
                    "Structure is hidden because this pattern was rejected.",
                    List.of(EligibilityAction.no_action_available),
                    evidenceSummary, safetySummary);
        }

        if (status == PatternStatus.deferred) {
            return response(instance, EligibilityState.deferred, false, null,
                    EligibilityReasonCode.user_deferred_review,
                    "Review is deferred for this pattern.",
                    List.of(EligibilityAction.open_review),
                    evidenceSummary, safetySummary);
        }

        if (status == PatternStatus.archived) {
            return response(instance, EligibilityState.unsupported, false, null,
                    EligibilityReasonCode.unsupported_pattern_type,
                    "Structure is not available for archived patterns.",
                    List.of(EligibilityAction.no_action_available),
                    evidenceSummary, safetySummary);
        }

        return response(instance, EligibilityState.unsupported, false, null,
                EligibilityReasonCode.unsupported_pattern_type,
                "Structure is not available for this pattern.",
                List.of(EligibilityAction.no_action_available),
                evidenceSummary, safetySummary);
    }

    private List<EvidenceItem> evidenceItems(PatternInstance instance) {
        if (instance.getEvidenceChainId() == null || instance.getEvidenceChainId().isBlank()) {
            return List.of();
        }
        return evidenceItemRepository.findByEvidenceChainId(instance.getEvidenceChainId());
    }

    private EligibilityEvidenceSummary evidenceSummary(PatternInstance instance, List<EvidenceItem> evidenceItems) {
        LocalDateTime firstObservedAt = evidenceItems.stream()
                .map(EvidenceItem::getOccurredAt)
                .filter(occurredAt -> occurredAt != null)
                .min(Comparator.naturalOrder())
                .orElse(instance.getFirstObservedAt());
        LocalDateTime lastObservedAt = evidenceItems.stream()
                .map(EvidenceItem::getOccurredAt)
                .filter(occurredAt -> occurredAt != null)
                .max(Comparator.naturalOrder())
                .orElse(instance.getLastObservedAt());
        List<String> sourceChainIds = instance.getEvidenceChainId() == null || instance.getEvidenceChainId().isBlank()
                ? List.of()
                : List.of(instance.getEvidenceChainId());

        return EligibilityEvidenceSummary.builder()
                .evidenceCount(evidenceItems.size())
                .minimumRequiredCount(MINIMUM_REQUIRED_EVIDENCE_COUNT)
                .sourceChainIds(sourceChainIds)
                .firstObservedAt(firstObservedAt)
                .lastObservedAt(lastObservedAt)
                .build();
    }

    private EligibilitySafetySummary safetySummary(boolean safetyBlocked) {
        return EligibilitySafetySummary.builder()
                .safetyBlocked(safetyBlocked)
                .blockedReason(safetyBlocked ? EligibilityBlockedReason.crisis : null)
                .hiddenEvidenceCount(0)
                .build();
    }

    private StructureEligibilityDto response(PatternInstance instance,
                                             EligibilityState state,
                                             boolean canShowStructure,
                                             TrustTier trustTier,
                                             EligibilityReasonCode reasonCode,
                                             String displayMessage,
                                             List<EligibilityAction> requiredActions,
                                             EligibilityEvidenceSummary evidenceSummary,
                                             EligibilitySafetySummary safetySummary) {
        return StructureEligibilityDto.builder()
                .state(state)
                .canShowStructure(canShowStructure)
                .patternStatus(instance.getStatus())
                .trustTier(trustTier)
                .reasonCode(reasonCode)
                .displayMessage(displayMessage)
                .requiredActions(requiredActions)
                .evidenceSummary(evidenceSummary)
                .safetySummary(safetySummary)
                .cooldownSummary(null)
                .build();
    }
}
