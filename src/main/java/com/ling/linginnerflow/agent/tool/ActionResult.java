package com.ling.linginnerflow.agent.tool;

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

    // Markers used to classify a legacy observation STRING into a status. The
    // current tools encode failure/empty as text (e.g. "... failed",
    // "No relevant ... found"); this recovers a typed status from that text so
    // existing tools work unchanged. FAILURE is checked before PARTIAL.
    private static final String[] FAILURE_MARKERS = {
        "failed", "error", "exception", "unavailable", "tool not found"
    };
    private static final String[] PARTIAL_MARKERS = {
        "no relevant", "no sufficient", "not found", "no data", "no record",
        "no results", "couldn't find", "could not find", "empty"
    };

    /** Classify a legacy observation string into a typed ActionResult. */
    public static ActionResult fromObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return partial("");
        }
        String low = observation.toLowerCase();
        for (String m : FAILURE_MARKERS) {
            if (low.contains(m)) {
                return failure("tool_reported_failure", observation);
            }
        }
        for (String m : PARTIAL_MARKERS) {
            if (low.contains(m)) {
                return partial(observation);
            }
        }
        return success(observation);
    }
}
