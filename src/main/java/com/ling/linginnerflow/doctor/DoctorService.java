package com.ling.linginnerflow.doctor;

import com.ling.linginnerflow.agent.tool.EmotionTrendAnalyzer;
import com.ling.linginnerflow.agent.tool.PHQ9ScreeningTool;
import com.ling.linginnerflow.memory.UserMemory;
import com.ling.linginnerflow.memory.UserMemoryRepository;
import com.ling.linginnerflow.rag.HybridSearchService;
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
    private final HybridSearchService hybridSearchService;

    // ── 1. Patient List ──────────────────────────────────────────────

    public List<Map<String, Object>> getPatientList() {
        List<String> userIds = chatMessageRepository.findDistinctUserIds();
        List<Map<String, Object>> result = new ArrayList<>();

        for (String userId : userIds) {
            Map<String, Object> patient = new LinkedHashMap<>();
            patient.put("userId", userId);

            // Return as "L{n}" string so the frontend levelPillClass works directly
            int rawLevel = chatMessageRepository
                    .findFirstByUserIdAndRoleOrderByCreatedAtDesc(userId, "user")
                    .map(m -> m.getEmotionLevel() != null ? m.getEmotionLevel() : 0)
                    .orElse(0);
            patient.put("latestEmotionLevel", rawLevel > 0 ? "L" + rawLevel : null);

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

    // ── 2. Emotion Trend (configurable window) ───────────────────────

    public List<Map<String, Object>> getEmotionTrend(String userId, int days) {
        int window = Math.min(Math.max(days, 1), 90);
        LocalDateTime since = LocalDate.now().minusDays(window - 1).atStartOfDay();
        List<ChatMessage> messages = chatMessageRepository
                .findUserMessagesSince(userId, since);

        Map<LocalDate, List<Integer>> byDay = new LinkedHashMap<>();
        for (int i = window - 1; i >= 0; i--) {
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
                double avg = levels.stream().mapToInt(Integer::intValue).average().orElse(0);
                point.put("level", Math.round(avg * 10.0) / 10.0);
                point.put("sessionCount", levels.size());
            }
            trend.add(point);
        }

        log.info("[Doctor] Emotion trend fetched: userId={}, days={}", userId, window);
        return trend;
    }

    // ── 3. Patient Summary ───────────────────────────────────────────

    public Map<String, Object> getPatientSummary(String userId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userId", userId);

        userMemoryRepository.findByUserId(userId).ifPresentOrElse(mem -> {
            summary.put("emotionPattern", mem.getEmotionPattern());
            summary.put("coreStruggles", mem.getCoreStruggles());
            summary.put("effectiveCoping", mem.getEffectiveCoping());
            summary.put("l4Reflection", mem.getReflection());
            summary.put("lastActiveAt", mem.getLastActiveAt());
            summary.put("wikiConflicts", mem.getConflicts());
        }, () -> {
            summary.put("emotionPattern", null);
            summary.put("coreStruggles", null);
            summary.put("effectiveCoping", null);
            summary.put("l4Reflection", null);
            summary.put("lastActiveAt", null);
            summary.put("wikiConflicts", null);
        });

        // Last Planner routing decision (from most recent assistant message)
        chatMessageRepository.findFirstByUserIdAndRoleOrderByCreatedAtDesc(userId, "assistant")
                .ifPresent(m -> {
                    summary.put("lastPlannerTarget",
                            m.getTargetLevel() != null ? "L" + m.getTargetLevel() : null);
                    summary.put("lastPlannerStrategy", m.getRouteStrategy());
                });

        // PHQ-9: full text + parsed structured fields
        String phq9Raw = phq9ScreeningTool.execute(userId);
        summary.put("phq9Screening", phq9Raw);
        parsePHQ9Into(phq9Raw, summary);

        String trendReport = emotionTrendAnalyzer.execute(userId);
        summary.put("emotionTrendReport", trendReport);

        // CBT Evidence: retrieve relevant knowledge snippets based on patient profile
        summary.put("cbtEvidence", retrieveCbtEvidence(summary));

        log.info("[Doctor] Patient summary built: userId={}", userId);
        return summary;
    }

    private List<String> retrieveCbtEvidence(Map<String, Object> summary) {
        String struggles = (String) summary.get("coreStruggles");
        String pattern   = (String) summary.get("emotionPattern");
        if ((struggles == null || struggles.isBlank()) &&
            (pattern   == null || pattern.isBlank())) {
            return List.of();
        }
        String query = String.join(" ",
                struggles != null ? struggles : "",
                pattern   != null ? pattern   : "").trim();
        try {
            String raw = hybridSearchService.hybridSearch(query);
            if (raw == null || raw.isBlank()) return List.of();
            return Arrays.stream(raw.split("---"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[Doctor] CBT evidence retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ── 3b. Memory-only summary (no LLM calls) ──────────────────────

    public Map<String, Object> getMemorySummary(String userId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userId", userId);
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
        log.info("[Doctor] Memory summary built: userId={}", userId);
        return summary;
    }

    // ── 4. Crisis Heatmap (L3+, last 90 days) ───────────────────────

    public List<Map<String, Object>> getCrisisHeatmap(String userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(90);
        List<ChatMessage> messages = chatMessageRepository.findHeatmapMessages(userId, since);

        // Group levels by "dayIndex:timeSlot" key
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (ChatMessage m : messages) {
            if (m.getCreatedAt() == null || m.getEmotionLevel() == null) continue;
            // DayOfWeek.getValue(): 1=Monday … 7=Sunday → convert to 0-based index
            int dow = m.getCreatedAt().getDayOfWeek().getValue() - 1;
            String slot = toTimeSlot(m.getCreatedAt().getHour());
            grouped.computeIfAbsent(dow + ":" + slot, k -> new ArrayList<>())
                   .add(m.getEmotionLevel());
        }

        String[] days = {"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((key, levels) -> {
            String[] parts = key.split(":");
            int dayIdx = Integer.parseInt(parts[0]);
            double avg = levels.stream().mapToInt(i -> i).average().orElse(0);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("dayOfWeek", days[dayIdx]);
            point.put("timeSlot", parts[1]);
            point.put("avgLevel", Math.round(avg * 10.0) / 10.0);
            point.put("count", levels.size());
            result.add(point);
        });
        log.info("[Doctor] Crisis heatmap: userId={}, cells={}", userId, result.size());
        return result;
    }

    private String toTimeSlot(int hour) {
        if (hour >= 6 && hour < 12) return "morning";
        if (hour >= 12 && hour < 18) return "afternoon";
        if (hour >= 18 && hour < 22) return "evening";
        return "night";
    }

    // ── 5. Crisis Alerts (L5, configurable window) ───────────────────

    public List<Map<String, Object>> getCrisisAlerts(String userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<ChatMessage> alerts = chatMessageRepository.findCrisisAlerts(userId, since);

        return alerts.stream()
                .map(m -> {
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("timestamp", m.getCreatedAt());
                    alert.put("content", m.getContent());
                    alert.put("emotionLevel", m.getEmotionLevel());
                    return alert;
                })
                .collect(Collectors.toList());
    }

    // ── PHQ-9 text parser ────────────────────────────────────────────

    private void parsePHQ9Into(String raw, Map<String, Object> out) {
        if (raw == null || raw.isBlank()) return;

        boolean inIndicators = false;
        boolean inRec = false;
        List<String> indicators = new ArrayList<>();
        StringBuilder rec = new StringBuilder();

        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Score Range:")) {
                out.put("phq9ScoreRange", t.substring("Score Range:".length()).trim());
                inIndicators = false;
                inRec = false;
            } else if (t.startsWith("Severity:")) {
                out.put("phq9Severity", t.substring("Severity:".length()).trim());
                inIndicators = false;
                inRec = false;
            } else if (t.startsWith("Key Indicators:")) {
                inIndicators = true;
                inRec = false;
                String rest = t.substring("Key Indicators:".length()).trim();
                if (!rest.isEmpty()) indicators.add(rest.replaceAll("^[-•·]\\s*", ""));
            } else if (t.startsWith("Recommendation:")) {
                inIndicators = false;
                inRec = true;
                String rest = t.substring("Recommendation:".length()).trim();
                if (!rest.isEmpty()) rec.append(rest).append(" ");
            } else if (t.startsWith("Disclaimer:") || t.startsWith("PHQ-9 SCREENING")) {
                inIndicators = false;
                inRec = false;
            } else if (inIndicators && !t.isEmpty()) {
                indicators.add(t.replaceAll("^[-•·]\\s*", ""));
            } else if (inRec && !t.isEmpty()) {
                rec.append(t).append(" ");
            }
        }

        out.put("phq9KeyIndicators", String.join("\n", indicators));
        out.put("phq9Recommendation", rec.toString().trim());
    }
}
