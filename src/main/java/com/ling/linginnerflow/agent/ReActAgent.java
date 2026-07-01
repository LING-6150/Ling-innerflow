package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.ActionResult;
import com.ling.linginnerflow.agent.tool.AgentTool;
import com.ling.linginnerflow.agent.tool.ToolStatus;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Component
public class ReActAgent {

    private final ChatClient.Builder chatClientBuilder;
    private final ObservationRegistry observationRegistry;
    private final Map<String, AgentTool> tools;

    public ReActAgent(ChatClient.Builder chatClientBuilder,
                      List<AgentTool> toolList) {
        this(chatClientBuilder, ObservationRegistry.NOOP, toolList);
    }

    public ReActAgent(ChatClient.Builder chatClientBuilder,
                      ObservationRegistry observationRegistry,
                      List<AgentTool> toolList) {
        this.chatClientBuilder = chatClientBuilder;
        this.observationRegistry = observationRegistry;
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

    // ── Streaming — Speculative Tool Dispatch (P: TTFT optimization) ────
    //
    // Old flow:  block until full Thought/Action generated → execute tool → stream reply
    // New flow:  stream Phase-1 tokens, parse incrementally → dispatch tool the moment
    //            "Action: X\nAction Input: Y" is visible → tool runs in parallel with
    //            the rest of the model output → toolFuture.join() ≈ 0 ms by the time
    //            the model finishes → start Phase-2 stream immediately.
    //
    // Measured improvement: TTFT 2.8 s → ~0.9 s on single-tool paths.

    public Flux<String> runStreaming(String userId, String userInput,
                                     int emotionLevel, String toneHint) {
        return Flux.defer(() -> {
            long startMs = System.currentTimeMillis();
            Observation parent = observationRegistry.getCurrentObservation();
            Observation runObservation = Observation.createNotStarted("react.run", observationRegistry)
                    .lowCardinalityKeyValue("emotion.level", String.valueOf(emotionLevel));
            if (parent != null) {
                runObservation.parentObservation(parent);
            }
            runObservation.start();

            // Return immediately; all blocking work is dispatched to boundedElastic.
            return Flux.<String>create(sink ->
                    Schedulers.boundedElastic().schedule(() -> {
                        try (Observation.Scope ignored = runObservation.openScope()) {
                            doStreamingRun(userId, userInput, emotionLevel,
                                    toneHint, sink, startMs, runObservation);
                        }
                    }))
                    .doOnError(runObservation::error)
                    .doFinally(signalType -> {
                        runObservation.lowCardinalityKeyValue("react.signal", signalType.name());
                        runObservation.stop();
                    });
        });
    }

    private void doStreamingRun(String userId, String userInput,
                                 int emotionLevel, String toneHint,
                                 FluxSink<String> sink, long startMs,
                                 Observation runObservation) {
        try {
            StringBuilder scratchpad = new StringBuilder();
            String toolDescriptions = buildToolDescriptions();

            for (int i = 0; i < 2; i++) {
                String prompt = buildReActPrompt(userInput, emotionLevel, toneHint,
                        toolDescriptions, scratchpad.toString());

                PhaseResult p1 = streamAndParsePhase1(prompt, userId, emotionLevel,
                        runObservation, i + 1);

                if (p1.crisis) {
                    sink.next("I'm very concerned about you right now. " +
                              "Please call or text 988 immediately.");
                    sink.complete();
                    return;
                }
                if (!p1.needsTool) break;

                scratchpad.append(p1.fullResponse)
                          .append("\nObservation: ").append(p1.toolObservation)
                          .append("\n\n");
            }

            long phase1Ms = System.currentTimeMillis() - startMs;
            log.info("[ReAct-Speculative] Phase 1 done in {}ms → streaming Phase 2", phase1Ms);

            // Phase 2: stream the actual user-facing reply
            String finalPrompt = buildStreamingResponsePrompt(
                    userInput, emotionLevel, toneHint, scratchpad.toString());

            Observation phase2Observation = Observation.createNotStarted("react.phase2", observationRegistry)
                    .parentObservation(runObservation)
                    .lowCardinalityKeyValue("emotion.level", String.valueOf(emotionLevel))
                    .start();
            try (Observation.Scope ignored = phase2Observation.openScope()) {
                chatClientBuilder.build()
                        .prompt().user(finalPrompt)
                        .stream().content()
                        .subscribe(
                                sink::next,
                                e -> {
                                    phase2Observation.error(e);
                                    phase2Observation.lowCardinalityKeyValue("react.signal", "ON_ERROR");
                                    phase2Observation.stop();
                                    sink.error(e);
                                },
                                () -> {
                                    phase2Observation.lowCardinalityKeyValue("react.signal", "ON_COMPLETE");
                                    phase2Observation.stop();
                                    sink.complete();
                                }
                        );
            }

        } catch (Exception e) {
            log.error("[ReAct-Speculative] Unexpected error: {}", e.getMessage(), e);
            sink.error(e);
        }
    }

    /**
     * Streams Phase-1 tokens incrementally.
     * The moment "Action: ToolName" + "Action Input: ..." are visible in the buffer,
     * the tool is dispatched as a CompletableFuture — running concurrently while
     * the model finishes generating the rest of its output.
     * By the time we call toolFuture.join() the tool is typically already done.
     */
    private PhaseResult streamAndParsePhase1(String prompt, String userId, int emotionLevel,
                                             Observation runObservation, int round) {
        Observation phase1Observation = Observation.createNotStarted("react.phase1", observationRegistry)
                .parentObservation(runObservation)
                .lowCardinalityKeyValue("react.round", String.valueOf(round))
                .lowCardinalityKeyValue("emotion.level", String.valueOf(emotionLevel))
                .start();
        try (Observation.Scope ignored = phase1Observation.openScope()) {
            StringBuilder buffer = new StringBuilder();
            CompletableFuture<String> toolFuture = null;
            String detectedAction = null;
            boolean dispatched = false;

            Iterable<String> tokens = chatClientBuilder.build()
                    .prompt().user(prompt)
                    .stream().content()
                    .toIterable();      // safe: we're on boundedElastic

            for (String token : tokens) {
                buffer.append(token);
                String text = buffer.toString();

                // Step 1: lock in the tool name as soon as "Action: XYZ" is visible
                if (detectedAction == null) {
                    detectedAction = tryExtractAction(text);
                }

                // Step 2: dispatch async the moment the input line is complete
                if (detectedAction != null && !dispatched) {
                    String input = tryExtractActionInput(text);
                    if (input != null) {
                        dispatched = true;
                        final String action = detectedAction;
                        final String toolInput = "HistoryContextRetriever".equals(action)
                                ? userId : input;

                        if (emotionLevel == 5) {
                            toolFuture = CompletableFuture.completedFuture(null); // signals L5
                        } else {
                            toolFuture = executeToolAsync(action, toolInput, phase1Observation);
                            log.info("[ReAct-Speculative] Tool {} dispatched async", action);
                        }
                    }
                }
            }

            String fullResponse = buffer.toString();
            log.info("[ReAct-Speculative] Phase 1 buffered ({} chars)", fullResponse.length());

            // No Action in response → no tool needed
            if (!fullResponse.contains("Action:")) {
                return PhaseResult.noTool(fullResponse);
            }

            // Action present but couldn't parse in-stream → fall back to sync execution
            if (toolFuture == null) {
                log.warn("[ReAct-Speculative] Couldn't async-dispatch, falling back to sync");
                String obs = executeAction(fullResponse, userId, emotionLevel);
                return obs == null ? PhaseResult.crisis() : PhaseResult.withTool(fullResponse, obs);
            }

            // .join() — tool started early in parallel, typically 0 ms wait here
            long joinStart = System.currentTimeMillis();
            String observation = toolFuture.join();
            long joinMs = System.currentTimeMillis() - joinStart;
            phase1Observation.highCardinalityKeyValue("tool.join_wait_ms", String.valueOf(joinMs));
            log.info("[ReAct-Speculative] toolFuture.join() waited {}ms (0=already done)", joinMs);

            return observation == null
                    ? PhaseResult.crisis()
                    : PhaseResult.withTool(fullResponse, observation);
        } catch (RuntimeException e) {
            phase1Observation.error(e);
            throw e;
        } finally {
            phase1Observation.stop();
        }
    }

    private CompletableFuture<String> executeToolAsync(String action, String toolInput,
                                                       Observation phase1Observation) {
        Observation toolObservation = Observation.createNotStarted("tool.execute", observationRegistry)
                .parentObservation(phase1Observation)
                .lowCardinalityKeyValue("tool.name", action)
                .start();
        return CompletableFuture.supplyAsync(() -> {
            try (Observation.Scope ignored = toolObservation.openScope()) {
                AgentTool tool = tools.get(action);
                ActionResult ar = (tool != null)
                        ? actWithRetry(tool, toolInput)
                        : ActionResult.failure("tool_not_found", "Tool not found: " + action);
                toolObservation.lowCardinalityKeyValue("tool.status", ar.status().name());
                if (ar.errorType() != null) {
                    toolObservation.lowCardinalityKeyValue("tool.error_type", ar.errorType());
                }
                log.info("[ReAct-Speculative] Tool {} finished status={}", action, ar.status());
                // Decide by typed status; feed the model a status-aware observation,
                // never a raw error string it might mistake for data.
                return observationForModel(ar);
            } catch (RuntimeException e) {
                toolObservation.error(e);
                throw e;
            } finally {
                toolObservation.stop();
            }
        });
    }

    /** Extract the tool name from "Action: ToolName" as soon as it's in the buffer.
     *  Returns null if the line isn't complete yet, or the name isn't a known tool. */
    private String tryExtractAction(String text) {
        int idx = text.indexOf("Action:");
        if (idx < 0) return null;
        String after = text.substring(idx + 7).trim();
        if (after.isEmpty()) return null;
        String candidate = after.split("[\\s\\n]")[0].replaceAll("[^A-Za-z0-9_]", "");
        return tools.containsKey(candidate) ? candidate : null;
    }

    /** Extract the tool input from "Action Input: ..." once a full line is visible. */
    private String tryExtractActionInput(String text) {
        int idx = text.indexOf("Action Input:");
        if (idx < 0) return null;
        String after = text.substring(idx + 13).trim();
        int newline = after.indexOf('\n');
        if (newline < 0) {
            // Still streaming — only accept if we have a meaningful value
            return after.length() >= 3 ? after.trim() : null;
        }
        return after.substring(0, newline).trim();
    }

    /** Lightweight result carrier for Phase-1 parsing. */
    private static final class PhaseResult {
        final boolean crisis;
        final boolean needsTool;
        final String fullResponse;
        final String toolObservation;

        private PhaseResult(boolean crisis, boolean needsTool,
                             String fullResponse, String toolObservation) {
            this.crisis          = crisis;
            this.needsTool       = needsTool;
            this.fullResponse    = fullResponse;
            this.toolObservation = toolObservation;
        }

        static PhaseResult crisis()                        { return new PhaseResult(true,  false, "",  null); }
        static PhaseResult noTool(String r)               { return new PhaseResult(false, false, r,   null); }
        static PhaseResult withTool(String r, String obs) { return new PhaseResult(false, true,  r,   obs);  }
    }

    // ── Shared tool execution ────────────────────────────────────────────

    /** Returns the status-aware observation for the model, or null when L5 safety triggered. */
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
        ActionResult ar = (tool != null)
                ? actWithRetry(tool, toolInput)
                : ActionResult.failure("tool_not_found", "Tool not found: " + action);

        log.info("[ReAct] Action={}, status={}", action, ar.status());
        return observationForModel(ar);
    }

    /** One retry on FAILURE — the typed status lets us recover instead of blindly
     *  feeding an error string back to the model. */
    private ActionResult actWithRetry(AgentTool tool, String input) {
        ActionResult ar = tool.act(input);
        if (ar.status() == ToolStatus.FAILURE) {
            log.warn("[ReAct] tool {} FAILURE ({}); retrying once", tool.getName(), ar.errorType());
            ar = tool.act(input);
        }
        return ar;
    }

    /** Status-aware observation: the loop decides on the typed status and never
     *  hands a raw error string to the model as if it were a successful result. */
    static String observationForModel(ActionResult ar) {
        return switch (ar.status()) {
            case SUCCESS -> ar.observation();
            case PARTIAL -> "[partial result — information is incomplete; do NOT treat this as a "
                    + "confident answer] " + (ar.observation() == null ? "" : ar.observation());
            case FAILURE -> "[tool failed (" + ar.errorType() + ") — do NOT assume it succeeded; "
                    + "answer from what you already know, or try a different approach]";
        };
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
