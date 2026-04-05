package com.ling.linginnerflow.agent.state;

import lombok.Data;

@Data
public class EmotionState {

    // 用户输入的原始文字
    private String userInput;

    // 情绪等级 L1-L5
    private int emotionLevel;

    // LLM分析的情绪描述
    private String emotionDescription;

    // 最终给用户的回复
    private String response;

    // 是否触发危机模式
    private boolean crisisMode = false;

    // 加在其他字段旁边
    private String userId;
}
