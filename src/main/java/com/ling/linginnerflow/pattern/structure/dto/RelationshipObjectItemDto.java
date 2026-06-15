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
public class RelationshipObjectItemDto {
    String objectId;
    String displayLabel;
    String objectType;
    String labelSource;
    int evidenceCount;
    LocalDateTime firstSeenAt;
    LocalDateTime lastSeenAt;
    List<String> representativeEvidenceItemIds;
    String privacyLevel;
}
