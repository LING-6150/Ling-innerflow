package com.ling.linginnerflow.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 记忆服务
 * 管理三层记忆：短期（Redis）+ 长期（MySQL）+ 摘要压缩
 *
 * 工作流程：
 * 1. 用户发消息 → 读取短期记忆（Redis）+ 长期记忆（MySQL）
 * 2. 把历史上下文注入Prompt
 * 3. LLM回复后 → 把新对话存入短期记忆
 * 4. 对话超过10轮 → 触发摘要压缩
 * 5. 会话结束 → 提取关键信息写入长期记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    private final StringRedisTemplate redisTemplate;
    private final UserMemoryRepository userMemoryRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final MemoryCompressionService compressionService;

    // 获取用户人格，默认WARM
    public Persona getPersona(String userId) {
        return userMemoryRepository.findByUserId(userId)
                .map(m -> {
                    try {
                        return Persona.valueOf(m.getPersona());
                    } catch (Exception e) {
                        return Persona.WARM;
                    }
                })
                .orElse(Persona.WARM);
    }

    // 设置用户人格
    public void setPersona(String userId, Persona persona) {
        UserMemory memory = userMemoryRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserMemory m = new UserMemory();
                    m.setUserId(userId);
                    return m;
                });
        memory.setPersona(persona.name());
        userMemoryRepository.save(memory);
    }

    private static final String SHORT_MEMORY_PREFIX = "memory:short:";
    private static final long SHORT_MEMORY_TTL = 30;

    // Configurable threshold: how many rounds before triggering compression
    @Value("${memory.compression.threshold:10}")
    private int compressionThreshold;

    // ==================== 短期记忆 ====================

    /**
     * 添加一条消息到短期记忆
     */
    public void addMessage(String userId, String role, String content) {
        try {
            String key = SHORT_MEMORY_PREFIX + userId;
            List<ConversationMessage> history = getShortMemory(userId);

            history.add(new ConversationMessage(
                    role, content, System.currentTimeMillis()));

            // 存回Redis，刷新TTL
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(history),
                    SHORT_MEMORY_TTL,
                    TimeUnit.MINUTES
            );

            log.info("短期记忆更新: userId={}, 当前轮数={}",
                    userId, history.size() / 2);

            // When history exceeds the threshold, trigger async sliding-window compression.
            // Pass a snapshot so the async task works on stable data.
            if (history.size() >= compressionThreshold * 2) {
                compressionService.compressAsync(userId, new ArrayList<>(history));
            }

        } catch (Exception e) {
            log.error("短期记忆写入失败: {}", e.getMessage());
        }
    }

    /**
     * 读取短期记忆
     */
    public List<ConversationMessage> getShortMemory(String userId) {
        try {
            String key = SHORT_MEMORY_PREFIX + userId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return new ArrayList<>();
            return objectMapper.readValue(json,
                    new TypeReference<List<ConversationMessage>>() {});
        } catch (Exception e) {
            log.error("短期记忆读取失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 清除短期记忆（会话结束时调用）
     */
    public void clearShortMemory(String userId) {
        redisTemplate.delete(SHORT_MEMORY_PREFIX + userId);
        log.info("短期记忆已清除: userId={}", userId);
    }

    // ==================== 长期记忆 ====================

    /**
     * 读取长期记忆
     */
    public UserMemory getLongMemory(String userId) {
        return userMemoryRepository.findByUserId(userId)
                .orElse(null);
    }

    /**
     * Wiki编译 — 读取现有档案 + 新对话 → AI决定合并策略
     * P0-2: 首次对话（Wiki为空）走简单提取，避免AI对着空Wiki推理
     * 后续对话走 merge prompt，AI看到旧档案后决定增/改/保留
     */
    public void updateLongMemory(String userId,
                                 List<ConversationMessage> history) {
        if (history.isEmpty()) return;

        try {
            UserMemory memory = userMemoryRepository
                    .findByUserId(userId)
                    .orElse(new UserMemory());
            memory.setUserId(userId);

            boolean isFirstSession = memory.getEmotionPattern() == null
                    && memory.getCoreStruggles() == null
                    && memory.getTriggers() == null;

            String prompt = isFirstSession
                    ? buildFirstExtractPrompt(history)
                    : buildMergePrompt(memory, history);

            String raw = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();
            raw = raw.replaceAll("(?s)```json\\s*|```\\s*", "").trim();

            WikiMergeResult result = objectMapper.readValue(raw, WikiMergeResult.class);
            applyMergeResult(memory, result);

            if (result.getChangeLogEntry() != null) {
                appendChangeLog(memory, result.getChangeLogEntry());
            }

            userMemoryRepository.save(memory);
            log.info("[Wiki] 编译完成: userId={}", userId);

            generateReflection(userId);

        } catch (Exception e) {
            log.error("[Wiki] 编译失败: {}", e.getMessage());
        }
    }

    private void applyMergeResult(UserMemory memory, WikiMergeResult result) {
        if (result.getEmotionPattern() != null)
            memory.setEmotionPattern(result.getEmotionPattern());
        if (result.getCoreStruggles() != null)
            memory.setCoreStruggles(result.getCoreStruggles());
        if (result.getEffectiveCoping() != null)
            memory.setEffectiveCoping(result.getEffectiveCoping());
        if (result.getLanguageStyle() != null)
            memory.setLanguageStyle(result.getLanguageStyle());
        if (result.getTriggerUpdates() != null)
            mergeTriggers(memory, result.getTriggerUpdates());
        if (result.getNewProgressNote() != null)
            appendProgressNote(memory, result.getNewProgressNote());
        // P2-8: persist any AI-detected contradictions
        if (result.getConflicts() != null && !result.getConflicts().isEmpty())
            memory.setConflicts(toJson(result.getConflicts()));
    }

    private void mergeTriggers(UserMemory memory,
                                List<WikiMergeResult.TriggerAction> updates) {
        List<WikiObservation> existing = parseTriggers(memory.getTriggers());
        String today = LocalDate.now().toString();

        for (WikiMergeResult.TriggerAction u : updates) {
            switch (u.getAction()) {
                case "new" -> {
                    String conf = u.getConfidence() != null ? u.getConfidence() : "medium";
                    WikiObservation o = new WikiObservation(u.getObservation(), 1, today, today, conf, 0.0);
                    o.setScore(computeScore(1, today, conf));
                    existing.add(o);
                }
                case "increment" -> existing.stream()
                        .filter(o -> o.getObservation().equalsIgnoreCase(u.getObservation()))
                        .findFirst()
                        .ifPresent(o -> {
                            o.setCount(o.getCount() + 1);
                            o.setLastSeen(today);
                            o.setScore(computeScore(o.getCount(), today, o.getConfidence()));
                        });
                case "remove" -> existing.removeIf(
                        o -> o.getObservation().equalsIgnoreCase(u.getObservation()));
            }
        }
        memory.setTriggers(toJson(existing));
    }

    private void appendProgressNote(UserMemory memory, String note) {
        List<Map<String, String>> notes = parseProgressNotes(memory.getProgressNotes());
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("date", LocalDate.now().toString());
        entry.put("note", note);
        notes.add(entry);
        if (notes.size() > 20) notes = notes.subList(notes.size() - 20, notes.size());
        memory.setProgressNotes(toJson(notes));
    }

    private void appendChangeLog(UserMemory memory, String entry) {
        List<Map<String, String>> log = parseChangeLog(memory.getWikiChangeLog());
        Map<String, String> item = new LinkedHashMap<>();
        item.put("date", LocalDate.now().toString());
        item.put("entry", entry);
        log.add(0, item); // newest first
        if (log.size() > 50) log = log.subList(0, 50);
        memory.setWikiChangeLog(toJson(log));
    }

    private List<Map<String, String>> parseChangeLog(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        // Migrate from old plain-text "YYYY-MM-DD: entry\n" format
        if (!json.trim().startsWith("[")) {
            List<Map<String, String>> migrated = new ArrayList<>();
            for (String line : json.split("\n")) {
                line = line.trim();
                if (line.isBlank()) continue;
                Map<String, String> m = new LinkedHashMap<>();
                int idx = line.indexOf(": ");
                if (idx > 0) {
                    m.put("date", line.substring(0, idx));
                    m.put("entry", line.substring(idx + 2));
                } else {
                    m.put("date", "—");
                    m.put("entry", line);
                }
                migrated.add(m);
            }
            return migrated;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // ==================== Prompt构建 ====================

    /**
     * 构建带历史上下文的Prompt前缀
     * 供各节点调用，把记忆注入Prompt
     */
    public String buildContextPrompt(String userId) {
        StringBuilder context = new StringBuilder();

        UserMemory longMemory = getLongMemory(userId);
        if (longMemory != null) {
            context.append("=== USER WIKI ===\n");

            if (longMemory.getCoreStruggles() != null)
                context.append("Core struggles: ").append(longMemory.getCoreStruggles()).append("\n");

            if (longMemory.getEmotionPattern() != null)
                context.append("Emotion pattern: ").append(longMemory.getEmotionPattern()).append("\n");

            // triggers：score 降序，过滤低于 0.3 的陈旧/低置信观察（P1-5）
            List<WikiObservation> triggers = parseTriggers(longMemory.getTriggers());
            List<WikiObservation> activeTriggers = triggers.stream()
                    .filter(t -> t.getScore() >= 0.3)
                    .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                    .toList();
            if (!activeTriggers.isEmpty()) {
                String triggerText = activeTriggers.stream()
                        .map(t -> t.getObservation() + " (×" + t.getCount() + ", score=" + t.getScore() + ")")
                        .collect(Collectors.joining(", "));
                context.append("Known triggers: ").append(triggerText).append("\n");
            }

            if (longMemory.getEffectiveCoping() != null)
                context.append("What helps: ").append(longMemory.getEffectiveCoping()).append("\n");

            // progressNotes：只显示最近3条，避免context过长
            List<Map<String, String>> notes = parseProgressNotes(longMemory.getProgressNotes());
            if (!notes.isEmpty()) {
                int start = Math.max(0, notes.size() - 3);
                String notesText = notes.subList(start, notes.size()).stream()
                        .map(n -> n.get("date") + ": " + n.get("note"))
                        .collect(Collectors.joining(" | "));
                context.append("Progress: ").append(notesText).append("\n");
            }

            if (longMemory.getLanguageStyle() != null)
                context.append("How they speak: ").append(longMemory.getLanguageStyle()).append("\n");

            if (longMemory.getReflection() != null)
                context.append("Deep insight: ").append(longMemory.getReflection()).append("\n");

            context.append("=================\n\n");
        }

        // 最近5轮对话
        List<ConversationMessage> history = getShortMemory(userId);
        if (!history.isEmpty()) {
            context.append("[Recent Conversation]\n");
            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                ConversationMessage msg = history.get(i);
                String roleLabel = "user".equals(msg.getRole()) ? "User" : "AI";
                context.append(roleLabel).append(": ")
                        .append(msg.getContent()).append("\n");
            }
            context.append("\n");
        }

        return context.toString();
    }

    /** P0-2: 首次对话专用 — 纯提取，无旧档案干扰，格式与 WikiMergeResult 一致 */
    private String buildFirstExtractPrompt(List<ConversationMessage> history) {
        StringBuilder conv = new StringBuilder();
        history.forEach(m -> conv.append(m.getRole()).append(": ").append(m.getContent()).append("\n"));
        return """
            You are building an initial psychological profile from a first therapy-adjacent conversation.
            Be specific and evidence-based — only record what is clearly evidenced.
            Return ONLY valid JSON, no markdown:
            {
              "emotionPattern": "specific patterns observed, or null",
              "coreStruggles": "specific stressors/pain points, or null",
              "effectiveCoping": "what seemed to help in this session, or null",
              "languageStyle": "how this person expresses inner states, or null",
              "triggerUpdates": [
                {"observation": "specific trigger", "action": "new", "confidence": "high|medium|low"}
              ],
              "conflicts": [],
              "newProgressNote": "any notable first-session insight, or null",
              "changeLogEntry": "Initial profile created from first session"
            }
            Conversation:
            %s
            """.formatted(conv);
    }

    /**
     * Merge prompt：把现有Wiki + 新对话给AI，让AI决定更新策略
     * 核心区别：AI看到旧档案，判断"增/改/保留/冲突"而不是直接覆盖
     */
    private String buildMergePrompt(UserMemory existing,
                                     List<ConversationMessage> history) {
        // 序列化现有Wiki
        StringBuilder wiki = new StringBuilder();
        if (existing.getEmotionPattern() != null)
            wiki.append("emotionPattern: ").append(existing.getEmotionPattern()).append("\n");
        if (existing.getCoreStruggles() != null)
            wiki.append("coreStruggles: ").append(existing.getCoreStruggles()).append("\n");
        if (existing.getEffectiveCoping() != null)
            wiki.append("effectiveCoping: ").append(existing.getEffectiveCoping()).append("\n");
        if (existing.getLanguageStyle() != null)
            wiki.append("languageStyle: ").append(existing.getLanguageStyle()).append("\n");

        List<WikiObservation> triggers = parseTriggers(existing.getTriggers());
        if (!triggers.isEmpty()) {
            String t = triggers.stream()
                    .map(o -> o.getObservation() + "(×" + o.getCount() + ")")
                    .collect(Collectors.joining(", "));
            wiki.append("triggers: ").append(t).append("\n");
        }

        List<Map<String, String>> notes = parseProgressNotes(existing.getProgressNotes());
        if (!notes.isEmpty()) {
            String n = notes.stream().map(m -> m.get("note")).collect(Collectors.joining("; "));
            wiki.append("progressNotes: ").append(n).append("\n");
        }

        // 序列化新对话
        StringBuilder conv = new StringBuilder();
        history.forEach(msg -> conv.append(msg.getRole())
                .append(": ").append(msg.getContent()).append("\n"));

        boolean firstSession = wiki.isEmpty();

        return """
            You are a clinical psychologist maintaining a structured patient wiki.

            %s

            New conversation:
            %s

            Rules:
            - Only record what is evidenced in THIS conversation
            - For triggers: "increment" if same trigger recurred, "new" if novel, "remove" only if explicitly resolved
            - For text fields: return updated value if meaningfully changed; null if no change needed
            - Be specific, not generic. Avoid boilerplate phrases.
            - conflicts: list only genuine contradictions between the existing wiki and THIS session
              (e.g. wiki says "avoids exercise" but this session shows active jogging habit).
              resolution must be one of: "updated" (wiki changed), "kept" (wiki unchanged, one-off), "both noted"
            - changeLogEntry: 1-2 sentences describing what changed this session

            Return ONLY valid JSON, no markdown:
            {
              "emotionPattern": "updated or null",
              "coreStruggles": "updated or null",
              "effectiveCoping": "updated or null",
              "languageStyle": "updated or null",
              "triggerUpdates": [
                {"observation": "specific trigger", "action": "new|increment|remove", "confidence": "high|medium|low"}
              ],
              "conflicts": [
                {"field": "emotionPattern|coreStruggles|effectiveCoping|triggers", "existing": "...", "observed": "...", "resolution": "updated|kept|both noted"}
              ],
              "newProgressNote": "specific growth observation, or null",
              "changeLogEntry": "brief summary"
            }
            """.formatted(
                firstSession ? "EXISTING WIKI: (empty — first session)" : "EXISTING WIKI:\n" + wiki,
                conv
        );
    }

    /**
     * score = countScore × recencyScore
     * countScore  = min(1.0, count / 5.0)          — 5次观察达满分
     * recencyScore = exp(−daysSince / 90)           — 90天半衰期
     * confirmed   = 1.0（用户亲自确认，不衰减）
     */
    private double computeScore(int count, String lastSeen, String confidence) {
        if ("confirmed".equals(confidence)) return 1.0;
        double countScore = Math.min(1.0, count / 5.0);
        double recencyScore = 1.0;
        if (lastSeen != null) {
            try {
                long days = ChronoUnit.DAYS.between(LocalDate.parse(lastSeen), LocalDate.now());
                recencyScore = Math.exp(-days / 90.0);
            } catch (Exception ignored) {}
        }
        return Math.round(countScore * recencyScore * 100.0) / 100.0;
    }

    private List<WikiObservation> parseTriggers(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            List<WikiObservation> list = objectMapper.readValue(json,
                    new TypeReference<List<WikiObservation>>() {});
            // 每次加载时实时重算 score — 时间衰减无需定时任务
            list.forEach(o -> o.setScore(computeScore(o.getCount(), o.getLastSeen(), o.getConfidence())));
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, String>> parseProgressNotes(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    // ==================== 用户反馈闭环 ====================

    /**
     * 返回当前用户的 Wiki 摘要（供前端展示 + 纠错）
     */
    public WikiSummaryDto getWikiSummary(String userId) {
        UserMemory mem = getLongMemory(userId);
        WikiSummaryDto dto = new WikiSummaryDto();
        if (mem == null) return dto;

        dto.setCoreStruggles(mem.getCoreStruggles());
        dto.setEmotionPattern(mem.getEmotionPattern());
        dto.setEffectiveCoping(mem.getEffectiveCoping());
        dto.setLanguageStyle(mem.getLanguageStyle());
        dto.setReflection(mem.getReflection());

        List<WikiObservation> triggers = parseTriggers(mem.getTriggers())
                .stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .toList();
        dto.setTriggers(triggers);

        List<Map<String, String>> notes = parseProgressNotes(mem.getProgressNotes());
        int start = Math.max(0, notes.size() - 5);
        dto.setProgressNotes(notes.subList(start, notes.size()));

        // P2-8: parse conflicts JSON
        if (mem.getConflicts() != null && !mem.getConflicts().isBlank()) {
            try {
                List<Map<String, String>> conflicts = objectMapper.readValue(
                        mem.getConflicts(), new TypeReference<>() {});
                dto.setConflicts(conflicts);
            } catch (Exception ignored) {}
        }

        // P1-4: structured changeLog (most recent 10 entries)
        List<Map<String, String>> cl = parseChangeLog(mem.getWikiChangeLog());
        dto.setChangeLog(cl.subList(0, Math.min(10, cl.size())));

        dto.setHasData(mem.getEmotionPattern() != null
                || mem.getCoreStruggles() != null
                || !triggers.isEmpty());
        return dto;
    }

    /**
     * 用户主动纠错 — 直接修改 Wiki，用户纠错视为最高置信度
     */
    public void applyUserCorrection(String userId, String field,
                                     String observationText, String correctionType,
                                     String correctionText) {
        UserMemory mem = userMemoryRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("No wiki found for user: " + userId));

        switch (field) {
            case "triggers" -> applyTriggerCorrection(mem, observationText, correctionType, correctionText);
            case "emotionPattern" -> { if ("correct".equals(correctionType)) mem.setEmotionPattern(correctionText); }
            case "coreStruggles"  -> { if ("correct".equals(correctionType)) mem.setCoreStruggles(correctionText); }
            case "effectiveCoping"-> { if ("correct".equals(correctionType)) mem.setEffectiveCoping(correctionText); }
            case "languageStyle"  -> { if ("correct".equals(correctionType)) mem.setLanguageStyle(correctionText); }
        }

        recordUserCorrection(mem, field, observationText, correctionType, correctionText);
        appendChangeLog(mem, "User correction on [" + field + "]: " + correctionType);
        userMemoryRepository.save(mem);
        log.info("[Wiki] 用户纠错: userId={}, field={}, type={}", userId, field, correctionType);
    }

    private void applyTriggerCorrection(UserMemory mem, String observationText,
                                         String correctionType, String correctionText) {
        List<WikiObservation> triggers = parseTriggers(mem.getTriggers());
        String today = LocalDate.now().toString();
        switch (correctionType) {
            case "delete"  -> triggers.removeIf(t -> t.getObservation().equalsIgnoreCase(observationText));
            case "correct" -> triggers.stream()
                    .filter(t -> t.getObservation().equalsIgnoreCase(observationText))
                    .findFirst()
                    .ifPresent(t -> t.setObservation(correctionText));
            case "add"     -> triggers.add(new WikiObservation(correctionText, 1, today, today, "confirmed", 1.0));
        }
        mem.setTriggers(toJson(triggers));
    }

    /**
     * 用户主动清除所有记忆（CCPA / 隐私权）
     */
    public void clearUserWiki(String userId) {
        userMemoryRepository.findByUserId(userId).ifPresent(mem -> {
            mem.setEmotionPattern(null);
            mem.setCoreStruggles(null);
            mem.setEffectiveCoping(null);
            mem.setTriggers(null);
            mem.setProgressNotes(null);
            mem.setLanguageStyle(null);
            mem.setReflection(null);
            mem.setConflicts(null);
            mem.setWikiChangeLog(null);
            mem.setUserCorrections(null);
            mem.setConversationSummary(null);
            userMemoryRepository.save(mem);
        });
        log.info("[Wiki] 用户清除记忆: userId={}", userId);
    }

    private void recordUserCorrection(UserMemory mem, String field, String observationText,
                                       String correctionType, String correctionText) {
        try {
            List<Map<String, String>> corrections = mem.getUserCorrections() != null
                    ? objectMapper.readValue(mem.getUserCorrections(), new TypeReference<>() {})
                    : new ArrayList<>();
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("date", LocalDate.now().toString());
            entry.put("field", field);
            entry.put("observation", observationText != null ? observationText : "");
            entry.put("type", correctionType);
            entry.put("correction", correctionText != null ? correctionText : "");
            corrections.add(entry);
            if (corrections.size() > 50) corrections = corrections.subList(corrections.size() - 50, corrections.size());
            mem.setUserCorrections(toJson(corrections));
        } catch (Exception e) {
            log.warn("[Wiki] 纠错记录写入失败: {}", e.getMessage());
        }
    }

    // DTO
    @lombok.Data
    public static class WikiSummaryDto {
        private String coreStruggles;
        private String emotionPattern;
        private String effectiveCoping;
        private String languageStyle;
        private String reflection;
        private List<WikiObservation> triggers = new ArrayList<>();
        private List<Map<String, String>> progressNotes = new ArrayList<>();
        private List<Map<String, String>> conflicts = new ArrayList<>();
        private List<Map<String, String>> changeLog = new ArrayList<>();
        private boolean hasData;
    }

    // ==================== 活跃时间更新 ====================

    public void updateLastActiveAt(String userId) {
        UserMemory memory = userMemoryRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserMemory m = new UserMemory();
                    m.setUserId(userId);
                    return m;
                });
        memory.setLastActiveAt(java.time.LocalDateTime.now());
        userMemoryRepository.save(memory);
        log.info("[Memory] lastActiveAt updated: userId={}", userId);
    }

    // ==================== L4 Reflection ====================

    public void generateReflection(String userId) {
        try {
            UserMemory memory = userMemoryRepository
                    .findByUserId(userId).orElse(null);
            if (memory == null) return;

            // 收集现有记忆内容
            StringBuilder memoryContent = new StringBuilder();
            if (memory.getEmotionPattern() != null)
                memoryContent.append("Emotion pattern: ")
                        .append(memory.getEmotionPattern()).append("\n");
            if (memory.getCoreStruggles() != null)
                memoryContent.append("Core struggles: ")
                        .append(memory.getCoreStruggles()).append("\n");
            if (memory.getEffectiveCoping() != null)
                memoryContent.append("Effective coping: ")
                        .append(memory.getEffectiveCoping()).append("\n");
            if (memory.getConversationSummary() != null)
                memoryContent.append("Recent summary: ")
                        .append(memory.getConversationSummary()).append("\n");

            if (memoryContent.isEmpty()) return;

            String prompt = """
            Based on the following user memory data, generate a 
            high-level insight about this user's emotional patterns 
            and progress. Focus on trends, triggers, and what helps.
            Keep it under 200 words, written as a clinical observation.
            
            Memory data:
            %s
            
            Return only the insight text, no labels or formatting.
            """.formatted(memoryContent.toString());

            String reflection = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();

            memory.setReflection(reflection);
            userMemoryRepository.save(memory);

            log.info("[Memory] L4 Reflection generated: userId={}", userId);

        } catch (Exception e) {
            log.error("[Memory] Reflection generation failed: {}",
                    e.getMessage());
        }
    }

    /** AI merge 结果结构 */
    @lombok.Data
    static class WikiMergeResult {
        private String emotionPattern;
        private String coreStruggles;
        private String effectiveCoping;
        private String languageStyle;
        private List<TriggerAction> triggerUpdates;
        private List<ConflictEntry> conflicts;
        private String newProgressNote;
        private String changeLogEntry;

        @lombok.Data
        static class TriggerAction {
            private String observation;
            private String action;      // "new" | "increment" | "remove"
            private String confidence;  // "high" | "medium" | "low"
        }

        @lombok.Data
        static class ConflictEntry {
            private String field;       // "triggers" | "emotionPattern" | "coreStruggles" | "effectiveCoping"
            private String existing;    // what was already in the wiki
            private String observed;    // what was seen in this session
            private String resolution;  // "updated" | "kept" | "both noted"
        }
    }

    /** 单条触发点观察，存入 triggers JSON 数组 */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    static class WikiObservation {
        private String observation;
        private int count = 1;
        private String firstSeen;
        private String lastSeen;
        private String confidence = "medium";
        private double score = 0.0; // 0..1，每次 parseTriggers 时实时重算
    }
}