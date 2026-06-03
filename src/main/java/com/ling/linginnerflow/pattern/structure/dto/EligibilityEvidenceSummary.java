package com.ling.linginnerflow.pattern.structure.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EligibilityEvidenceSummary {
    int evidenceCount;
    int minimumRequiredCount;
    List<String> sourceChainIds;
    LocalDateTime firstObservedAt;
    LocalDateTime lastObservedAt;
}
