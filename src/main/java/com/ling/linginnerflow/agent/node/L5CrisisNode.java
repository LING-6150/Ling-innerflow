package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import org.springframework.stereotype.Component;

@Component
public class L5CrisisNode {
    public EmotionState process(EmotionState state) {
        state.setCrisisMode(true);
        state.setResponse(
                "【重要】我非常担心你现在的状态。\n" +
                        "请立刻拨打以下热线，有人会帮助你：\n" +
                        "• 北京心理危机热线：010-82951332\n" +
                        "• 全国心理援助热线：400-161-9995\n" +
                        "• 生命热线：400-821-1215\n" +
                        "你不是一个人，请现在就拨打。"
        );
        return state;
    }
}
