package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.ActionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The loop's decide-by-status contract: the model gets a status-aware
 * observation, and on FAILURE it gets a recovery instruction — NOT the raw error
 * string it could mistake for real data.
 */
class ReActAgentStatusTest {

    @Test
    void successFeedsRawObservation() {
        assertThat(ReActAgent.observationForModel(ActionResult.success("here is the result")))
                .isEqualTo("here is the result");
    }

    @Test
    void failureFeedsRecoveryNotRawError() {
        String s = ReActAgent.observationForModel(
                ActionResult.failure("timeout", "RAW_ERROR_BLOB_FROM_TOOL"));
        assertThat(s).contains("tool failed").contains("do NOT assume it succeeded");
        // the model must NOT receive the raw error text as if it were data
        assertThat(s).doesNotContain("RAW_ERROR_BLOB_FROM_TOOL");
    }

    @Test
    void partialFeedsIncompleteCaveat() {
        String s = ReActAgent.observationForModel(ActionResult.partial("a little data"));
        assertThat(s).contains("partial result").contains("a little data");
    }
}
