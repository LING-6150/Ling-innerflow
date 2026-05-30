package com.ling.linginnerflow.pattern.definition;

import lombok.Data;

import java.util.List;

/**
 * Mirrors the PatternDefinition YAML schema (product §8.1 + engine §3.5).
 * Loaded at startup by {@link PatternDefinitionLoader}; never user-specific.
 */
@Data
public class PatternDefinition {

    /** Stable system-internal identifier, e.g. {@code people_pleasing}. */
    private String patternKey;

    /** Chinese display name shown in the Insight Panel. */
    private String displayNameZh;

    /** English display name. */
    private String displayNameEn;

    /**
     * The default primary domain for grouping.
     * One of: self, family, intimate, work, social, body.
     */
    private String primaryDomain;

    /**
     * Other domains where this pattern frequently surfaces.
     * May be null/empty for patterns with a single domain focus.
     */
    private List<String> alsoIn;

    /**
     * Plain-language, non-clinical description of the pattern.
     * Never references DSM, ICD, MBTI, attachment theory labels,
     * or any diagnostic terminology (product §13.2).
     */
    private String neutralDescription;

    /**
     * Three clarifications of what this pattern is NOT —
     * prevents labeling drift and diagnostic over-reach.
     */
    private List<String> whatItIsNot;

    /**
     * Three concrete behavioural shapes used as evidence retrieval anchors
     * and for HyDE expansion in evidence retrieval (engine §3.1).
     */
    private List<String> evidenceShapes;

    /**
     * Three reflective prompts offered to the user after they confirm
     * or partially confirm a pattern instance (product §10).
     */
    private List<String> reflectivePrompts;

    /**
     * Schema version. Increment triggers probe-vector recomputation
     * at startup (engine §1.5).
     */
    private int version;

    // ── Engine §3.5 additive fields ──────────────────────────────────────────

    /**
     * BM25 seed terms mixing Chinese and English — real phrases users write,
     * not abstract label names. Used to build the ES keyword query in
     * evidence retrieval (engine §3.2, step 2).
     */
    private List<String> lexicalCues;

    /**
     * Number of hypothetical first-person user utterances the HyDE prompt
     * should generate for this pattern during evidence retrieval (engine §3.1).
     * Defaults to 3 if absent in YAML.
     */
    private int hydeExemplars;
}
