package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalyzerNode {

    private final ChatClient.Builder chatClientBuilder;

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

        String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .trim();

        int level;
        try {
            level = Integer.parseInt(result);
            if (level < 1 || level > 5) level = 1;
        } catch (NumberFormatException e) {
            log.warn("Unexpected LLM response: {}, defaulting to L1", result);
            level = 1;
        }

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