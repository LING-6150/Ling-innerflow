package com.ling.linginnerflow.doctor;

import com.ling.linginnerflow.agent.tool.EmotionTrendAnalyzer;
import com.ling.linginnerflow.agent.tool.PHQ9ScreeningTool;
import com.ling.linginnerflow.memory.UserMemory;
import com.ling.linginnerflow.memory.UserMemoryRepository;
import com.ling.linginnerflow.websocket.ChatMessage;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserMemoryRepository userMemoryRepository;
    private final PHQ9ScreeningTool phq9ScreeningTool;
    private final EmotionTrendAnalyzer emotionTrendAnalyzer;

    // ── 1. Patient List ──────────────────────────────────────────────

    public List<Map<String, Object>> getPatientList() {
        List<String> userIds = chatMessageRepository.findDistinctUserIds();
        List<Map<String, Object>> result = new ArrayList<>();

        for (String userId : userIds) {
            Map<String, Object> patient = new LinkedHashMap<>();
            patient.put("userId", userId);

            // Latest emotion level from most recent user message
            int latestLevel = chatMessageRepository
                    .findFirstByUserIdAndRoleOrderByCreatedAtDesc(userId, "user")
                    .map(m -> m.getEmotionLevel() != null ? m.getEmotionLevel() : 0)
                    .orElse(0);
            patient.put("latestEmotionLevel", latestLevel);

            // lastActiveAt from long-term memory
            LocalDateTime lastActive = userMemoryRepository
                    .findByUserId(userId)
                    .map(UserMemory::getLastActiveAt)
                    .orElse(null);
            patient.put("lastActiveAt", lastActive);

            result.add(patient);
        }

        log.info("[Doctor] Patient list fetched: {} users", result.size());
        return result;
    }

    // ── 2. 7-Day Emotion Trend ───────────────────────────────────────

    public List<Map<String, Object>> getEmotionTrend(String userId) {
        LocalDateTime since = LocalDate.now().minusDays(6).atStartOfDay();
        List<ChatMessage> messages = chatMessageRepository
                .findUserMessagesSince(userId, since);

        // Group by calendar date, compute average emotionLevel per day
        Map<LocalDate, List<Integer>> byDay = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            byDay.put(LocalDate.now().minusDays(i), new ArrayList<>());
        }
        for (ChatMessage m : messages) {
            if (m.getEmotionLevel() != null && m.getEmotionLevel() > 0) {
                LocalDate day = m.getCreatedAt().toLocalDate();
                byDay.computeIfPresent(day, (k, v) -> {
                    v.add(m.getEmotionLevel());
                    return v;
                });
            }
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Integer>> entry : byDay.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey().toString());
            List<Integer> levels = entry.getValue();
            if (levels.isEmpty()) {
                point.put("level", null);
                point.put("sessionCount", 0);
            } else {
                double avg = levels.stream()
                        .mapToInt(Integer::intValue).average().orElse(0);
                point.put("level", Math.round(avg * 10.0) / 10.0);
                point.put("sessionCount", levels.size());
            }
            trend.add(point);
        }

        log.info("[Doctor] Emotion trend fetched: userId={}", userId);
        return trend;
    }

    // ── 3. Patient Summary ───────────────────────────────────────────

    public Map<String, Object> getPatientSummary(String userId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userId", userId);

        // L4 Reflection + long-term memory from MySQL
        userMemoryRepository.findByUserId(userId).ifPresentOrElse(mem -> {
            summary.put("emotionPattern", mem.getEmotionPattern());
            summary.put("coreStruggles", mem.getCoreStruggles());
            summary.put("effectiveCoping", mem.getEffectiveCoping());
            summary.put("l4Reflection", mem.getReflection());
            summary.put("lastActiveAt", mem.getLastActiveAt());
        }, () -> {
            summary.put("emotionPattern", null);
            summary.put("coreStruggles", null);
            summary.put("effectiveCoping", null);
            summary.put("l4Reflection", null);
            summary.put("lastActiveAt", null);
        });

        // PHQ-9 screening estimate via LLM
        String phq9Result = phq9ScreeningTool.execute(userId);
        summary.put("phq9Screening", phq9Result);

        // Structured emotion trend summary via EmotionTrendAnalyzer
        String trendReport = emotionTrendAnalyzer.execute(userId);
        summary.put("emotionTrendReport", trendReport);

        log.info("[Doctor] Patient summary built: userId={}", userId);
        return summary;
    }

    // ── 4. Crisis Alerts (L5, last 30 days) ─────────────────────────

    public List<Map<String, Object>> getCrisisAlerts(String userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<ChatMessage> alerts = chatMessageRepository
                .findCrisisAlerts(userId, since);

        List<Map<String, Object>> result = alerts.stream()
                .map(m -> {
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("timestamp", m.getCreatedAt());
                    alert.put("content", m.getContent());
                    alert.put("emotionLevel", m.getEmotionLevel());
                    return alert;
                })
                .collect(Collectors.toList());

        log.info("[Doctor] Crisis alerts fetched: userId={}, count={}",
                userId, result.size());
        return result;
    }
}
