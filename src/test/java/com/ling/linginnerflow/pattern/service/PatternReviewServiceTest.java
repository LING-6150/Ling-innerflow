package com.ling.linginnerflow.pattern.service;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import com.ling.linginnerflow.pattern.repo.PatternInstanceRepository;
import com.ling.linginnerflow.pattern.safety.LanguageFirewall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatternReviewServiceTest {

    @Mock private PatternInstanceRepository patternInstanceRepository;
    @Mock private LanguageFirewall languageFirewall;

    private PatternReviewService service;

    @BeforeEach
    void setUp() {
        service = new PatternReviewService(patternInstanceRepository, languageFirewall);
        when(patternInstanceRepository.save(any(PatternInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void review_defer_setsDeferredStatusAndReviewTimestamp() {
        PatternInstance instance = instance(PatternStatus.candidate);
        instance.setHidden(true);
        when(patternInstanceRepository.findById("pattern-1")).thenReturn(Optional.of(instance));

        PatternInstance result = service.review("user-1", "pattern-1", "defer", null, "not now");

        assertThat(result.getStatus()).isEqualTo(PatternStatus.deferred);
        assertThat(result.getUserNote()).isEqualTo("not now");
        assertThat(result.getLastReviewedAt()).isNotNull();
        assertThat(result.isHidden()).isFalse();
        verify(patternInstanceRepository).save(instance);
    }

    private PatternInstance instance(PatternStatus status) {
        PatternInstance instance = new PatternInstance();
        instance.setId("pattern-1");
        instance.setUserId("user-1");
        instance.setPatternKey("pattern_key");
        instance.setDomain(Domain.work);
        instance.setStatus(status);
        return instance;
    }
}
