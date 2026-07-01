package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.config.Observations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalyzerNode {

    /**
     * Deterministic crisis safety net. These phrases force L5 regardless of what
     * the LLM returns — crisis routing must never depend solely on an LLM
     * classification that can fail, be malformed, or be prompt-injected.
     */
    private static final List<String> CRISIS_MARKERS = List.of(
            // English
            "kill myself", "killing myself", "suicide", "suicidal",
            "end it all", "end my life", "ending it all", "take my own life",
            "want to die", "wanna die", "don't want to live", "do not want to live",
            "better off dead", "self-harm", "self harm", "hurt myself",
            "harm myself", "cut myself", "no reason to live",
            // Chinese
            "自杀", "想死", "不想活", "活不下去", "活着没意思", "没意思活",
            "结束生命", "结束自己", "伤害自己", "不想活了", "了结自己"
    );

    private static final Pattern FIRST_DIGIT_1_TO_5 = Pattern.compile("[1-5]");

    private final ChatClient.Builder chatClientBuilder;
    private final Observations observations;

    /**
     * Returns 5 when the raw user input contains a crisis marker, else 0.
     * Deterministic and LLM-independent.
     */
    int detectCrisisLevel(String userInput) {
        if (userInput == null) {
            return 0;
        }
        String normalized = userInput.toLowerCase();
        for (String marker : CRISIS_MARKERS) {
            if (normalized.contains(marker)) {
                return 5;
            }
        }
        return 0;
    }

    /**
     * Resolves the final emotion level from the LLM response and the raw input.
     *
     * Safety contract: the result is the MAX of (a) the level robustly parsed
     * from the LLM response and (b) the deterministic crisis-keyword level. A
     * malformed / non-numeric LLM response can never downgrade a crisis to L1.
     */
    int resolveLevel(String llmRaw, String userInput) {
        int crisisLevel = detectCrisisLevel(userInput);

        int llmLevel = 1;
        if (llmRaw != null) {
            Matcher m = FIRST_DIGIT_1_TO_5.matcher(llmRaw);
            if (m.find()) {
                llmLevel = Integer.parseInt(m.group());
            } else {
                log.warn("Unexpected LLM response: {}, no 1-5 level found; falling back to keyword net", llmRaw);
            }
        }

        return Math.max(llmLevel, crisisLevel);
    }

    public EmotionState analyze(EmotionState state) {
        String prompt = """
    You are an emotion analysis assistant.
    Analyze the emotional severity of the following user input and return a single number from 1 to 5.
    
    1 = Everyday stress, neutral mood, occasionally tired, not affecting daily life
    2 = Mild anxiety, some negative emotions, but mostly functioning normally
    3 = Moderate distress, noticeable negative thought patterns (self-criticism, cognitive distortions),
        emotions affecting daily state, but not at a breaking point
    4 = Severe distress, feeling broken, hopeless, or unable to cope, intense suffering
    5 = Crisis state, thoughts of self-harm
    
    Important notes:
    - Self-criticism, cognitive distortions, negative thought patterns = L3
    - Only assign L4 for very severe suffering
    - Do NOT assign L4 unless the user explicitly says they're breaking down, hopeless, or can't go on
    
    User input: %s
    
    Return only the number 1-5. No other text.
    """.formatted(state.getUserInput());

        ChatClient chatClient = chatClientBuilder.build();

        observations.tagPrompt("emotion.analyzer", "v1");
        String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        int level = resolveLevel(result, state.getUserInput());

        state.setEmotionLevel(level);
        state.setEmotionDescription(
                com.ling.linginnerflow.agent.state.EmotionLevel
                        .fromLevel(level).getDescription()
        );

        if (level == 5) {
            state.setCrisisMode(true);
        }

        log.info("Emotion analysis: input={}, result=L{}", state.getUserInput(), level);
        return state;
    }
}
