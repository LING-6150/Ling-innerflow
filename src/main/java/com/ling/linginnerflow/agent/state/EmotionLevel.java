package com.ling.linginnerflow.agent.state;

import lombok.Getter;

@Getter
public enum EmotionLevel {

    L1(1, "日常压力", "你感觉有些疲惫，需要被倾听"),
    L2(2, "轻度焦虑", "你有些负面情绪，需要引导"),
    L3(3, "中度困扰", "你的情绪影响到了日常生活，需要CBT干预"),
    L4(4, "严重困扰", "你需要专业帮助"),
    L5(5, "危机状态", "你现在非常痛苦，我们需要立刻联系专业支持");

    private final int level;
    private final String name;
    private final String description;

    EmotionLevel(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    public static EmotionLevel fromLevel(int level) {
        for (EmotionLevel e : values()) {
            if (e.level == level) return e;
        }
        return L1; // 默认L1
    }
}