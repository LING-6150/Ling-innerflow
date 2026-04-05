package com.ling.linginnerflow.emotion;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emotion-log")
@RequiredArgsConstructor
public class EmotionLogController {

    private final EmotionLogService emotionLogService;

    /**
     * 情绪趋势（前端画折线图用）
     */
    @GetMapping("/trend")
    public List<Map<String, Object>> getTrend(
            @RequestParam(defaultValue = "7") int days) {
        String userId = getUserIdFromToken();
        return emotionLogService.getDailyTrend(userId, days);
    }

    /**
     * 情绪分布（前端画饼图用）
     */
    @GetMapping("/distribution")
    public Map<String, Object> getDistribution(
            @RequestParam(defaultValue = "30") int days) {
        String userId = getUserIdFromToken();
        return emotionLogService.getEmotionDistribution(userId, days);
    }

    /**
     * 7天概览（个人中心首页用）
     */
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        String userId = getUserIdFromToken();
        return emotionLogService.getOverview(userId);
    }

    /**
     * 历史记录列表
     */
    @GetMapping("/history")
    public List<EmotionLog> getHistory() {
        String userId = getUserIdFromToken();
        return emotionLogService.getHistory(userId);
    }

    private String getUserIdFromToken() {
        Authentication auth = SecurityContextHolder.getContext()
                .getAuthentication();
        return (String) auth.getPrincipal();
    }

    private final EmotionInsightService emotionInsightService;

    /**
     * 情绪解读（陪伴式，不评判）
     */
    @GetMapping("/insight")
    public Map<String, Object> getInsight() {
        String userId = getUserIdFromToken();
        return emotionInsightService.getInsight(userId);
    }
}