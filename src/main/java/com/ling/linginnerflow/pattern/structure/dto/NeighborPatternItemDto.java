package com.ling.linginnerflow.pattern.structure.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class NeighborPatternItemDto {
    String patternInstanceId;
    String patternKey;
    String displayName;
    PatternStatus patternStatus;
    int cooccurrenceCount;
    int sharedSourceCount;
    LocalDateTime firstCooccurredAt;
    LocalDateTime lastCooccurredAt;
    String connectorLabel;
    List<NeighborEvidencePairDto> representativePairs;
}
