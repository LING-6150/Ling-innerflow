package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.CorpusRecord;
import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class B2_SinglePromptBaselineTest {
    private final PatternDefinitionLoader defs = loadedDefinitions();
    private final B2_SinglePromptBaseline baseline = new B2_SinglePromptBaseline(mock(ChatClient.Builder.class), defs);

    @Test
    void buildPromptContainsEveryPatternKeyVerbatim() {
        String prompt = baseline.buildPrompt(persona());

        assertThat(prompt).contains("strict JSON");
        assertThat(prompt).contains("pattern_key");
        assertThat(prompt).contains("2026-01-01 [journal] I keep replaying the conversation.");
        assertThat(defs.keys()).allSatisfy(key -> assertThat(prompt).contains(key));
    }

    @Test
    void parseResponseAcceptsValidJsonArray() {
        Set<PredictedPattern> parsed = baseline.parseResponse("["
                + "{\"pattern_key\":\"rumination\",\"domain\":\"self\"},"
                + "{\"pattern_key\":\"people_pleasing\",\"domain\":\"family\"}"
                + "]");

        assertThat(parsed).containsExactlyInAnyOrder(
                new PredictedPattern("rumination", Domain.self),
                new PredictedPattern("people_pleasing", Domain.family));
    }

    @Test
    void parseResponseAcceptsJsonWrappedInMarkdownFence() {
        Set<PredictedPattern> parsed = baseline.parseResponse("```json\n["
                + "{\"pattern_key\":\"avoidance\",\"domain\":\"social\"}"
                + "]\n```");

        assertThat(parsed).containsExactly(new PredictedPattern("avoidance", Domain.social));
    }

    @Test
    void parseResponseAcceptsJsonWithGarbagePrefixAndSuffix() {
        Set<PredictedPattern> parsed = baseline.parseResponse("Here is my answer:\n["
                + "{\"pattern_key\":\"perfectionism\",\"domain\":\"work\"}"
                + "]\nHope this helps.");

        assertThat(parsed).containsExactly(new PredictedPattern("perfectionism", Domain.work));
    }

    @Test
    void parseResponseSkipsMissingFieldsAndUnknownKeys() {
        Set<PredictedPattern> parsed = baseline.parseResponse("["
                + "{\"pattern_key\":\"invented_pattern\",\"domain\":\"self\"},"
                + "{\"pattern_key\":\"rumination\"},"
                + "{\"domain\":\"work\"},"
                + "{\"pattern_key\":\"self_criticism\",\"domain\":\"self\"}"
                + "]");

        assertThat(parsed).containsExactly(new PredictedPattern("self_criticism", Domain.self));
        assertThat(parsed).allSatisfy(pattern -> assertThat(defs.keys()).contains(pattern.patternKey()));
    }

    @Test
    void parseResponseNeverReturnsUnknownPatternKey() {
        Set<PredictedPattern> parsed = baseline.parseResponse("["
                + "{\"pattern_key\":\"unknown\",\"domain\":\"self\"},"
                + "{\"pattern_key\":\"comparison_loop\",\"domain\":\"body\"}"
                + "]");

        assertThat(parsed).containsExactly(new PredictedPattern("comparison_loop", Domain.body));
        assertThat(parsed).allSatisfy(pattern -> assertThat(defs.keys()).contains(pattern.patternKey()));
    }

    @Test
    void predictThrowsWhenLiveFlagIsDisabled() {
        assertThatThrownBy(() -> baseline.predict(persona()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("B2 requires -Dpattern.eval.b2.live=true (consumes network/tokens).");
    }

    @Disabled("requires live LLM, see PE-4 issue")
    @Test
    void predictCanRunAgainstLiveLlmWhenExplicitlyEnabled() {
        // Enable manually with -Dpattern.eval.b2.live=true and provide a real ChatClient.Builder bean/configuration.
        B2_SinglePromptBaseline liveBaseline = new B2_SinglePromptBaseline(mock(ChatClient.Builder.class), defs);
        liveBaseline.live = true;

        assertThat(liveBaseline.predict(persona())).allSatisfy(
                pattern -> assertThat(defs.keys()).contains(pattern.patternKey()));
    }

    private PatternDefinitionLoader loadedDefinitions() {
        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();
        return loader;
    }

    private GTPersona persona() {
        return new GTPersona(
                "fixture",
                "fixture",
                List.of(),
                List.of(),
                List.of(),
                List.of(new CorpusRecord(
                        LocalDate.of(2026, 1, 1),
                        "journal",
                        "I keep replaying the conversation.")));
    }
}
