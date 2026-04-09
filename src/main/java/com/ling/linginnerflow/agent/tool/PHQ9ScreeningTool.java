package com.ling.linginnerflow.agent.tool;

import com.ling.linginnerflow.websocket.ChatMessage;
import com.ling.linginnerflow.websocket.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PHQ9ScreeningTool implements AgentTool {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatClient.Builder chatClientBuilder;

    @Override
    public String getName() {
        return "PHQ9ScreeningTool";
    }

    @Override
    public String getDescription() {
        return "Generates a PHQ-9 depression screening estimate based on " +
                "the patient's recent conversation history. " +
                "Returns risk level (Minimal/Mild/Moderate/Severe) " +
                "and recommended clinical action. Input: userId";
    }

    @Override
    public String execute(String userId) {
        try {
            log.info("[Tool] PHQ9ScreeningTool: userId={}", userId);

            // 拿最近20条对话
            List<ChatMessage> history = chatMessageRepository
                    .findTop50ByUserIdOrderByCreatedAtAsc(userId);

            if (history.isEmpty()) {
                return "Insufficient conversation history for PHQ-9 screening.";
            }

            // 取最近20条用户消息
            List<ChatMessage> userMessages = history.stream()
                    .filter(m -> "user".equals(m.getRole()))
                    .collect(Collectors.toList());

            log.info("[Tool] PHQ9: total={}, userMessages={}",
                    history.size(), userMessages.size());

            if (userMessages.isEmpty()) {
                return "No user messages found for PHQ-9 screening.";
            }

            int start = Math.max(0, userMessages.size() - 20);
            String recentMessages = userMessages.subList(start, userMessages.size())
                    .stream()
                    .map(ChatMessage::getContent)
                    .collect(Collectors.joining("\n- ", "- ", ""));

            // 用LLM基于对话历史做PHQ-9估算
            String prompt = """
                You are a clinical mental health screening assistant.
                
                Based on the following patient's recent messages, 
                provide a PHQ-9 depression screening estimate.
                
                Patient messages:
                %s
                
                Analyze these messages and provide:
                1. PHQ-9 estimated score range (0-27)
                2. Severity level: Minimal (0-4) / Mild (5-9) / 
                   Moderate (10-14) / Moderately Severe (15-19) / Severe (20-27)
                3. Key symptoms observed from the conversation
                4. Recommended clinical action
                
                Format your response as:
                PHQ-9 SCREENING ESTIMATE
                Score Range: X-X
                Severity: [level]
                Key Indicators: [list 2-3 specific things from the conversation]
                Recommendation: [clinical action]
                Disclaimer: This is an AI-assisted estimate based on 
                conversation analysis, not a clinical diagnosis.
                """.formatted(recentMessages);

            String result = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();

            log.info("[Tool] PHQ9 screening complete: userId={}", userId);
            return result;

        } catch (Exception e) {
            log.error("[Tool] PHQ9ScreeningTool failed: {}", e.getMessage());
            return "PHQ-9 screening failed: " + e.getMessage();
        }
    }
}