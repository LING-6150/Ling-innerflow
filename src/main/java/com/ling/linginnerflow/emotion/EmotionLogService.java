package com.ling.linginnerflow.emotion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 情绪日志服务
 * 记录每次对话的情绪数据，提供趋势分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionLogService {

    private final EmotionLogRepository emotionLogRepository;

    /**
     * 记录一次情绪日志
     * 在EmotionController和CheckInConsumer里调用
     */
    public void log(String userId, int emotionLevel,
                    String userInput, String aiResponse, String source) {
        try {
            EmotionLog log = new EmotionLog();
            log.setUserId(userId);
            log.setEmotionLevel(emotionLevel);
            log.setUserInput(userInput);
            log.setAiResponse(aiResponse);
            log.setSource(source);
            emotionLogRepository.save(log);
        } catch (Exception e) {
            log.error("情绪日志记录失败: {}", e.getMessage());
        }
    }

    /**
     * 查询最近N天的情绪趋势
     * 返回每天的平均情绪等级和记录次数
     */
    public List<Map<String, Object>> getDailyTrend(
            String userId, int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        List<Object[]> raw = emotionLogRepository
                .findDailyStats(userId, start);

        List<Map<String, Object>> result = new ArrayList<>();
        raw.forEach(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row[0].toString());
            item.put("avgLevel",
                    Math.round(((Number) row[1]).doubleValue() * 10.0)
                            / 10.0);
            item.put("count", ((Number) row[2]).longValue());
            result.add(item);
        });
        return result;
    }

    /**
     * 查询情绪等级分布
     * 返回L1-L5各占多少次
     */
    public Map<String, Object> getEmotionDistribution(
            String userId, int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        List<Object[]> raw = emotionLogRepository
                .findEmotionDistribution(userId, start);

        Map<String, Long> distribution = new LinkedHashMap<>();
        // 初始化L1-L5都为0
        for (int i = 1; i <= 5; i++) {
            distribution.put("L" + i, 0L);
        }
        // 填入实际数据
        raw.forEach(row -> {
            int level = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            distribution.put("L" + level, count);
        });

        // 计算总次数
        long total = distribution.values().stream()
                .mapToLong(Long::longValue).sum();

        Map<String, Object> result = new HashMap<>();
        result.put("distribution", distribution);
        result.put("total", total);
        result.put("days", days);
        return result;
    }

    /**
     * 获取用户情绪概览
     * 最近7天的综合统计
     */
    public Map<String, Object> getOverview(String userId) {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        List<EmotionLog> logs = emotionLogRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        userId, start, LocalDateTime.now());

        if (logs.isEmpty()) {
            return Map.of(
                    "message", "最近7天暂无情绪记录",
                    "total", 0
            );
        }

        // 计算平均情绪等级
        double avgLevel = logs.stream()
                .mapToInt(EmotionLog::getEmotionLevel)
                .average()
                .orElse(0);

        // 找出最高和最低情绪
        int maxLevel = logs.stream()
                .mapToInt(EmotionLog::getEmotionLevel)
                .max().orElse(0);
        int minLevel = logs.stream()
                .mapToInt(EmotionLog::getEmotionLevel)
                .min().orElse(0);

        // 最近一次情绪
        EmotionLog latest = logs.get(logs.size() - 1);

        Map<String, Object> result = new HashMap<>();
        result.put("total", logs.size());
        result.put("avgLevel",
                Math.round(avgLevel * 10.0) / 10.0);
        result.put("maxLevel", maxLevel);
        result.put("minLevel", minLevel);
        result.put("latestLevel", latest.getEmotionLevel());
        result.put("latestTime",
                latest.getCreatedAt().toString());
        return result;
    }

    public List<EmotionLog> getHistory(String userId) {
        return emotionLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }
}