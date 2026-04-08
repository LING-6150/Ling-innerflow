package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class L1CompanionNode {

    private final ChatClient.Builder chatClientBuilder;

    public EmotionState process(EmotionState state) {
        String prompt = """
    You are a warm, quiet listener.
    The user just needs to feel heard — no advice, no analysis.
    Respond in 1-2 sentences, like a friend. Keep it under 40 words.
    Don't ask a question. Just be present.
    
    User said: %s
    """.formatted(state.getUserInput());

        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        state.setResponse(response);
        return state;
    }
}