package com.ling.linginnerflow.pattern.structure.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PatternStructureResponse {
    String apiVersion;
    String patternInstanceId;
    String patternKey;
    String displayName;
    PatternStatus patternStatus;
    StructureEligibilityDto eligibility;
    LocalDateTime generatedAt;
    String generatorVersion;
    List<String> sourceChainIds;
    EvidenceWindowDto evidenceWindow;
    PatternStructureModulesDto modules;
    StructureSafetyDto safety;
    Map<String, Object> experimental;
}
