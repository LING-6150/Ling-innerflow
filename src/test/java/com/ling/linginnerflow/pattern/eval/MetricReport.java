package com.ling.linginnerflow.pattern.eval;

public record MetricReport(
        double precision,
        double recall,
        double f1,
        double hardNegativeFPR
) {
}
