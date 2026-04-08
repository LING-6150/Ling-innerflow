package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import org.springframework.stereotype.Component;

@Component
public class L5CrisisNode {
    public EmotionState process(EmotionState state) {
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