package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.LoopEvalHarness.Arm;
import com.ling.linginnerflow.agent.LoopEvalHarness.FlakyStat;
import com.ling.linginnerflow.agent.LoopEvalHarness.Metrics;
import com.ling.linginnerflow.agent.LoopEvalHarness.Profile;
import org.junit.jupiter.api.Test;

import static com.ling.linginnerflow.agent.LoopEvalHarness.Arm.BASELINE;
import static com.ling.linginnerflow.agent.LoopEvalHarness.Arm.TREATMENT;
import static com.ling.linginnerflow.agent.LoopEvalHarness.Decision;
import static com.ling.linginnerflow.agent.LoopEvalHarness.Profile.NAIVE;
import static com.ling.linginnerflow.agent.LoopEvalHarness.Profile.ROBUST;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A/B eval: result-driven (treatment, #66) vs assume-success (baseline) loop
 * handling of tool outcomes, under a naive and a robust model. Deterministic —
 * no live LLM. Prints a report and asserts the value-claims so they can't
 * silently regress.
 */
class LoopAbEvalTest {

    @Test
    void abComparisonAndInvariants() {
        Metrics bNaive = LoopEvalHarness.evaluate(BASELINE, NAIVE);
        Metrics tNaive = LoopEvalHarness.evaluate(TREATMENT, NAIVE);
        Metrics bRobust = LoopEvalHarness.evaluate(BASELINE, ROBUST);
        Metrics tRobust = LoopEvalHarness.evaluate(TREATMENT, ROBUST);

        FlakyStat fb = LoopEvalHarness.flakyStudy(BASELINE, 0.30, 12, 500);
        FlakyStat ft = LoopEvalHarness.flakyStudy(TREATMENT, 0.30, 12, 500);

        System.out.println(report(bNaive, tNaive, bRobust, tRobust, fb, ft));

        // ── 1. Treatment is correct on every scenario, under BOTH models ──
        assertThat(tNaive.correctRate()).isEqualTo(1.0);
        assertThat(tRobust.correctRate()).isEqualTo(1.0);

        // ── 2. Treatment strictly beats baseline under both model profiles ──
        assertThat(tNaive.correctRate()).isGreaterThan(bNaive.correctRate());
        assertThat(tRobust.correctRate()).isGreaterThan(bRobust.correctRate());

        // ── 3. The robust-model gap isolates what comprehension can't fix:
        //       uncaught exceptions + transient faults are loop-structure bugs ──
        assertThat(bRobust.crashes()).isGreaterThanOrEqualTo(2);   // throw + transient
        assertThat(tNaive.crashes()).isZero();
        assertThat(tRobust.crashes()).isZero();

        // ── 4. Treatment never hands the model a raw error as if it were data ──
        assertThat(tNaive.rawErrorLeaks()).isZero();
        assertThat(tRobust.rawErrorLeaks()).isZero();
        assertThat(bNaive.rawErrorLeaks()).isGreaterThanOrEqualTo(1);

        // ── 5. Transient fault recovered by retry (treatment only) ──
        assertThat(tNaive.transientRecovered()).isEqualTo(1);
        assertThat(bNaive.transientRecovered()).isZero();

        // ── 6. No regression on the happy path: identical feedback bytes ──
        var successSc = LoopEvalHarness.scenarios().get(0);   // "success"
        String bFeed = LoopEvalHarness.run(BASELINE, NAIVE, successSc).feedback();
        var successSc2 = LoopEvalHarness.scenarios().get(0);
        String tFeed = LoopEvalHarness.run(TREATMENT, NAIVE, successSc2).feedback();
        assertThat(tFeed).isEqualTo(bFeed).isEqualTo(LoopEvalFaultTool.SUCCESS_BODY);

        // ── 7. Variance study: retry lifts success p→~(1-p²) and tightens spread ──
        assertThat(ft.meanSuccess()).isGreaterThan(fb.meanSuccess());
        assertThat(ft.stdevSuccess()).isLessThanOrEqualTo(fb.stdevSuccess() + 1e-9);
        assertThat(fb.meanSuccess()).isCloseTo(fb.theoretical(), org.assertj.core.data.Offset.offset(0.03));
        assertThat(ft.meanSuccess()).isCloseTo(ft.theoretical(), org.assertj.core.data.Offset.offset(0.03));
    }

    private static String report(Metrics bN, Metrics tN, Metrics bR, Metrics tR,
                                 FlakyStat fb, FlakyStat ft) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== ReAct loop A/B: result-driven (#66) vs assume-success ===\n");
        sb.append("Deterministic, no live LLM. 5 pre-registered scenarios x 2 model profiles.\n\n");
        sb.append(String.format("%-26s %8s %8s %8s %12s%n",
                "arm / model", "correct", "crashes", "err-leak", "transient✓"));
        sb.append("-".repeat(66)).append("\n");
        for (Metrics m : new Metrics[]{bN, tN, bR, tR}) {
            sb.append(String.format("%-26s %7.0f%% %8d %8d %12s%n",
                    m.arm() + " / " + m.profile(),
                    m.correctRate() * 100, m.crashes(), m.rawErrorLeaks(),
                    m.transientRecovered() + "/1"));
        }
        sb.append("\n=== Flaky-tool variance study (p=0.30 throw, 12 batches x 500 turns) ===\n");
        sb.append(String.format("%-12s %14s %14s %14s%n",
                "arm", "mean success", "stdev", "theoretical"));
        sb.append("-".repeat(56)).append("\n");
        for (FlakyStat f : new FlakyStat[]{fb, ft}) {
            sb.append(String.format("%-12s %12.1f%% %13.3f %12.1f%%%n",
                    f.arm(), f.meanSuccess() * 100, f.stdevSuccess(), f.theoretical() * 100));
        }
        sb.append("\nReading: retry-once turns an independent failure prob p into ~p^2,\n");
        sb.append("lifting success and shrinking run-to-run variance. The robust-model\n");
        sb.append("column shows typing/retry still wins where comprehension can't help:\n");
        sb.append("uncaught exceptions and transient faults are loop-structure problems.\n");
        sb.append("Decision labels: ").append(java.util.Arrays.toString(Decision.values())).append("\n");
        return sb.toString();
    }
}
