package com.ling.linginnerflow.pattern.domain;

/**
 * The four source types that EvidenceItems may cite (§9).
 * Determines the icon and deep-link behavior in the Insight Panel evidence drawer.
 */
public enum SourceType {
    chat_message,
    journal_entry,
    checkin,
    wiki_fact
}
