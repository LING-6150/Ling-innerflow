package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RouterNode {

    private final L1CompanionNode l1CompanionNode;
    private final L2GuidanceNode l2GuidanceNode;
    private final L3CBTNode l3CBTNode;
    private final L4ProfessionalNode l4ProfessionalNode;
    private final L5CrisisNode l5CrisisNode;

    public EmotionState route(EmotionState state) {
        int level = state.getEmotionLevel();
        log.info("路由到L{}节点", level);

        return switch (level) {
            case 1 -> l1CompanionNode.process(state);
            case 2 -> l2GuidanceNode.process(state);
            case 3 -> l3CBTNode.process(state);
            case 4 -> l4ProfessionalNode.process(state);
            case 5 -> l5CrisisNode.process(state);
            default -> l1CompanionNode.process(state);
        };
    }
}