package com.ling.linginnerflow.pattern.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstainGateTest {
    @Test
    void parseLabelDecision() {
        AbstainGate gate = new AbstainGate(null, null);

        AbstainGate.AbstainResult result = gate.parse("prefix {\"decision\":\"LABEL\",\"primary_reason\":\"MODEL_UNCERTAIN\",\"fit_score\":0.91,\"specificity_score\":0.82,\"matched_evidence_shape\":\"Avoids submitting work\",\"supporting_quote\":\"I cannot send it yet\",\"rationale\":\"direct fit\"} suffix");

        assertThat(result.decision()).isEqualTo(AbstainDecision.LABEL);
        assertThat(result.fitScore()).isEqualTo(0.91);
        assertThat(result.specificityScore()).isEqualTo(0.82);
        assertThat(result.matchedEvidenceShape()).isEqualTo("Avoids submitting work");
        assertThat(result.supportingQuote()).isEqualTo("I cannot send it yet");
        assertThat(result.rationale()).isEqualTo("direct fit");
    }

    @Test
    void parseAbstainReason() {
        AbstainGate gate = new AbstainGate(null, null);

        AbstainGate.AbstainResult result = gate.parse("{\"decision\":\"ABSTAIN_NO_SAFE_V1_LABEL\",\"primary_reason\":\"DECOY_MATCH\",\"fit_score\":0.22,\"specificity_score\":0.31,\"rationale\":\"performed self-criticism\"}");

        assertThat(result.decision()).isEqualTo(AbstainDecision.DECOY_MATCH);
        assertThat(result.fitScore()).isEqualTo(0.22);
        assertThat(result.specificityScore()).isEqualTo(0.31);
    }

    @Test
    void malformedOutputBecomesSystemError() {
        AbstainGate gate = new AbstainGate(null, null);

        AbstainGate.AbstainResult result = gate.parse("not json");

        assertThat(result.decision()).isEqualTo(AbstainDecision.SYSTEM_ERROR);
    }
}
