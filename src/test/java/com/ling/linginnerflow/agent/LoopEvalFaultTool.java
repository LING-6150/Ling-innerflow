package com.ling.linginnerflow.agent;

import com.ling.linginnerflow.agent.tool.AgentTool;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fault-injecting {@link AgentTool} for the loop A/B eval. It produces a
 * controllable outcome so we can exercise the failure paths a real deployment
 * hits but a happy-path demo never does. The returned strings mirror what the
 * real tools emit (so the production classifier sees realistic input):
 *
 * <ul>
 *   <li>{@code SUCCESS}  → "Relevant CBT intervention:\n&lt;content&gt;"</li>
 *   <li>{@code PARTIAL}  → "No relevant CBT content found." (a real empty-result sentinel)</li>
 *   <li>{@code HARD_FAILURE} → "CBT knowledge base lookup failed." (a caught, reported failure)</li>
 *   <li>{@code THROW}    → throws RuntimeException (an UNcaught tool error)</li>
 *   <li>{@code TRANSIENT}→ throws for the first N calls, then SUCCESS (recoverable by retry)</li>
 *   <li>{@code FLAKY}    → throws with probability p per call (seeded; for variance study)</li>
 * </ul>
 */
final class LoopEvalFaultTool implements AgentTool {

    enum Behavior { SUCCESS, PARTIAL, HARD_FAILURE, THROW, TRANSIENT, FLAKY }

    static final String SUCCESS_BODY =
            "Relevant CBT intervention:\nName the thought, then test it against the evidence.";

    private final Behavior behavior;
    private final int failuresBeforeSuccess;   // for TRANSIENT
    private final double failureProb;          // for FLAKY
    private final Random rng;                   // seeded; for FLAKY
    private final AtomicInteger calls = new AtomicInteger();

    private LoopEvalFaultTool(Behavior b, int failuresBeforeSuccess, double failureProb, Random rng) {
        this.behavior = b;
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        this.failureProb = failureProb;
        this.rng = rng;
    }

    static LoopEvalFaultTool of(Behavior b) {
        return new LoopEvalFaultTool(b, 0, 0.0, null);
    }

    /** Throws on the first {@code n} calls, then succeeds — recoverable by a retry. */
    static LoopEvalFaultTool transient_(int n) {
        return new LoopEvalFaultTool(Behavior.TRANSIENT, n, 0.0, null);
    }

    /** Throws with probability {@code p} on every call, using a seeded RNG. */
    static LoopEvalFaultTool flaky(double p, long seed) {
        return new LoopEvalFaultTool(Behavior.FLAKY, 0, p, new Random(seed));
    }

    /**
     * Throws with probability {@code p}, drawing from a CALLER-OWNED RNG so a whole
     * batch shares one well-distributed stream (reseeding per draw is biased).
     */
    static LoopEvalFaultTool flaky(double p, Random sharedRng) {
        return new LoopEvalFaultTool(Behavior.FLAKY, 0, p, sharedRng);
    }

    int callCount() {
        return calls.get();
    }

    @Override
    public String getName() {
        return "LoopEvalFaultTool";
    }

    @Override
    public String getDescription() {
        return "Fault-injecting tool for the loop A/B eval.";
    }

    @Override
    public String execute(String input) {
        int n = calls.incrementAndGet();
        switch (behavior) {
            case SUCCESS:
                return SUCCESS_BODY;
            case PARTIAL:
                return "No relevant CBT content found.";
            case HARD_FAILURE:
                return "CBT knowledge base lookup failed.";
            case THROW:
                throw new RuntimeException("simulated uncaught tool crash");
            case TRANSIENT:
                if (n <= failuresBeforeSuccess) {
                    throw new RuntimeException("simulated transient failure (call " + n + ")");
                }
                return SUCCESS_BODY;
            case FLAKY:
                if (rng.nextDouble() < failureProb) {
                    throw new RuntimeException("simulated flaky failure");
                }
                return SUCCESS_BODY;
            default:
                throw new IllegalStateException("unknown behavior " + behavior);
        }
    }
}
