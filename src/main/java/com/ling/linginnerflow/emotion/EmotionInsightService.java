package com.ling.linginnerflow.emotion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 情绪解读引擎（规则版）
 * 根据用户情绪数据生成陪伴式解读
 * 不说教，不鼓励，只是"帮用户说出他们说不出来的话"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionInsightService {

    private final EmotionLogRepository emotionLogRepository;

    /**
     * 生成情绪洞察
     */
    public Map<String, Object> getInsight(String userId) {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        List<EmotionLog> logs = emotionLogRepository
                .findByUserIdAndCreatedAtBetweenOrderByCreatedAtAsc(
                        userId, start, LocalDateTime.now());

        if (logs.isEmpty()) {
            return Map.of(
                    "summary", "还没有情绪记录，开始和我聊聊吧 🌸",
                    "pattern", "",
                    "suggestion", "",
                    "tone", "gentle"
            );
        }

        double avgLevel = logs.stream()
                .mapToInt(EmotionLog::getEmotionLevel)
                .average().orElse(1.0);

        String trend = analyzeTrend(logs);
        String summary = generateSummary(avgLevel, logs.size());
        String pattern = generatePattern(trend, logs);
        String suggestion = generateSuggestion(avgLevel, trend);

        return Map.of(
                "summary", summary,
                "pattern", pattern,
                "suggestion", suggestion,
                "tone", getTone(avgLevel),
                "avgLevel", Math.round(avgLevel * 10.0) / 10.0,
                "totalDays", logs.size()
        );
    }

    /**
     * 分析情绪趋势方向
     */
    private String analyzeTrend(List<EmotionLog> logs) {
        if (logs.size() < 3) return "stable";

        // 取前半段和后半段平均值比较
        int mid = logs.size() / 2;
        double firstHalf = logs.subList(0, mid).stream()
                .mapToInt(EmotionLog::getEmotionLevel)
                .average().orElse(1.0);
        double secondHalf = logs.subList(mid, logs.size()).stream()
                .mapToInt(EmotionLog::getEmotionLevel)
                .average().orElse(1.0);

        double diff = secondHalf - firstHalf;
        if (diff > 0.5) return "rising";      // 情绪在上升（变差）
        if (diff < -0.5) return "improving";   // 情绪在改善
        return "fluctuating";                  // 波动
    }

    /**
     * 生成主要总结（陪伴式，不评判）
     */
    private String generateSummary(double avgLevel, int count) {
        if (avgLevel <= 1.5) {
            return "这几天你比较平静，情绪比较稳定 🌱";
        } else if (avgLevel <= 2.5) {
            return "这几天有些起伏，但你一直在记录自己";
        } else if (avgLevel <= 3.5) {
            return "这段时间有些沉重，你一直在坚持面对";
        } else {
            return "这段时间很不容易，你能坚持到这里，已经很了不起了";
        }
    }

    /**
     * 生成情绪模式描述
     */
    private String generatePattern(String trend,
                                   List<EmotionLog> logs) {
        return switch (trend) {
            case "rising" -> "情绪最近有些向下，需要多关注自己";
            case "improving" -> "状态在慢慢变好，这几天轻了一些";
            case "fluctuating" -> "有些起伏，但你一直在走";
            default -> "情绪比较平稳，没有太大波动";
        };
    }

    /**
     * 生成轻柔建议（不是"你应该"，而是"也许可以"）
     */
    private String generateSuggestion(double avgLevel,
                                      String trend) {
        if (avgLevel > 3 || "rising".equals(trend)) {
            return "也许可以试着和我多聊一聊，或者去 Tap 一下";
        } else if (avgLevel > 2) {
            return "慢一点没关系，你不需要马上变好";
        } else {
            return "继续这样就好，记录本身就是在照顾自己";
        }
    }

    /**
     * 根据情绪等级确定语气
     */
    private String getTone(double avgLevel) {
        if (avgLevel <= 1.5) return "calm";
        if (avgLevel <= 2.5) return "gentle";
        if (avgLevel <= 3.5) return "supportive";
        return "caring";
    }
}