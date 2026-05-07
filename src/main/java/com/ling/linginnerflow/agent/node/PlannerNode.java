package com.ling.linginnerflow.agent.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.agent.state.EmotionState;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Planning Agent：在情绪分析之后、响应节点之前执行。
 *
 * 不只看"这条消息是几级"，还综合：
 *  - 上一轮等级
 *  - 最近5轮轨迹（是否在升级/降级）
 * 输出结构化路由决策：targetLevel + strategy + toneHint
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerNode {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public EmotionState plan(EmotionState state) {
        int current = state.getEmotionLevel();

        // L5 不经过 Planner，直接走危机响应
        if (current == 5) {
            state.setTargetLevel(5);
            state.setStrategy("pure");
            state.setToneHint("Crisis state. Immediate safety response.");
            return state;
        }

        String trend = deriveTrend(current, state.getPreviousLevel(), state.getLevelHistory());
        String historyStr = formatHistory(state.getLevelHistory(), state.getPreviousLevel());

        String memorySection = buildMemorySection(state.getCoreStruggles(), state.getEmotionPattern());

        String prompt = """
                You are a clinical routing planner for a mental health AI companion.

                Your job: decide which emotional support mode to activate this round,
                considering not just the current score but the user's emotional trajectory.

                ── Current analysis ──
                Detected level : L%d
                Previous round : %s
                Recent history : %s
                Trend          : %s
                User message   : "%s"
                %s

                ── Support modes ──
                L1 = Everyday stress   → quiet companion, just be present
                L2 = Mild anxiety      → gentle guidance, warm empathy
                L3 = Moderate distress → CBT-informed reflection, name patterns
                L4 = Severe distress   → grounded stabilization, slow tone
                L5 = Crisis            → immediate safety response (never override)

                ── Strategy options ──
                pure         → standard response for targetLevel
                escalate     → user trending up; be more present/serious than level alone suggests
                de-escalate  → user calming down; softer/warmer to avoid abrupt tone shift
                blend        → borderline; acknowledge both current and adjacent level

                ── Rules ──
                1. If trajectory is rising (e.g. L1→L2→L3), prefer escalate even if current is L3
                2. If trajectory falling (e.g. L4→L3→L2), use de-escalate for smooth transition
                3. Never route below L2 if previousLevel was L4 or above
                4. If insufficient history, use pure strategy
                5. toneHint must be ≤ 20 words, a concrete instruction for the response node

                Return ONLY valid JSON, no markdown fences, no explanation:
                {
                  "targetLevel": <1-5>,
                  "strategy": "<pure|escalate|de-escalate|blend>",
                  "toneHint": "<short instruction>"
                }
                """.formatted(
                current,
                state.getPreviousLevel() == 0 ? "none (first message)" : "L" + state.getPreviousLevel(),
                historyStr,
                trend,
                state.getUserInput(),
                memorySection
        );

        try {
            String raw = chatClientBuilder.build()
                    .prompt().user(prompt).call().content();

            raw = raw.replaceAll("(?s)```json\\s*|```\\s*", "").trim();

            PlannerOutput output = objectMapper.readValue(raw, PlannerOutput.class);

            int target = Math.max(1, Math.min(5, output.getTargetLevel()));
            // Rule 3: never fall below L2 if coming from L4+
            if (state.getPreviousLevel() >= 4 && target < 2) target = 2;

            String strategy = isValidStrategy(output.getStrategy())
                    ? output.getStrategy() : "pure";
            String hint = output.getToneHint() != null
                    ? output.getToneHint().trim() : "";

            state.setTargetLevel(target);
            state.setStrategy(strategy);
            state.setToneHint(hint);

            log.info("[Planner] userId={} | analyzed=L{} → routed=L{} | strategy={} | trend={} | hint={}",
                    state.getUserId(), current, target, strategy, trend, hint);

        } catch (Exception e) {
            // 解析失败：降级为直接路由，不影响主流程
            log.warn("[Planner] Parse failed, falling back to raw level. error={}", e.getMessage());
            state.setTargetLevel(current);
            state.setStrategy("pure");
            state.setToneHint("");
        }

        return state;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private String deriveTrend(int current, int previous, List<Integer> history) {
        if (previous == 0 || history == null || history.size() < 2) {
            return "insufficient history";
        }
        if (current > previous) return "escalating";
        if (current < previous) return "de-escalating";
        // stable this round — check longer window
        if (history.size() >= 3) {
            int oldest = history.get(0);
            if (current > oldest) return "slowly escalating";
            if (current < oldest) return "slowly de-escalating";
        }
        return "stable";
    }

    private String formatHistory(List<Integer> history, int previous) {
        if ((history == null || history.isEmpty()) && previous == 0) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        if (history != null) {
            for (Integer l : history) sb.append("L").append(l).append(" → ");
        }
        if (previous > 0) sb.append("L").append(previous).append(" → ");
        sb.append("(current)");
        return sb.toString();
    }

    private String buildMemorySection(String coreStruggles, String emotionPattern) {
        boolean hasStruggles = coreStruggles != null && !coreStruggles.isBlank();
        boolean hasPattern   = emotionPattern != null && !emotionPattern.isBlank();
        if (!hasStruggles && !hasPattern) return "";
        StringBuilder sb = new StringBuilder("\n── Long-term user context ──\n");
        if (hasStruggles) sb.append("Core struggles  : ").append(coreStruggles).append("\n");
        if (hasPattern)   sb.append("Emotion pattern : ").append(emotionPattern).append("\n");
        sb.append("Use this to calibrate routing when trajectory is borderline.\n");
        return sb.toString();
    }

    private boolean isValidStrategy(String s) {
        return s != null && (s.equals("pure") || s.equals("escalate")
                || s.equals("de-escalate") || s.equals("blend"));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PlannerOutput {
        private int targetLevel;
        private String strategy;
        private String toneHint;
    }
}
