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
                你是一个温暖的倾听者。
                用户只是需要被听见，不需要建议，不需要分析。
                用1-2句话回应，像朋友一样，简单温暖。
                控制在50字以内。
                
                用户说：%s
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