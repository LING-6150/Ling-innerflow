package com.ling.linginnerflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.memory.UserMemory;
import com.ling.linginnerflow.memory.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Proactive message service.
 * Runs daily at 9 AM to check user emotion trends and activity,
 * then pushes caring messages to online users via WebSocket.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProactiveMessageService {

    private final WebSocketSessionManager sessionManager;
    private final ChatMessageRepository chatMessageRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final ObjectMapper objectMapper;

    // Triggered at 9:00 AM every day
    @Scheduled(cron = "0 0 9 * * *")
    public void runDailyCheck() {
        log.info("[Proactive] Daily check started");

        List<String> userIds = chatMessageRepository.findDistinctUserIds();
        for (String userId : userIds) {
            try {
                checkAndNotify(userId);
            } catch (Exception e) {
                log.error("[Proactive] Error checking userId={}: {}",
                        userId, e.getMessage());
            }
        }

        log.info("[Proactive] Daily check completed, {} users scanned",
                userIds.size());
    }

    private void checkAndNotify(String userId) {
        // --- Rule 1: 3+ consecutive days with emotion level >= 3 ---
        if (hasHighEmotionStreak(userId, 3)) {
            String message = buildHighEmotionMessage(userId);
            push(userId, "check_in", message);
            log.info("[Proactive] High emotion streak detected: userId={}", userId);
            return;
        }

        // --- Rule 2: No login for 7+ days ---
        if (isInactiveForDays(userId, 7)) {
            String message = buildInactiveMessage(userId);
            push(userId, "check_in", message);
            log.info("[Proactive] Inactive user detected: userId={}", userId);
        }
    }

    /**
     * Returns true if the user has had at least one message with emotionLevel >= 3
     * on each of the past [days] consecutive calendar days.
     */
    private boolean hasHighEmotionStreak(String userId, int days) {
        LocalDateTime since = LocalDate.now()
                .minusDays(days).atStartOfDay();

        List<ChatMessage> messages = chatMessageRepository
                .findUserMessagesSince(userId, since);

        if (messages.isEmpty()) return false;

        // Build a set of dates that had L3+ emotion
        java.util.Set<LocalDate> highDays = new java.util.HashSet<>();
        for (ChatMessage msg : messages) {
            if (msg.getEmotionLevel() != null && msg.getEmotionLevel() >= 3) {
                highDays.add(msg.getCreatedAt().toLocalDate());
            }
        }

        // Check that each of the past [days] calendar days is covered
        for (int i = 1; i <= days; i++) {
            LocalDate day = LocalDate.now().minusDays(i);
            if (!highDays.contains(day)) return false;
        }
        return true;
    }

    /**
     * Returns true if lastActiveAt is older than [days] days, or never recorded.
     */
    private boolean isInactiveForDays(String userId, int days) {
        return userMemoryRepository.findByUserId(userId)
                .map(m -> {
                    if (m.getLastActiveAt() == null) return false;
                    return m.getLastActiveAt()
                            .isBefore(LocalDateTime.now().minusDays(days));
                })
                .orElse(false);
    }

    private String buildHighEmotionMessage(String userId) {
        String name = resolveDisplayName(userId);
        return String.format(
                "Hey %s — I've noticed you've been carrying a lot lately. " +
                "I'm here whenever you want to talk. " +
                "No pressure, just know you don't have to go through this alone.",
                name);
    }

    private String buildInactiveMessage(String userId) {
        String name = resolveDisplayName(userId);
        return String.format(
                "Hi %s — it's been a while. " +
                "Just checking in to see how you're doing. " +
                "I'm still here if you need someone to talk to.",
                name);
    }

    /**
     * Uses coreStruggles context to personalise the greeting name,
     * falls back to "there" when no memory exists.
     */
    private String resolveDisplayName(String userId) {
        return userMemoryRepository.findByUserId(userId)
                .map(UserMemory::getCoreStruggles)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> "there")   // avoid exposing raw DB strings as a name
                .orElse("there");
    }

    /**
     * Pushes a message to the user's active WebSocket session.
     * Silently drops the message if the user is offline.
     */
    private void push(String userId, String type, String content) {
        WebSocketSession session = sessionManager.getSession(userId);
        if (session == null || !session.isOpen()) {
            log.info("[Proactive] User offline, skipping push: userId={}", userId);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "content", content,
                    "proactive", true
            ));
            session.sendMessage(new TextMessage(payload));
            log.info("[Proactive] Message sent: userId={}", userId);
        } catch (Exception e) {
            log.error("[Proactive] Failed to push message to userId={}: {}",
                    userId, e.getMessage());
        }
    }
}
