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
public class SceneDistributionItemDto {
    String sceneId;
    String label;
    String labelSource;
    int evidenceCount;
    double evidenceShare;
    LocalDateTime firstSeenAt;
    LocalDateTime lastSeenAt;
    List<String> representativeEvidenceItemIds;
}
