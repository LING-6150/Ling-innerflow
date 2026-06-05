package com.ling.linginnerflow.pattern.structure.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvidenceItemDto {
    String id;
    String sourceType;
    String sourceRef;
    LocalDateTime occurredAt;
    String excerpt;
    EvidenceExcerptVisibility excerptVisibility;
    @JsonProperty("is_verbatim")
    boolean verbatim;
    String interpretation;
    String deepLink;
}
