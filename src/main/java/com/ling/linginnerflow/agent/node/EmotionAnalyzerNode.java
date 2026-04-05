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
        你是一个专业的情绪分析助手。
        请分析以下用户输入的情绪严重程度，返回一个1-5的数字：
        
        1 = 日常压力，心情一般，偶尔感到疲惫，不影响正常生活
        2 = 轻度焦虑，有些负面情绪，但基本能正常生活
        3 = 中度困扰，有明显的负面思维模式（如自我否定、认知扭曲），
            情绪影响到日常状态，但没有到崩溃的程度
        4 = 严重困扰，感到崩溃、绝望、撑不住，强烈痛苦
        5 = 危机状态，有伤害自己的念头
        
        重要提示：
        - 有自我否定、认知扭曲、负面思维模式 = L3
        - 只有非常严重的痛苦才是L4
        - 不要轻易判断L4，除非用户明确说崩溃/绝望/撑不住
        
        用户输入：%s
        
        只返回数字1-5，不要任何其他文字。
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
            log.warn("LLM返回格式异常: {}, 默认L1", result);
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

        log.info("LLM情绪分析: 输入={}, 结果=L{}", state.getUserInput(), level);
        return state;
    }
}