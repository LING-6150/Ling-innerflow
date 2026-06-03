package com.ling.linginnerflow.pattern.structure.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EligibilityCooldownSummary {
    LocalDateTime rejectedAt;
    LocalDateTime cooldownExpiresAt;
    boolean canReopen;
}
