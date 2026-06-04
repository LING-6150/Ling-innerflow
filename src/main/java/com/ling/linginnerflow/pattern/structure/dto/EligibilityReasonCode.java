package com.ling.linginnerflow.pattern.structure.dto;

public enum EligibilityReasonCode {
    confirmed_pattern,
    partially_confirmed_pattern,
    insufficient_evidence,
    too_few_evidence_items,
    evidence_window_too_sparse,
    rejected_by_user,
    rejection_cooldown_active,
    archived,
    stale_evidence,
    awaiting_user_review,
    user_deferred_review,
    safety_blocked_crisis,
    unsupported_pattern_type,
    unsupported_source_type,
    structure_not_enabled
}
