package com.ling.linginnerflow.agent.tool;

import com.ling.linginnerflow.websocket.ChatMessage;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryContextRetriever implements AgentTool {

    private final ChatMessageRepository chatMessageRepository;

    @Override
    public String getName() {
        return "HistoryContextRetriever";
    }

    @Override
    public String getDescription() {
        return "Call this when the user references past experiences, uses words like " +
                "'last time', 'again', 'still', 'same as before', or 'keeps happening'. " +
                "Retrieves recent conversation history to identify emotional patterns. " +
                "Input: userId";
    }

    @Override
    public String execute(String userId) {
        log.info("[Tool] HistoryContextRetriever input: '{}'", userId);
        try {
            List<ChatMessage> history = chatMessageRepository
                    .findTop50ByUserIdOrderByCreatedAtAsc(userId);

            if (history.isEmpty()) {
                return "No conversation history found.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Recent conversation history:\n");

            int start = Math.max(0, history.size() - 10);
            for (int i = start; i < history.size(); i++) {
                ChatMessage msg = history.get(i);
                String role = "user".equals(msg.getRole()) ? "User" : "AI";
                sb.append(role).append(": ")
                        .append(msg.getContent()).append("\n");
                if (msg.getEmotionLevel() != null
                        && msg.getEmotionLevel() > 0) {
                    sb.append("(Emotion level: L")
                            .append(msg.getEmotionLevel()).append(")\n");
                }
            }

            log.info("[Tool] HistoryContextRetriever: userId={}, found {} records",
                    userId, history.size());
            return sb.toString();

        } catch (Exception e) {
            log.error("[Tool] HistoryContextRetriever failed: {}", e.getMessage());
            return "Failed to retrieve conversation history.";
        }
    }
}