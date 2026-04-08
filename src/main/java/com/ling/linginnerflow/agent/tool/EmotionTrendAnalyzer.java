package com.ling.linginnerflow.agent.tool;

import com.ling.linginnerflow.websocket.ChatMessage;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionTrendAnalyzer implements AgentTool {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public String getName() {
        return "EmotionTrendAnalyzer";
    }

    @Override
    public String getDescription() {
        return "Analyzes the user's emotional trend over the past 7 days. " +
                "Call this when the user shows persistent distress, " +
                "repeatedly mentions similar emotions, or when understanding emotional patterns is needed. " +
                "Input: userId";
    }

    @Override
    public String execute(String userId) {
        try {
            log.info("[Tool] EmotionTrendAnalyzer: userId={}", userId);

            // 拿最近7天有情绪等级的消息
            List<ChatMessage> history = chatMessageRepository
                    .findTop50ByUserIdOrderByCreatedAtAsc(userId);

            List<ChatMessage> userMsgs = history.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .filter(m -> m.getEmotionLevel() != null
                            && m.getEmotionLevel() > 0)
                    .collect(Collectors.toList());

            if (userMsgs.isEmpty()) {
                return "No sufficient emotion records yet.";
            }

            // 统计各等级出现次数
            Map<Integer, Long> levelCount = userMsgs.stream()
                    .collect(Collectors.groupingBy(
                            ChatMessage::getEmotionLevel,
                            Collectors.counting()));

            // 计算平均情绪等级
            double avgLevel = userMsgs.stream()
                    .mapToInt(ChatMessage::getEmotionLevel)
                    .average().orElse(1.0);

            // 最近3条的情绪等级
            List<Integer> recentLevels = userMsgs.stream()
                    .skip(Math.max(0, userMsgs.size() - 3))
                    .map(ChatMessage::getEmotionLevel)
                    .collect(Collectors.toList());

            // 判断趋势
            String trend;
            boolean persistentDistress = recentLevels.stream()
                    .allMatch(l -> l >= 3);

            if (persistentDistress) {
                trend = "Persistent distress (recent conversations all L3 or above)";
            } else if (avgLevel >= 3) {
                trend = "Generally low mood";
            } else if (avgLevel <= 1.5) {
                trend = "Generally stable";
            } else {
                trend = "Fluctuating emotions";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("User Emotion Trend Analysis:\n");
            sb.append("Average emotion level: L").append(String.format("%.1f", avgLevel)).append("\n");
            sb.append("Overall trend: ").append(trend).append("\n");
            sb.append("Recent emotion levels: ").append(
                    recentLevels.stream().map(l -> "L" + l).collect(Collectors.joining(" → "))
            ).append("\n");

            if (persistentDistress) {
                sb.append("[Note] User has been in persistent moderate-to-high distress. Consider proactive support.");
            }

            log.info("[Tool] EmotionTrendAnalyzer Result: trend={}, avg={}",
                    trend, avgLevel);
            return sb.toString();

        } catch (Exception e) {
            log.error("[Tool] EmotionTrendAnalyzer failed: {}", e.getMessage());
            return "Emotion trend analysis failed.";
        }
    }
}