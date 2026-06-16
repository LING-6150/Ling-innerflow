package com.ling.linginnerflow.pattern.structure.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PatternStructureModulesDto {
    SceneDistributionModuleDto sceneDistribution;
    RelationshipObjectsModuleDto relationshipObjects;
    TemporalStructureModuleDto temporalStructure;
    NeighborPatternsModuleDto neighborPatterns;
}
