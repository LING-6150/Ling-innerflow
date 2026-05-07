package com.ling.linginnerflow.agent.state;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class EmotionState {

    private String userInput;
    private int emotionLevel;
    private String emotionDescription;
    private String response;
    private boolean crisisMode = false;
    private String userId;

    // ── Planner fields ──────────────────────────────────────────────
    // 上一轮情绪等级（0 = 第一条消息，无历史）
    private int previousLevel = 0;
    // 最近5轮情绪等级，用于趋势判断
    private List<Integer> levelHistory = new ArrayList<>();
    // Planner 决定的目标路由等级（可能与 emotionLevel 不同）
    private int targetLevel = 0;
    // 路由策略：pure / escalate / de-escalate / blend
    private String strategy = "pure";
    // 传给响应节点的简短语气提示
    private String toneHint = "";

    // ── UserMemory context injected before Planner ──────────────────
    private String coreStruggles = "";
    private String emotionPattern = "";
}
