package com.ling.linginnerflow.pattern.structure.controller;

import com.ling.linginnerflow.pattern.structure.dto.PatternStructureEvidenceResponse;
import com.ling.linginnerflow.pattern.structure.dto.StructureEligibilityDto;
import com.ling.linginnerflow.pattern.structure.service.PatternStructureEvidenceService;
import com.ling.linginnerflow.pattern.structure.service.PatternStructureEligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pattern-instances")
@RequiredArgsConstructor
public class PatternStructureController {

    private final PatternStructureEligibilityService eligibilityService;
    private final PatternStructureEvidenceService evidenceService;

    @GetMapping("/{patternInstanceId}/structure/eligibility")
    public StructureEligibilityDto eligibility(@PathVariable String patternInstanceId) {
        return eligibilityService.getEligibility(userId(), patternInstanceId);
    }

    @GetMapping("/{patternInstanceId}/structure/evidence")
    public PatternStructureEvidenceResponse evidence(@PathVariable String patternInstanceId,
                                                     @RequestParam(name = "evidence_item_ids", required = false) String evidenceItemIds,
                                                     @RequestParam(name = "include_hidden_counts", defaultValue = "false") boolean includeHiddenCounts) {
        return evidenceService.getEvidence(userId(), patternInstanceId, evidenceItemIds(evidenceItemIds), includeHiddenCounts);
    }

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }

    private List<String> evidenceItemIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }
}
