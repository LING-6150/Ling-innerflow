package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class L2GuidanceNode {

    private final ChatClient.Builder chatClientBuilder;
    private final MemoryService memoryService;

    public EmotionState process(EmotionState state) {

        // 读取历史上下文
        String context = memoryService
                .buildContextPrompt(state.getUserId());

        String prompt = """
    You are a warm emotional companion.
    
    %s
    
    The user is feeling mildly anxious or low. They need to feel understood.
    
    Guidelines:
    1. If there's history, naturally continue the thread — don't repeat what you already know
    2. Empathize first (1 sentence — natural, not scripted)
    3. Invite them to share more with one gentle question (optional)
    4. No advice unless they ask
    5. Conversational tone, under 80 words
    
    User said: %s
    """.formatted(context, state.getUserInput());

        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        state.setResponse(response);
        return state;
    }
}