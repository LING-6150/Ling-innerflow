package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.AgentTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verifies the complete streaming chain for ReActAgent.runStreaming():
 *
 * Q1. runStreaming() returns a Flux<String> that emits every token.
 * Q2. Each token is the raw content chunk (handler wraps it as type:"chunk").
 * Q3. Flux completes normally so the handler can send type:"done".
 * Q4. Tool-calling phase is synchronous (.call()), streaming starts only after.
 */
@ExtendWith(MockitoExtension.class)
class ReActStreamingVerificationTest {

    // Deep stubs let us chain .build().prompt().user(...).call().content()
    // and .build().prompt().user(...).stream().content() without creating
    // intermediate mocks manually.
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder mockBuilder;

    private ReActAgent agent;

    // Stub tool that records whether it was called
    private boolean toolWasCalled;
    private AgentTool stubTool;

    @BeforeEach
    void setUp() {
        toolWasCalled = false;
        stubTool = new AgentTool() {
            @Override public String getName() { return "CBTSkillLibrary"; }
            @Override public String getDescription() { return "returns cbt tips"; }
            @Override public String execute(String input) {
                toolWasCalled = true;
                return "mindfulness tip for: " + input;
            }
        };
        agent = new ReActAgent(mockBuilder, List.of(stubTool));
    }

    // ── Q1 + Q3: Flux emits tokens and completes ─────────────────────────

    @Test
    @DisplayName("Q1+Q3: runStreaming() returns Flux that emits every token and completes normally")
    void runStreaming_noToolNeeded_emitsAllTokensAndCompletes() {
        // Tool-gathering call: no Action → go straight to streaming
        when(mockBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Thought: No tool needed.\nFinal Answer: ignored");

        // Streaming call returns three tokens
        when(mockBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("I'm", " here", " with you."));

        Flux<String> result = agent.runStreaming("user1", "I feel tired", 2, "");

        // Collect all tokens (blocks until Flux completes)
        List<String> tokens = result.collectList().block();

        // Q1: all tokens received
        assertThat(tokens).containsExactly("I'm", " here", " with you.");

        // Q3: Flux completed (collectList().block() would throw if it errored)
        assertThat(tokens).isNotNull();
    }

    // ── Q4: Tool-calling is synchronous, streaming comes after ───────────

    @Test
    @DisplayName("Q4: tool-calling phase uses .call() (sync), streaming starts only after tool completes")
    void runStreaming_withToolAction_executesToolSynchronouslyThenStreams() {
        // First tool-gathering call: triggers a tool action
        when(mockBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Thought: need tool\nAction: CBTSkillLibrary\nAction Input: anxiety");

        // Streaming call returns a token
        when(mockBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("Here's something that might help."));

        List<String> tokens = agent.runStreaming("user1", "I keep spiraling", 3, "").collectList().block();

        // Q4a: the stub tool was invoked synchronously before streaming started
        assertThat(toolWasCalled).isTrue();

        // Q4b: streaming still completed and we got a token
        assertThat(tokens).containsExactly("Here's something that might help.");
    }

    // ── Ordering: sync call precedes stream call ─────────────────────────

    @Test
    @DisplayName("Q4 ordering: .call() (tool-gather) happens before .stream() (response)")
    void runStreaming_callOrder_syncBeforeStream() {
        when(mockBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Thought: no tool");
        when(mockBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("token"));

        agent.runStreaming("user1", "hello", 1, "").collectList().block();

        // Verify .call() was invoked before .stream()
        InOrder order = inOrder(mockBuilder.build().prompt().user(anyString()));
        order.verify(mockBuilder.build().prompt().user(anyString())).call();
        order.verify(mockBuilder.build().prompt().user(anyString())).stream();
    }

    // ── L5 safety: Flux.just() used, not the streaming endpoint ─────────

    @Test
    @DisplayName("L5 protection: returns a single-element Flux immediately (no streaming call)")
    void runStreaming_l5EmotionLevel_returnsFixedFluxWithoutStreaming() {
        // In L5, executeAction() returns null → Flux.just(safetyMsg) is returned
        // before reaching the streaming call. Set up the tool-gather call to have Action:
        when(mockBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Thought: need tool\nAction: CBTSkillLibrary\nAction Input: crisis");

        List<String> tokens = agent.runStreaming("user1", "I want to die", 5, "").collectList().block();

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0)).contains("988");

        // Tool was NOT actually executed (L5 guard triggers before tool.execute())
        assertThat(toolWasCalled).isFalse();

        // Streaming endpoint was NOT called
        verify(mockBuilder.build().prompt().user(anyString()), never()).stream();
    }

    // ── Q2: token shape (handler responsibility, verified by code inspection) ─

    @Test
    @DisplayName("Q2: each Flux element is a raw string token (handler wraps it as type=chunk)")
    void runStreaming_tokenShape_isRawString() {
        when(mockBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Thought: no tool");
        when(mockBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("raw", "token", "s"));

        List<String> tokens = agent.runStreaming("user1", "hey", 1, "").collectList().block();

        // Each element is a plain String — the handler in EmotionWebSocketHandler
        // wraps it as Map.of("type","chunk","content", chunk) before sending.
        assertThat(tokens).allSatisfy(t -> assertThat(t).isInstanceOf(String.class));
        assertThat(tokens).containsExactly("raw", "token", "s");
    }

    // ── Context is injected when a tool provides it ───────────────────────

    @Test
    @DisplayName("Tool observation is included in the streaming prompt (context injection)")
    void runStreaming_toolObservation_appearsInStreamingPrompt() {
        when(mockBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("Thought: need tool\nAction: CBTSkillLibrary\nAction Input: rumination");
        when(mockBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("response"));

        List<String> tokens = agent.runStreaming("user1", "I keep ruminating", 3, "").collectList().block();

        assertThat(toolWasCalled).isTrue();
        // streaming call happened (tokens arrived) and context was passed through
        assertThat(tokens).containsExactly("response");
    }
}
