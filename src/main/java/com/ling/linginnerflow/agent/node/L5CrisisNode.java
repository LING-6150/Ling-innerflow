package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.config.Observations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class L5CrisisNode {

    private final Observations observations;

    public EmotionState process(EmotionState state) {
        observations.tagPrompt("emotion.l5.crisis", "v1");
        state.setCrisisMode(true);
        state.setResponse(
                "I'm really concerned about you right now.\n" +
                        "Please reach out to one of these crisis lines — someone is there to help:\n" +
                        "• 988 Suicide & Crisis Lifeline: call or text 988\n" +
                        "• Crisis Text Line: text HOME to 741741\n" +
                        "• NAMI Helpline: 1-800-950-6264\n" +
                        "You don't have to go through this alone. Please reach out now."
        );
        return state;
    }
}
