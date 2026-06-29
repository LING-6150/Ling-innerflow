package com.ling.linginnerflow.agent.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionResultTest {

    @Test
    void classifiesRealToolReturnStrings() {
        assertThat(ActionResult.fromObservation("Relevant CBT intervention:\nbreathe slowly").status())
                .isEqualTo(ToolStatus.SUCCESS);
        assertThat(ActionResult.fromObservation("CBT knowledge base lookup failed.").status())
                .isEqualTo(ToolStatus.FAILURE);
        assertThat(ActionResult.fromObservation("Emotion trend analysis failed.").status())
                .isEqualTo(ToolStatus.FAILURE);
        // PHQ-9 reports failure with a dynamic tail.
        assertThat(ActionResult.fromObservation("PHQ-9 screening failed: connection reset").status())
                .isEqualTo(ToolStatus.FAILURE);
        assertThat(ActionResult.fromObservation("No relevant CBT content found.").status())
                .isEqualTo(ToolStatus.PARTIAL);
        assertThat(ActionResult.fromObservation("No sufficient emotion records yet.").status())
                .isEqualTo(ToolStatus.PARTIAL);
        assertThat(ActionResult.fromObservation(null).status()).isEqualTo(ToolStatus.PARTIAL);
        assertThat(ActionResult.fromObservation("   ").status()).isEqualTo(ToolStatus.PARTIAL);
    }

    /**
     * Regression (P1): successful tool content that happens to contain failure-like
     * words must NOT be classified as FAILURE. The checked-in CBT corpus contains
     * passages such as "...you feel you've failed" and "prediction error"; a body
     * scan would falsely flag a real, successful retrieval as a tool failure and feed
     * the model a fake recovery instruction instead of the CBT answer.
     */
    @Test
    void successfulContentWithFailureWordsIsNotFailure() {
        assertThat(ActionResult.fromObservation(
                "Relevant CBT intervention:\nIf someone is upset, you feel you've failed.").status())
                .isEqualTo(ToolStatus.SUCCESS);
        assertThat(ActionResult.fromObservation(
                "Relevant CBT intervention:\nThis is called a prediction error in CBT terms.").status())
                .isEqualTo(ToolStatus.SUCCESS);
        // Retrieved user history can quote the user's own words about failing.
        assertThat(ActionResult.fromObservation(
                "Recent conversation history:\nUser: I failed my exam and feel like an error.").status())
                .isEqualTo(ToolStatus.SUCCESS);
    }

    @Test
    void actFailsSafeWhenToolThrows() {
        AgentTool throwing = new AgentTool() {
            public String getName() { return "T"; }
            public String getDescription() { return ""; }
            public String execute(String i) { throw new RuntimeException("boom"); }
        };
        ActionResult ar = throwing.act("x");
        assertThat(ar.status()).isEqualTo(ToolStatus.FAILURE);
        assertThat(ar.errorType()).isEqualTo("RuntimeException");
    }

    @Test
    void actClassifiesSuccessfulExecute() {
        AgentTool ok = new AgentTool() {
            public String getName() { return "T"; }
            public String getDescription() { return ""; }
            public String execute(String i) { return "Relevant intervention: take a breath"; }
        };
        assertThat(ok.act("x").status()).isEqualTo(ToolStatus.SUCCESS);
    }
}
