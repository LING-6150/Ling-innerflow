package com.ling.linginnerflow.agent.tool;

public interface AgentTool {
    String getName();
    String getDescription();

    /** Raw tool execution. Tools encode their own success/empty/failure as text. */
    String execute(String input);

    /**
     * Structured tool action: runs {@link #execute} and returns a typed
     * {@link ActionResult} so the ReAct loop can branch on SUCCESS/PARTIAL/FAILURE
     * instead of inferring the outcome from text. The default classifies the
     * legacy observation string and turns any thrown exception into FAILURE
     * (fail-safe — the loop must never see an uncaught tool error as "success").
     * A tool may override this to report a more precise status.
     */
    default ActionResult act(String input) {
        try {
            return ActionResult.fromObservation(execute(input));
        } catch (RuntimeException e) {
            return ActionResult.failure(
                e.getClass().getSimpleName(),
                "Tool '" + getName() + "' threw " + e.getClass().getSimpleName());
        }
    }
}
