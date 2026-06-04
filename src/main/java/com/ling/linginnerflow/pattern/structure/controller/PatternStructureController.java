package com.ling.linginnerflow.pattern.structure.controller;

import com.ling.linginnerflow.pattern.structure.dto.StructureEligibilityDto;
import com.ling.linginnerflow.pattern.structure.service.PatternStructureEligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pattern-instances")
@RequiredArgsConstructor
public class PatternStructureController {

    private final PatternStructureEligibilityService eligibilityService;

    @GetMapping("/{patternInstanceId}/structure/eligibility")
    public StructureEligibilityDto eligibility(@PathVariable String patternInstanceId) {
        return eligibilityService.getEligibility(userId(), patternInstanceId);
    }

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }
}
