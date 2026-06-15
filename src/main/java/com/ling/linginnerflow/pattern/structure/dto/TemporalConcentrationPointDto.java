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
public class TemporalConcentrationPointDto {
    LocalDateTime bucketStart;
    LocalDateTime bucketEnd;
    String granularity;
    int evidenceCount;
    List<String> representativeEvidenceItemIds;
}
