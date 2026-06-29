package com.ling.linginnerflow.agent.tool;

/**
 * Structured outcome of a tool call — so the ReAct loop decides on a REAL,
 * typed result instead of guessing success/failure from observation text.
 *
 * <ul>
 *   <li>{@code SUCCESS} — tool returned usable content.</li>
 *   <li>{@code PARTIAL} — tool ran but returned nothing useful / low-signal
 *       (e.g. retrieval empty, "No relevant ... found"): the model should
 *       proceed cautiously, not treat it as a confident answer.</li>
 *   <li>{@code FAILURE} — exception / timeout / malformed output / tool missing:
 *       the loop triggers recovery rather than feeding an error string back for
 *       the model to (mis)interpret.</li>
 * </ul>
 */
public enum ToolStatus {
    SUCCESS,
    PARTIAL,
    FAILURE
}
