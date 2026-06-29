package com.ling.linginnerflow.agent.tool;

import java.util.Set;

/**
 * Typed result of a tool action. Replaces "naked observation string" so the
 * ReAct loop can branch on {@link ToolStatus} instead of inferring the outcome
 * from text (which lets a model proceed on a hallucinated "success").
 *
 * @param status      the typed outcome
 * @param observation text to surface to the model (may be empty on FAILURE)
 * @param errorType   short machine-ish tag when status==FAILURE (else null)
 */
public record ActionResult(ToolStatus status, String observation, String errorType) {

    public static ActionResult success(String observation) {
        return new ActionResult(ToolStatus.SUCCESS, observation, null);
    }

    public static ActionResult partial(String observation) {
        return new ActionResult(ToolStatus.PARTIAL, observation, null);
    }

    public static ActionResult failure(String errorType, String observation) {
        return new ActionResult(ToolStatus.FAILURE, observation, errorType);
    }

    public boolean ok() {
        return status == ToolStatus.SUCCESS;
    }

    // The current tools encode failure/empty as a specific single-line wrapper
    // message, while SUCCESS content (retrieved CBT passages, the user's own
    // chat history, LLM-generated screening text) is free text that legitimately
    // contains words like "failed" or "error" (e.g. a CBT passage "...you feel
    // you've failed", or a user message "I failed my exam"). So we classify by
    // EXACT-matching the known wrapper messages on the FIRST line only — never by
    // scanning the body — so successful content can never be mislabeled FAILURE.
    // Unknown strings default to SUCCESS; genuine dispatch failures (tool missing,
    // thrown exception) are produced directly as FAILURE by the caller / act(),
    // not inferred here.
    private static final Set<String> FAILURE_SENTINELS = Set.of(
        "CBT knowledge base lookup failed.",
        "Emotion trend analysis failed.",
        "Failed to retrieve conversation history.",
        "Resource search failed."
    );
    private static final Set<String> PARTIAL_SENTINELS = Set.of(
        "No relevant CBT content found.",
        "No sufficient emotion records yet.",
        "Insufficient conversation history for PHQ-9 screening.",
        "No user messages found for PHQ-9 screening.",
        "No conversation history found."
    );

    /** Classify a known tool wrapper string into a typed ActionResult (first line only). */
    public static ActionResult fromObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return partial("");
        }
        String firstLine = observation.stripLeading().lines().findFirst().orElse("").strip();
        // PHQ-9 reports its failure as "PHQ-9 screening failed: <msg>" (dynamic tail).
        if (FAILURE_SENTINELS.contains(firstLine)
                || firstLine.startsWith("PHQ-9 screening failed:")) {
            return failure("tool_reported_failure", observation);
        }
        if (PARTIAL_SENTINELS.contains(firstLine)) {
            return partial(observation);
        }
        return success(observation);
    }
}
