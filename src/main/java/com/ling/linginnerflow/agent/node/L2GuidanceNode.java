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
                你是一个温暖的情绪引导师。
                
                %s
                用户有轻度焦虑，需要被引导和安抚。
                
                要求：
                1. 如果有历史记录，自然地延续上下文，不要重复问已知信息
                2. 先共情（1句）
                3. 引导用户说出更多（1句问题）
                4. 可以提一个简单的放松建议
                5. 语气轻松，控制在80字以内
                
                用户说：%s
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