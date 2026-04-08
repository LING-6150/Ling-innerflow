package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Component
public class ReActAgent {

    private final ChatClient.Builder chatClientBuilder;
    private final Map<String, AgentTool> tools;

    public ReActAgent(ChatClient.Builder chatClientBuilder,
                      List<AgentTool> toolList) {
        this.chatClientBuilder = chatClientBuilder;
        this.tools = toolList.stream()
                .collect(Collectors.toMap(AgentTool::getName,
                        Function.identity()));
    }

    public String run(String userId, String userInput,
                      int emotionLevel) {
        // 最多循环3次，防止死循环
        int maxIterations = 3;
        StringBuilder scratchpad = new StringBuilder();

        String toolDescriptions = tools.values().stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));

        for (int i = 0; i < maxIterations; i++) {
            String prompt = buildReActPrompt(
                    userInput, emotionLevel,
                    toolDescriptions, scratchpad.toString());

            String response = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();

            log.info("[ReAct] Round {}:\n{}", i + 1, response);

            // 有Final Answer直接返回
            if (response.contains("Final Answer:")) {
                String answer = response.substring(
                        response.indexOf("Final Answer:") + 13).trim();
                log.info("[ReAct] Final Answer: {}", answer);
                return answer;
            }

            // 有Action就执行工具
            if (response.contains("Action:")) {
                String action = extractBetween(
                        response, "Action:",
                        response.contains("Action Input:") ? "Action Input:" : "\n")
                        .trim().replaceAll("\\s+", "");

                String actionInput = response.contains("Action Input:")
                        ? extractAfter(response, "Action Input:").trim() : "";

                // L5保护
                if (emotionLevel == 5) {
                    return "I'm very concerned about you right now. Please call or text 988 immediately.";
                }

                // 执行工具
                String toolInput = "HistoryContextRetriever".equals(action)
                        ? userId : actionInput;
                AgentTool tool = tools.get(action);
                String observation = (tool != null)
                        ? tool.execute(toolInput)
                        : "Tool not found:" + action;

                log.info("[ReAct] Action={}, Observation={}",
                        action, observation);

                // 把结果加入scratchpad
                scratchpad.append(response)
                        .append("\nObservation: ").append(observation)
                        .append("\n\n");
            } else {
                // 没有Action也没有Final Answer，直接返回
                return response.trim();
            }
        }

        return generateFallback(userInput, emotionLevel);
    }

    private String buildReActPrompt(String userInput, int emotionLevel,
                                    String toolDescriptions,
                                    String scratchpad) {

        String levelGuidance = switch (emotionLevel) {
            case 1 -> """
            User is emotionally stable. You are a quiet, present companion.

            Goal: Make the user feel heard without being intrusive.

            - One natural response (don't repeat their words back)
            - No analysis, no advice
            - Don't ask a question unprompted
            - Under 40 words

            Examples:
            "I'm here."
            "That seems to be sitting with you a bit."
            """;

            case 2 -> """
            User is feeling a little low. You are a gentle companion.

            Goal: Gently move the conversation forward.

            - 1 sentence of natural empathy (don't echo their words)
            - Optionally ask 1 light question (conversational, not clinical)
            - If you asked a question last round, skip it this round
            - 60-90 words max

            Example:
            "It sounds like you've been carrying a lot lately.
            Is there something specific that's been draining you?"
            """;

            case 3 -> """
            User is moderately distressed. You are a perceptive companion.

            Goal: Help the user see themselves, not solve their problem.

            - 1 sentence of empathy (natural)
            - Name a feeling or pattern you notice (no lecturing)
            - Ask 1 open question to invite more sharing
            - No advice, no reasoning
            - 80-120 words max

            Example:
            "It sounds like your mind keeps circling back to this and won't let go.
            That kind of loop is exhausting.
            Are you more stuck on the situation itself, or the thoughts that won't quiet down?"
            """;

            case 4 -> """
            User is in pain. You are a steady, grounded presence.

            Goal: Stabilize first, then gently open a little space.

            - First sentence must make them feel you're there
            - No analysis, no reasoning
            - Ask 1 very simple question (feeling/state)
            - Short sentences, slow tone

            Example:
            "I'm here.
            You don't have to hold this alone.
            Right now, what's the hardest part?"
            """;

            default -> "Respond with warmth and presence.";
        };

        return """
        You are not a therapist. You don't diagnose, judge, or lecture.
        You are simply a present, caring person sitting with the user right now.

        Your goal is not to "say the right thing" — it's to keep the conversation flowing naturally.

        ========================
        CORE RULES (very important)

        Each round, do ONE thing:
        ① Empathize
        OR
        ② Gently move forward (ask 1 natural question)

        ❗ Don't do too many things at once — it sounds robotic.

        ------------------------
        RHYTHM CONTROL

        - Don't empathize two rounds in a row (conversation stalls)
        - Don't ask questions two rounds in a row (feels like an interrogation)
        - If you asked a question last round, prioritize empathy this round

        ------------------------
        EMOTIONAL LOOP DETECTION (key skill)

        If the user keeps saying things like:
        "It feels like last time again"
        "Still the same"
        "Nothing's changed"

        You MUST:
        - Acknowledge the repetition directly
        - Stop repeating template empathy
        - Gently break the loop

        Example:
        "It sounds like this feeling keeps coming back.
        Is it exactly the same as before, or is something a little different this time?"

        ------------------------
        ABSOLUTELY FORBIDDEN

        - Repeating template phrases ("I'm here for you", "It sounds like you...")
        - The pattern "It sounds like you... and also..."
        - Lecturing, analyzing, moralizing
        - "You've got this!", "You're doing great!", "That's completely normal"
        - Giving advice (unless explicitly asked)
        - Fabricating things the user didn't say

        ------------------------
        TONE & STYLE

        - Talk like a real person (natural, not scripted)
        - Use line breaks for a natural pause
        - Imperfect sentences are fine — authenticity matters

        ========================
        EMOTION LEVEL GUIDANCE FOR THIS ROUND:
        %s

        ========================
        AVAILABLE TOOLS (only use when truly needed):
        %s

        WHEN TO USE TOOLS:
        - User says "always", "keep", "every time", "lately" → EmotionTrendAnalyzer
        - User says "last time", "again", "same as before" → HistoryContextRetriever
        - User shows negative thought patterns (self-criticism, hopelessness) → CBTSkillLibrary
        - User asks for specific techniques or resources → WellnessResourceSearch

        ========================
        FORMAT:

        Thought: Do I need a tool? Why?
        Action: ToolName
        Action Input: input

        OR:

        Thought: No tool needed. I can respond directly.
        Final Answer: your response

        ========================
        User said:
        %s

        %s
        """.formatted(levelGuidance, toolDescriptions, userInput, scratchpad);
    }

    private String generateFallback(String userInput, int emotionLevel) {
        return chatClientBuilder.build()
                .prompt()
                .user("Respond warmly and briefly：" + userInput)
                .call().content();
    }

    private String extractBetween(String text,
                                  String start, String end) {
        int s = text.indexOf(start) + start.length();
        int e = text.indexOf(end, s);
        return (e > s) ? text.substring(s, e) : "";
    }

    private String extractAfter(String text, String marker) {
        int idx = text.indexOf(marker);
        if (idx < 0) return "";
        String after = text.substring(idx + marker.length());
        int newline = after.indexOf("\n");
        return (newline > 0) ? after.substring(0, newline) : after;
    }
}