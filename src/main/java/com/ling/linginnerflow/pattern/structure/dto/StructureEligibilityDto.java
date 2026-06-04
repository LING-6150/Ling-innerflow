package com.ling.linginnerflow.pattern.structure.dto;

import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StructureEligibilityDto {
    EligibilityState state;
    boolean canShowStructure;
    PatternStatus patternStatus;
    TrustTier trustTier;
    EligibilityReasonCode reasonCode;
    String displayMessage;
    List<EligibilityAction> requiredActions;
    EligibilityEvidenceSummary evidenceSummary;
    EligibilitySafetySummary safetySummary;
    EligibilityCooldownSummary cooldownSummary;
}
