package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.AgentTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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

    // ── Blocking (kept for backwards compatibility) ──────────────────────

    public String run(String userId, String userInput,
                      int emotionLevel, String toneHint) {
        int maxIterations = 3;
        StringBuilder scratchpad = new StringBuilder();
        String toolDescriptions = buildToolDescriptions();

        for (int i = 0; i < maxIterations; i++) {
            String prompt = buildReActPrompt(
                    userInput, emotionLevel, toneHint,
                    toolDescriptions, scratchpad.toString());

            String response = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();

            log.info("[ReAct] Round {}:\n{}", i + 1, response);

            if (response.contains("Final Answer:")) {
                String answer = response.substring(
                        response.indexOf("Final Answer:") + 13).trim();
                log.info("[ReAct] Final Answer: {}", answer);
                return answer;
            }

            if (response.contains("Action:")) {
                String observation = executeAction(response, userId, emotionLevel);
                if (observation == null) {
                    return "I'm very concerned about you right now. Please call or text 988 immediately.";
                }
                scratchpad.append(response)
                        .append("\nObservation: ").append(observation)
                        .append("\n\n");
            } else {
                return response.trim();
            }
        }

        return generateFallback(userInput, emotionLevel);
    }

    // ── Streaming (real token-by-token output) ───────────────────────────

    /**
     * Tool-gathering phase runs synchronously (max 2 iterations) so that
     * structured Thought/Action output can be parsed reliably.
     * The final user-facing response is generated via a streaming call,
     * returning a Flux<String> where each element is one token.
     */
    public Flux<String> runStreaming(String userId, String userInput,
                                     int emotionLevel, String toneHint) {
        StringBuilder scratchpad = new StringBuilder();
        String toolDescriptions = buildToolDescriptions();

        // Phase 1: synchronous tool-gathering (max 2 iterations)
        for (int i = 0; i < 2; i++) {
            String prompt = buildReActPrompt(
                    userInput, emotionLevel, toneHint,
                    toolDescriptions, scratchpad.toString());

            String response = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();   // <-- blocking call

            log.info("[ReAct-Stream] Tool round {}:\n{}", i + 1, response);

            if (!response.contains("Action:")) {
                // No tool needed — proceed directly to streaming response
                break;
            }

            String observation = executeAction(response, userId, emotionLevel);
            if (observation == null) {
                // L5 safety — return a fixed Flux immediately
                return Flux.just(
                        "I'm very concerned about you right now. " +
                        "Please call or text 988 immediately.");
            }
            log.info("[ReAct-Stream] Observation: {}", observation);
            scratchpad.append(response)
                    .append("\nObservation: ").append(observation)
                    .append("\n\n");
        }

        // Phase 2: stream the final user-facing response
        String finalPrompt = buildStreamingResponsePrompt(
                userInput, emotionLevel, toneHint, scratchpad.toString());
        log.info("[ReAct-Stream] Streaming final response for userId={}", userId);

        return chatClientBuilder.build()
                .prompt()
                .user(finalPrompt)
                .stream()
                .content();    // <-- returns Flux<String>; one element per token
    }

    // ── Shared tool execution ────────────────────────────────────────────

    /** Returns the tool observation, or null when L5 safety protection triggered. */
    private String executeAction(String response, String userId, int emotionLevel) {
        String action = extractBetween(
                response, "Action:",
                response.contains("Action Input:") ? "Action Input:" : "\n")
                .trim().replaceAll("\\s+", "");

        String actionInput = response.contains("Action Input:")
                ? extractAfter(response, "Action Input:").trim() : "";

        if (emotionLevel == 5) return null;

        String toolInput = "HistoryContextRetriever".equals(action) ? userId : actionInput;
        AgentTool tool = tools.get(action);
        String observation = (tool != null)
                ? tool.execute(toolInput)
                : "Tool not found: " + action;

        log.info("[ReAct] Action={}, Observation={}", action, observation);
        return observation;
    }

    // ── Prompt builders ──────────────────────────────────────────────────

    private String buildToolDescriptions() {
        return tools.values().stream()
                .map(t -> "- " + t.getName() + ": " + t.getDescription())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Prompt for the streaming response phase.
     * Instructs the model to output ONLY the reply text — no Thought/Action prefixes —
     * so every streamed token goes directly to the user.
     */
    private String buildStreamingResponsePrompt(String userInput, int emotionLevel,
                                                 String toneHint, String context) {
        String plannerSection = (toneHint != null && !toneHint.isBlank())
                ? "PLANNER GUIDANCE (follow this):\n" + toneHint + "\n\n"
                : "";
        String contextSection = context.isBlank() ? ""
                : "CONTEXT FROM TOOLS:\n" + context + "\n\n";

        return """
        You are not a therapist. You don't diagnose, judge, or lecture.
        You are simply a present, caring person sitting with the user right now.

        %s%s
        EMOTION LEVEL GUIDANCE:
        %s

        CORE RULES:
        - Talk like a real person, not a script
        - Don't rush to fix things; no advice unless explicitly asked
        - [CRITICAL] Output ONLY your response to the user.
          Do NOT include any prefix such as "Thought:", "Final Answer:", or "Assistant:".
          Just speak directly, as if in a conversation.

        User said: %s
        """.formatted(plannerSection, contextSection, buildLevelGuidance(emotionLevel), userInput);
    }

    private String buildLevelGuidance(int emotionLevel) {
        return switch (emotionLevel) {
            case 1 -> """
                    User is emotionally stable. Be a quiet, present companion.
                    - One natural response; make the user feel heard
                    - No advice, no analysis, no question unless it flows naturally
                    - Under 40 words""";
            case 2 -> """
                    User is feeling a little low. Be a gentle companion.
                    - 1 sentence of natural empathy
                    - Optionally 1 light conversational question
                    - 60–90 words max""";
            case 3 -> """
                    User is moderately distressed. Be a perceptive companion.
                    - 1 sentence of empathy; name a feeling or pattern you notice
                    - Ask 1 open question to invite more sharing
                    - 80–120 words max""";
            case 4 -> """
                    User is in pain. Be a steady, grounded presence.
                    - First sentence must make them feel you are there
                    - No analysis; short sentences, slow tone
                    - Ask 1 very simple question about their current state""";
            default -> "Respond with warmth and presence.";
        };
    }

    private String buildReActPrompt(String userInput, int emotionLevel,
                                    String toneHint,
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

        String plannerSection = (toneHint != null && !toneHint.isBlank())
                ? "========================\nPLANNER GUIDANCE (follow this):\n" + toneHint + "\n"
                : "";

        return """
        You are not a therapist. You don't diagnose, judge, or lecture.
        You are simply a present, caring person sitting with the user right now.

        Your goal is not to "say the right thing" — it's to keep the conversation flowing naturally.

        %s
        ========================
        CORE RULES (very important)""".formatted(plannerSection) + """

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
                .user("Respond warmly and briefly: " + userInput)
                .call().content();
    }

    private String extractBetween(String text, String start, String end) {
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
