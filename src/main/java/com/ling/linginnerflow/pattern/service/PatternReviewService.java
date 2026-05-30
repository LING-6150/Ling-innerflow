package com.ling.linginnerflow.pattern.service;

import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.safety.LanguageFirewall;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PatternReviewService {

    private final PatternInstanceRepository patternInstanceRepository;
    private final LanguageFirewall languageFirewall;

    @Transactional
    public PatternInstance review(String userId, String instanceId, String action,
                                  String summary, String userNote) {
        PatternInstance instance = getOwned(userId, instanceId);
        switch (action) {
            case "confirm" -> instance.setStatus(PatternStatus.confirmed);
            case "partial" -> {
                instance.setStatus(PatternStatus.partially_confirmed);
                if (summary != null && !summary.isBlank()) {
                    instance.setPersonalizedSummary(languageFirewall.enforce(summary));
                }
            }
            case "reject" -> instance.setStatus(PatternStatus.rejected);
            case "defer" -> {
            }
            case "archive" -> instance.setStatus(PatternStatus.archived);
            default -> throw new IllegalArgumentException("Unknown review action: " + action);
        }
        if (userNote != null) {
            instance.setUserNote(userNote);
        }
        instance.setLastReviewedAt(LocalDateTime.now());
        instance.setHidden(false);
        return patternInstanceRepository.save(instance);
    }

    @Transactional
    public PatternInstance edit(String userId, String instanceId, String summary, String userNote) {
        PatternInstance instance = getOwned(userId, instanceId);
        if (summary != null) {
            instance.setPersonalizedSummary(languageFirewall.enforce(summary));
        }
        if (userNote != null) {
            instance.setUserNote(userNote);
        }
        return patternInstanceRepository.save(instance);
    }

    private PatternInstance getOwned(String userId, String instanceId) {
        PatternInstance instance = patternInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Pattern instance not found"));
        if (!userId.equals(instance.getUserId())) {
            throw new IllegalArgumentException("Pattern instance not found");
        }
        return instance;
    }
}
