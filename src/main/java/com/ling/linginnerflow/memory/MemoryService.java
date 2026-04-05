package com.ling.linginnerflow.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    // 短期记忆Key前缀
    private static final String SHORT_MEMORY_PREFIX = "memory:short:";
    // 短期记忆TTL：30分钟无操作则过期
    private static final long SHORT_MEMORY_TTL = 30;
    // 触发摘要压缩的对话轮数
    private static final int SUMMARY_THRESHOLD = 10;

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

            // 超过阈值触发摘要压缩
            if (history.size() >= SUMMARY_THRESHOLD * 2) {
                compressMemory(userId, history);
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
     * 更新长期记忆
     * 在会话结束或定期调用，用LLM提取关键信息
     */
    public void updateLongMemory(String userId,
                                 List<ConversationMessage> history) {
        if (history.isEmpty()) return;

        try {
            // 用LLM从对话中提取关键信息
            String extractPrompt = buildExtractPrompt(history);
            String extracted = chatClientBuilder.build()
                    .prompt()
                    .user(extractPrompt)
                    .call()
                    .content();

            // 解析LLM返回的JSON
            UserMemoryExtract extract = objectMapper
                    .readValue(extracted, UserMemoryExtract.class);

            // 存入MySQL
            UserMemory memory = userMemoryRepository
                    .findByUserId(userId)
                    .orElse(new UserMemory());

            memory.setUserId(userId);
            if (extract.getEmotionPattern() != null) {
                memory.setEmotionPattern(extract.getEmotionPattern());
            }
            if (extract.getCoreStruggles() != null) {
                memory.setCoreStruggles(extract.getCoreStruggles());
            }
            if (extract.getEffectiveCoping() != null) {
                memory.setEffectiveCoping(extract.getEffectiveCoping());
            }

            userMemoryRepository.save(memory);
            log.info("长期记忆更新完成: userId={}", userId);

        } catch (Exception e) {
            log.error("长期记忆更新失败: {}", e.getMessage());
        }
    }

    // ==================== 摘要压缩 ====================

    /**
     * 摘要压缩
     * 对话超过10轮时，把历史压缩成摘要，清空Redis重新开始
     */
    private void compressMemory(String userId,
                                List<ConversationMessage> history) {
        try {
            log.info("触发摘要压缩: userId={}, 对话轮数={}",
                    userId, history.size() / 2);

            // 调LLM生成摘要
            String summaryPrompt = buildSummaryPrompt(history);
            String summary = chatClientBuilder.build()
                    .prompt()
                    .user(summaryPrompt)
                    .call()
                    .content();

            // 更新长期记忆里的摘要
            UserMemory memory = userMemoryRepository
                    .findByUserId(userId)
                    .orElse(new UserMemory());
            memory.setUserId(userId);
            memory.setConversationSummary(summary);
            userMemoryRepository.save(memory);

            // 清空Redis，用摘要作为新的起点
            List<ConversationMessage> compressed = new ArrayList<>();
            compressed.add(new ConversationMessage(
                    "system",
                    "【之前对话摘要】" + summary,
                    System.currentTimeMillis()
            ));

            String key = SHORT_MEMORY_PREFIX + userId;
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(compressed),
                    SHORT_MEMORY_TTL,
                    TimeUnit.MINUTES
            );

            log.info("摘要压缩完成: userId={}", userId);

        } catch (Exception e) {
            log.error("摘要压缩失败: {}", e.getMessage());
        }
    }

    // ==================== Prompt构建 ====================

    /**
     * 构建带历史上下文的Prompt前缀
     * 供各节点调用，把记忆注入Prompt
     */
    public String buildContextPrompt(String userId) {
        StringBuilder context = new StringBuilder();

        // 加入长期记忆
        UserMemory longMemory = getLongMemory(userId);
        if (longMemory != null) {
            context.append("【用户背景信息】\n");
            if (longMemory.getEmotionPattern() != null) {
                context.append("情绪模式：")
                        .append(longMemory.getEmotionPattern())
                        .append("\n");
            }
            if (longMemory.getCoreStruggles() != null) {
                context.append("核心困扰：")
                        .append(longMemory.getCoreStruggles())
                        .append("\n");
            }
            if (longMemory.getEffectiveCoping() != null) {
                context.append("有效应对：")
                        .append(longMemory.getEffectiveCoping())
                        .append("\n");
            }
            context.append("\n");
        }

        // 加入短期记忆（最近5轮）
        List<ConversationMessage> history = getShortMemory(userId);
        if (!history.isEmpty()) {
            context.append("【最近对话记录】\n");
            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                ConversationMessage msg = history.get(i);
                String roleLabel = "user".equals(msg.getRole())
                        ? "用户" : "AI";
                context.append(roleLabel).append("：")
                        .append(msg.getContent()).append("\n");
            }
            context.append("\n");
        }

        return context.toString();
    }

    /**
     * 构建信息提取Prompt
     */
    private String buildExtractPrompt(
            List<ConversationMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("请从以下对话中提取用户的关键心理信息，");
        sb.append("以JSON格式返回，只返回JSON不要其他文字：\n\n");
        history.forEach(msg -> sb.append(msg.getRole())
                .append(": ").append(msg.getContent()).append("\n"));
        sb.append("""
            \n返回格式：
            {
              "emotionPattern": "情绪模式描述，如：容易焦虑，有全或无思维",
              "coreStruggles": "核心困扰，如：工作压力、自我价值感低",
              "effectiveCoping": "有效应对方式，如：对呼吸练习反应好"
            }
            如果信息不足，对应字段返回null。
            """);
        return sb.toString();
    }

    /**
     * 构建摘要压缩Prompt
     */
    private String buildSummaryPrompt(
            List<ConversationMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将以下对话压缩成100字以内的摘要，");
        sb.append("保留关键情绪信息和重要转折点：\n\n");
        history.forEach(msg -> sb.append(msg.getRole())
                .append(": ").append(msg.getContent()).append("\n"));
        return sb.toString();
    }

    /**
     * LLM提取结果的结构
     */
    @lombok.Data
    static class UserMemoryExtract {
        private String emotionPattern;
        private String coreStruggles;
        private String effectiveCoping;
    }
}