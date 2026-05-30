package com.ling.linginnerflow.pattern.schedule;

import com.ling.linginnerflow.pattern.service.PatternDiscoveryService;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternRefreshScheduler {

    private final ChatMessageRepository chatMessageRepository;
    private final PatternDiscoveryService patternDiscoveryService;

    @Scheduled(cron = "${pattern.refresh.cron:0 30 3 * * *}")
    public void refreshAllUsers() {
        for (String userId : chatMessageRepository.findDistinctUserIds()) {
            try {
                patternDiscoveryService.refresh(userId);
            } catch (Exception e) {
                log.warn("[PatternRefresh] user failed userId={}, error={}", userId, e.getMessage());
            }
        }
    }
}
