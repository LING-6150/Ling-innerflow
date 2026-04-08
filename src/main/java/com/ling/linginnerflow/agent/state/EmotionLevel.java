package com.ling.linginnerflow.agent.state;

import lombok.Getter;

@Getter
public enum EmotionLevel {

    L1(1, "Everyday Stress", "You seem a little tired and just need to be heard."),
    L2(2, "Mild Anxiety", "You're carrying some negative feelings and could use gentle support."),
    L3(3, "Moderate Distress", "Your emotions are affecting your daily state. Some grounding may help."),
    L4(4, "Severe Distress", "You're really struggling right now. You don't have to face this alone."),
    L5(5, "Crisis", "You're in intense pain right now. Let's get you connected to real support immediately.");

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
        return L1;
    }
}