package com.ling.linginnerflow.pattern.structure.service;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityAction;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityBlockedReason;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityReasonCode;
import com.ling.linginnerflow.pattern.structure.dto.EligibilityState;
import com.ling.linginnerflow.pattern.structure.dto.StructureEligibilityDto;
import com.ling.linginnerflow.pattern.structure.dto.TrustTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class PatternStructureEligibilityServiceTest {

    private final PatternStructureEligibilityService service = new PatternStructureEligibilityService(
            null,
            null
    );

    @Test
    void confirmed_with_sufficient_evidence_is_allowed() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.confirmed),
                evidenceItems(3),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.allowed);
        assertThat(result.isCanShowStructure()).isTrue();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.confirmed_pattern);
        assertThat(result.getTrustTier()).isEqualTo(TrustTier.confirmed_by_user);
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.view_evidence);
        assertThat(result.getCooldownSummary()).isNull();
    }

    @Test
    void partially_confirmed_with_sufficient_evidence_is_allowed() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.partially_confirmed),
                evidenceItems(3),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.allowed);
        assertThat(result.isCanShowStructure()).isTrue();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.partially_confirmed_pattern);
        assertThat(result.getTrustTier()).isEqualTo(TrustTier.confirmed_by_user);
    }

    @Test
    void candidate_is_unreviewed() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.candidate),
                evidenceItems(3),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.unreviewed);
        assertThat(result.isCanShowStructure()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.awaiting_user_review);
        assertThat(result.getTrustTier()).isNull();
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.open_review);
    }

    @Test
    void rejected_is_rejected_without_cooldown_when_no_cooldown_data_exists() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.rejected),
                evidenceItems(3),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.rejected);
        assertThat(result.isCanShowStructure()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.rejected_by_user);
        assertThat(result.getCooldownSummary()).isNull();
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.no_action_available);
    }

    @Test
    void deferred_is_deferred() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.deferred),
                evidenceItems(3),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.deferred);
        assertThat(result.isCanShowStructure()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.user_deferred_review);
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.open_review);
    }

    @Test
    void archived_is_unsupported() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.archived),
                evidenceItems(3),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.unsupported);
        assertThat(result.isCanShowStructure()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.unsupported_pattern_type);
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.no_action_available);
    }

    @Test
    void confirmed_with_too_few_evidence_items_is_insufficient_evidence() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.confirmed),
                evidenceItems(2),
                false);

        assertThat(result.getState()).isEqualTo(EligibilityState.insufficient_evidence);
        assertThat(result.isCanShowStructure()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.too_few_evidence_items);
        assertThat(result.getEvidenceSummary().getEvidenceCount()).isEqualTo(2);
        assertThat(result.getEvidenceSummary().getMinimumRequiredCount()).isEqualTo(3);
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.add_or_wait_for_evidence);
    }

    @Test
    void crisis_safety_blocked_overrides_review_status() {
        StructureEligibilityDto result = service.mapEligibility(
                instance(PatternStatus.confirmed),
                evidenceItems(3),
                true);

        assertThat(result.getState()).isEqualTo(EligibilityState.crisis_safety_blocked);
        assertThat(result.isCanShowStructure()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo(EligibilityReasonCode.safety_blocked_crisis);
        assertThat(result.getSafetySummary().isSafetyBlocked()).isTrue();
        assertThat(result.getSafetySummary().getBlockedReason()).isEqualTo(EligibilityBlockedReason.crisis);
        assertThat(result.getRequiredActions()).containsExactly(EligibilityAction.no_action_available);
    }

    private PatternInstance instance(PatternStatus status) {
        PatternInstance instance = new PatternInstance();
        instance.setId("pattern-1");
        instance.setUserId("user-1");
        instance.setPatternKey("pattern_key");
        instance.setDomain(Domain.work);
        instance.setStatus(status);
        instance.setEvidenceChainId("chain-1");
        instance.setFirstObservedAt(LocalDateTime.parse("2026-01-01T12:00:00"));
        instance.setLastObservedAt(LocalDateTime.parse("2026-01-03T12:00:00"));
        return instance;
    }

    private List<EvidenceItem> evidenceItems(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> evidenceItem(index + 1))
                .toList();
    }

    private EvidenceItem evidenceItem(int dayOfMonth) {
        EvidenceItem item = new EvidenceItem();
        item.setEvidenceChainId("chain-1");
        item.setOccurredAt(LocalDateTime.of(2026, 1, dayOfMonth, 12, 0));
        return item;
    }
}
