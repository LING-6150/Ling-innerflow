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
        assertThat(ActionResult.fromObservation("No relevant CBT content found.").status())
                .isEqualTo(ToolStatus.PARTIAL);
        assertThat(ActionResult.fromObservation("No sufficient emotion records yet.").status())
                .isEqualTo(ToolStatus.PARTIAL);
        assertThat(ActionResult.fromObservation("Tool not found: Foo").status())
                .isEqualTo(ToolStatus.FAILURE);
        assertThat(ActionResult.fromObservation(null).status()).isEqualTo(ToolStatus.PARTIAL);
        assertThat(ActionResult.fromObservation("   ").status()).isEqualTo(ToolStatus.PARTIAL);
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
