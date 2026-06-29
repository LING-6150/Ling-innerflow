package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.ActionResult;
import com.ling.linginnerflow.agent.tool.ToolStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * A/B harness for the ReAct loop's tool-result handling.
 *
 * <p>It compares two loop arms over a fixed, pre-registered set of failure
 * scenarios, under two model profiles, with no live LLM (fully deterministic):
 *
 * <ul>
 *   <li><b>BASELINE — "assume-success":</b> the pre-#66 behavior. Call the tool
 *       once, feed its raw output back as the observation, no typed status, no
 *       retry, and an uncaught tool exception kills the turn.</li>
 *   <li><b>TREATMENT — "result-driven":</b> the #66 behavior. Call {@code tool.act()}
 *       (which turns a thrown exception into a typed FAILURE), retry once on FAILURE,
 *       and hand the model the real {@link ReActAgent#observationForModel} text
 *       (a recovery instruction on FAILURE — never the raw error string).</li>
 * </ul>
 *
 * <p>To avoid strawmanning the baseline, BOTH arms feed their text to the SAME
 * model. We run two model profiles:
 * <ul>
 *   <li><b>NAIVE</b> — recognizes only an explicitly framed signal ({@code [tool failed},
 *       {@code [partial result}); takes any other non-empty text at face value as
 *       usable content. This is the realistic "the LLM just continues on whatever
 *       string it got" failure mode.</li>
 *   <li><b>ROBUST</b> — additionally parses raw observation text as well as our own
 *       classifier would. This is a generous upper bound on model comprehension.</li>
 * </ul>
 * The gap under ROBUST isolates what typing/retry buys that no amount of model
 * comprehension can: robustness to uncaught exceptions and recovery of transient faults.
 */
final class LoopEvalHarness {

    enum Decision { USE_RESULT, RECOVER, HEDGE, CRASH }

    enum Arm { BASELINE, TREATMENT }

    enum Profile { NAIVE, ROBUST }

    /** A pre-registered scenario: a tool behavior + the ground-truth right end state. */
    record Scenario(String name, LoopEvalFaultTool tool, Decision expected) {}

    record TurnResult(Arm arm, Profile profile, String scenario,
                      Decision decision, boolean crashed, boolean retried,
                      boolean rawErrorLeaked, boolean correct, String feedback) {}

    // ── The two model profiles: feedback text → next-step decision ──────────

    static Decision decide(Profile profile, String feedback) {
        if (feedback != null) {
            if (feedback.startsWith("[tool failed")) return Decision.RECOVER;
            if (feedback.startsWith("[partial result")) return Decision.HEDGE;
        }
        if (profile == Profile.NAIVE) {
            // Over-confident: asserts on anything non-empty, hallucinates on empty.
            return Decision.USE_RESULT;
        }
        // ROBUST: parse the raw text as well as our classifier would.
        if (feedback == null || feedback.isBlank()) return Decision.HEDGE;
        ToolStatus s = ActionResult.fromObservation(feedback).status();
        return switch (s) {
            case SUCCESS -> Decision.USE_RESULT;
            case PARTIAL -> Decision.HEDGE;
            case FAILURE -> Decision.RECOVER;
        };
    }

    // ── The two loop arms ───────────────────────────────────────────────────

    private static TurnResult runBaseline(Profile profile, Scenario sc) {
        try {
            String obs = sc.tool().execute("in");          // once, no retry
            // assume-success: the raw tool text IS the observation handed back
            boolean leaked = ActionResult.fromObservation(obs).status() == ToolStatus.FAILURE;
            Decision d = decide(profile, obs);
            return new TurnResult(Arm.BASELINE, profile, sc.name(), d,
                    false, false, leaked, d == sc.expected(), obs);
        } catch (RuntimeException e) {
            // no safety net: an uncaught tool error kills the turn
            return new TurnResult(Arm.BASELINE, profile, sc.name(), Decision.CRASH,
                    true, false, false, Decision.CRASH == sc.expected(), null);
        }
    }

    private static TurnResult runTreatment(Profile profile, Scenario sc) {
        ActionResult ar = sc.tool().act("in");             // act() turns a throw into FAILURE
        boolean retried = false;
        if (ar.status() == ToolStatus.FAILURE) {           // retry once on FAILURE
            ar = sc.tool().act("in");
            retried = true;
        }
        String feedback = ReActAgent.observationForModel(ar);   // the REAL production feedback
        // by construction, FAILURE feedback never embeds the raw observation
        boolean leaked = ar.observation() != null && !ar.observation().isBlank()
                && ar.status() == ToolStatus.FAILURE
                && feedback.contains(ar.observation());
        Decision d = decide(profile, feedback);
        return new TurnResult(Arm.TREATMENT, profile, sc.name(), d,
                false, retried, leaked, d == sc.expected(), feedback);
    }

    static TurnResult run(Arm arm, Profile profile, Scenario sc) {
        return arm == Arm.BASELINE ? runBaseline(profile, sc) : runTreatment(profile, sc);
    }

    // ── The pre-registered failure-scenario set ──────────────────────────────
    // Fresh tool per scenario (stateful: TRANSIENT counts calls).

    static List<Scenario> scenarios() {
        List<Scenario> s = new ArrayList<>();
        s.add(new Scenario("success",        LoopEvalFaultTool.of(LoopEvalFaultTool.Behavior.SUCCESS),      Decision.USE_RESULT));
        s.add(new Scenario("partial_empty",  LoopEvalFaultTool.of(LoopEvalFaultTool.Behavior.PARTIAL),      Decision.HEDGE));
        s.add(new Scenario("hard_failure",   LoopEvalFaultTool.of(LoopEvalFaultTool.Behavior.HARD_FAILURE), Decision.RECOVER));
        s.add(new Scenario("uncaught_throw", LoopEvalFaultTool.of(LoopEvalFaultTool.Behavior.THROW),        Decision.RECOVER));
        s.add(new Scenario("transient_1",    LoopEvalFaultTool.transient_(1),                               Decision.USE_RESULT));
        return s;
    }

    // ── Aggregate metrics over the scenario set, per (arm, profile) ──────────

    record Metrics(Arm arm, Profile profile, int n, int correct,
                   int crashes, int rawErrorLeaks, int transientRecovered) {
        double correctRate() {
            return n == 0 ? 0 : (double) correct / n;
        }
    }

    static Metrics evaluate(Arm arm, Profile profile) {
        int correct = 0, crashes = 0, leaks = 0, transientRecovered = 0;
        List<Scenario> scs = scenarios();   // fresh tools each call
        for (Scenario sc : scs) {
            TurnResult r = run(arm, profile, sc);
            if (r.correct()) correct++;
            if (r.crashed()) crashes++;
            if (r.rawErrorLeaked()) leaks++;
            if (sc.name().equals("transient_1") && r.decision() == Decision.USE_RESULT) {
                transientRecovered++;
            }
        }
        return new Metrics(arm, profile, scs.size(), correct, crashes, leaks, transientRecovered);
    }

    // ── Flaky-tool variance study: retry turns failure prob p into ~p² ───────

    record FlakyStat(Arm arm, double p, double meanSuccess, double stdevSuccess, double theoretical) {}

    static FlakyStat flakyStudy(Arm arm, double p, int batches, int turnsPerBatch) {
        double[] rates = new double[batches];
        for (int b = 0; b < batches; b++) {
            // One RNG per batch, drawn sequentially (NOT reseeded per draw, which
            // is biased). Same seed per batch across arms => the first attempt of
            // each turn is paired between baseline and treatment.
            java.util.Random r = new java.util.Random(7_000_003L + b);
            int success = 0;
            for (int t = 0; t < turnsPerBatch; t++) {
                LoopEvalFaultTool tool = LoopEvalFaultTool.flaky(p, r);
                boolean ok;
                if (arm == Arm.BASELINE) {
                    try { tool.execute("in"); ok = true; } catch (RuntimeException e) { ok = false; }
                } else {
                    ActionResult ar = tool.act("in");
                    if (ar.status() == ToolStatus.FAILURE) ar = tool.act("in");  // retry once
                    ok = ar.status() != ToolStatus.FAILURE;
                }
                if (ok) success++;
            }
            rates[b] = (double) success / turnsPerBatch;
        }
        double mean = 0;
        for (double r : rates) mean += r;
        mean /= batches;
        double var = 0;
        for (double r : rates) var += (r - mean) * (r - mean);
        double stdev = Math.sqrt(var / batches);
        double theoretical = arm == Arm.BASELINE ? (1 - p) : (1 - p * p);
        return new FlakyStat(arm, p, mean, stdev, theoretical);
    }

    private LoopEvalHarness() {}
}
