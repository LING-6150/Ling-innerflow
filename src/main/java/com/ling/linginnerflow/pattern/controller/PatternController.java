package com.ling.linginnerflow.pattern.controller;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.EvidenceItemRepository;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.service.PatternDiscoveryService;
import com.ling.linginnerflow.pattern.service.PatternReviewService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/pattern")
@RequiredArgsConstructor
public class PatternController {

    private final PatternInstanceRepository patternInstanceRepository;
    private final EvidenceItemRepository evidenceItemRepository;
    private final PatternDiscoveryService patternDiscoveryService;
    private final PatternReviewService patternReviewService;

    @Value("${pattern.refresh.dev-endpoint-enabled:false}")
    private boolean refreshDevEndpointEnabled;

    @GetMapping("/instances")
    public List<PatternInstance> list(@RequestParam(required = false) String domain,
                                      @RequestParam(required = false) String status) {
        Domain parsedDomain = domain == null || domain.isBlank() ? null : Domain.valueOf(domain);
        PatternStatus parsedStatus = status == null || status.isBlank() ? null : PatternStatus.valueOf(status);
        return patternInstanceRepository.findByUserId(userId()).stream()
                .filter(instance -> !instance.isHidden())
                .filter(instance -> parsedDomain == null || instance.getDomain() == parsedDomain)
                .filter(instance -> parsedStatus == null || instance.getStatus() == parsedStatus)
                .sorted(Comparator.comparing(PatternInstance::getLastObservedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @GetMapping("/instances/{id}/evidence")
    public List<EvidenceItem> evidence(@PathVariable String id) {
        PatternInstance instance = owned(id);
        if (instance.getEvidenceChainId() == null) {
            return List.of();
        }
        return evidenceItemRepository.findByEvidenceChainId(instance.getEvidenceChainId()).stream()
                .sorted(Comparator.comparing(EvidenceItem::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @PostMapping("/instances/{id}/review")
    public PatternInstance review(@PathVariable String id, @RequestBody ReviewRequest request) {
        return patternReviewService.review(userId(), id, request.getAction(),
                request.getSummary(), request.getUserNote());
    }

    @PatchMapping("/instances/{id}")
    public PatternInstance edit(@PathVariable String id, @RequestBody EditRequest request) {
        return patternReviewService.edit(userId(), id, request.getSummary(), request.getUserNote());
    }

    @PostMapping("/refresh")
    public ResponseEntity<PatternDiscoveryService.RefreshResult> refresh() {
        if (!refreshDevEndpointEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(patternDiscoveryService.refresh(userId()));
    }

    private PatternInstance owned(String id) {
        PatternInstance instance = patternInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pattern instance not found"));
        if (!userId().equals(instance.getUserId())) {
            throw new IllegalArgumentException("Pattern instance not found");
        }
        return instance;
    }

    private String userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getPrincipal();
    }

    @Data
    public static class ReviewRequest {
        private String action;
        private String summary;
        private String userNote;
    }

    @Data
    public static class EditRequest {
        private String summary;
        private String userNote;
    }
}
