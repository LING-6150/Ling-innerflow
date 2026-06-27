package com.ling.linginnerflow.agent.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmotionAnalyzerNode crisis fail-safe logic.
 *
 * These target the pure resolution logic (no LLM call), so they construct the
 * node with a null ChatClient.Builder — the builder is only used by analyze().
 *
 * Core safety invariant: a crisis MUST NOT be downgraded to L1 just because the
 * LLM returned a non-numeric / malformed response. Crisis detection is defense
 * in depth: a deterministic keyword net runs regardless of the LLM, and the
 * final level is the max of the two signals.
 */
class EmotionAnalyzerNodeTest {

    private final EmotionAnalyzerNode node = new EmotionAnalyzerNode(null);

    @Test
    @DisplayName("clean numeric LLM response is parsed as-is")
    void parsesCleanNumber() {
        assertThat(node.resolveLevel("3", "feeling down lately")).isEqualTo(3);
    }

    @Test
    @DisplayName("robustly extracts the level from a noisy LLM response")
    void extractsLevelFromNoisyResponse() {
        assertThat(node.resolveLevel("Level: 4", "work is overwhelming")).isEqualTo(4);
        assertThat(node.resolveLevel("5.", "...")).isEqualTo(5);
    }

    @Test
    @DisplayName("crisis keyword forces L5 even when the LLM under-rates it")
    void crisisKeywordOverridesLowLlmLevel() {
        assertThat(node.resolveLevel("1", "honestly I want to kill myself")).isEqualTo(5);
    }

    @Test
    @DisplayName("crisis keyword forces L5 even when the LLM response is unparseable")
    void crisisKeywordSurvivesParseFailure() {
        // The exact failure that the old code mishandled: LLM returns prose,
        // parseInt throws, old code defaulted to L1 and missed the crisis.
        assertThat(node.resolveLevel("I'm really concerned about you", "I don't want to live anymore"))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Chinese crisis phrasing is also detected")
    void detectsChineseCrisisPhrasing() {
        assertThat(node.resolveLevel("2", "我不想活了")).isEqualTo(5);
    }

    @Test
    @DisplayName("benign input with an unparseable LLM response stays low, not crisis")
    void benignParseFailureStaysLow() {
        assertThat(node.resolveLevel("hmm not sure", "just had a long day at the office"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("out-of-range numbers do not escalate a benign message")
    void outOfRangeNumberClampsLowForBenignInput() {
        assertThat(node.resolveLevel("9", "had coffee with a friend")).isEqualTo(1);
    }

    @Test
    @DisplayName("detectCrisisLevel returns 5 on crisis keywords and 0 otherwise")
    void detectCrisisLevelIsDeterministic() {
        assertThat(node.detectCrisisLevel("thinking about ending it all")).isEqualTo(5);
        assertThat(node.detectCrisisLevel("a normal stressful day")).isEqualTo(0);
    }
}
