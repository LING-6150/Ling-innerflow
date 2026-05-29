package com.ling.linginnerflow.pattern.safety;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Language Firewall — V1 safety gate for Pattern Discovery (§13.1).
 *
 * <p>Enforces the product rule: the system must never use prohibited diagnostic
 * language in any surfaced {@code personalized_summary} or evidence
 * interpretation. Every generated text string that will be shown to a user
 * MUST pass through this firewall before being persisted or returned.
 *
 * <h2>Two-gate design</h2>
 * <ol>
 *   <li><b>Regex/substring blacklist (always active):</b> fast, cheap,
 *       offline-capable check that catches the most common violations
 *       including Chinese diagnostic phrasing and Latin clinical taxonomy
 *       tokens. Returns {@code false} from {@link #isClean(String)} on any
 *       hit.</li>
 *   <li><b>Optional LLM judge (disabled by default):</b> controlled by
 *       {@code pattern.firewall.llm-judge=true}. When enabled, text that
 *       passes the regex gate is sent to a cheap LLM call for a yes/no
 *       verdict. Default is {@code false} so tests run fully offline.</li>
 * </ol>
 *
 * <h2>Canonical safe phrasing</h2>
 * All generated pattern summaries must use or be consistent with
 * {@link #SAFE_PHRASING_TEMPLATE}. This template is the single authoritative
 * source of the approved non-diagnostic framing from product §13.1.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Check only:
 * if (!firewall.isClean(summary)) { regenerate(); }
 *
 * // Enforce (throws on violation):
 * String safe = firewall.enforce(summary);
 * }</pre>
 *
 * @see com.ling.linginnerflow.pattern.dedup.PatternDeduplicator
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageFirewall {

    // -------------------------------------------------------------------------
    // Canonical safe phrasing — product §13.1
    // -------------------------------------------------------------------------

    /**
     * Canonical non-diagnostic phrasing template from product spec §13.1.
     *
     * <p>All generated {@code personalized_summary} values must follow this
     * structure. Callers should substitute:
     * <ul>
     *   <li>{@code X} — the life-domain scene where the pattern appears
     *       (e.g. "和母亲的电话里")</li>
     *   <li>{@code Y} — a neutral, behavioural description of the recurring
     *       action (e.g. "反复压下自己的想法")</li>
     *   <li>{@code <pattern_display>} — the pattern's Chinese display name
     *       from {@code PatternDefinition.displayNameZh} (e.g. "讨好倾向")</li>
     * </ul>
     */
    public static final String SAFE_PHRASING_TEMPLATE =
            "我观察到在 X 场景里反复出现 Y。这个可能属于 <pattern_display> 类的 pattern，你怎么看？";

    // -------------------------------------------------------------------------
    // Blacklist patterns (§13.1 + §13.2)
    // -------------------------------------------------------------------------

    /**
     * Patterns for "你有…" and "你是…型" — direct second-person diagnostic
     * attribution. The lookahead ensures we only fire when the phrase is
     * followed by diagnostic nouns, not benign continuations.
     *
     * <p>Examples caught: "你有抑郁症", "你是回避型人格", "你有焦虑障碍".
     */
    private static final Pattern PATTERN_YOU_HAVE_DIAGNOSTIC =
            Pattern.compile("你有(?=[^，。]*(?:型|诊断|病|障碍|症))", Pattern.DOTALL);

    private static final Pattern PATTERN_YOU_ARE_TYPE =
            Pattern.compile("你是(?=[^，。]*(?:型|诊断|病|障碍|症))", Pattern.DOTALL);

    /**
     * Standalone diagnostic suffix tokens — prohibited regardless of the
     * subject phrase.
     */
    private static final List<String> SUBSTRING_BLACKLIST = List.of(
            "诊断",
            "人格障碍",
            "抑郁症",
            "焦虑症"
    );

    /**
     * Latin clinical taxonomy tokens — matched case-insensitively.
     * The product (§13.2) prohibits any reference to DSM, ICD, or pop-psych
     * typing systems by name.
     */
    private static final List<Pattern> LATIN_TOKEN_BLACKLIST = List.of(
            Pattern.compile("\\bDSM\\b",  Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bICD\\b",  Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bMBTI\\b", Pattern.CASE_INSENSITIVE)
    );

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    /**
     * When {@code true}, text that passes the regex gate is also sent to a
     * cheap LLM call for a secondary yes/no verdict. Default {@code false}
     * keeps tests fully offline and avoids API cost on every summary write.
     */
    @Value("${pattern.firewall.llm-judge:false}")
    private boolean llmJudgeEnabled;

    private final ChatClient.Builder chatClientBuilder;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code text} contains no prohibited diagnostic
     * language (regex gate passes, and optional LLM judge passes).
     *
     * <p>{@code null} or blank input is treated as clean (nothing to violate).
     *
     * @param text the generated summary or interpretation to check
     * @return {@code false} if any blacklist pattern matches or the LLM judge
     *         returns a violation; {@code true} otherwise
     */
    public boolean isClean(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        // Gate 1: regex / substring blacklist
        if (PATTERN_YOU_HAVE_DIAGNOSTIC.matcher(text).find()) {
            log.debug("[Firewall] Violation: '你有…diagnostic' pattern in text");
            return false;
        }
        if (PATTERN_YOU_ARE_TYPE.matcher(text).find()) {
            log.debug("[Firewall] Violation: '你是…type' pattern in text");
            return false;
        }
        for (String token : SUBSTRING_BLACKLIST) {
            if (text.contains(token)) {
                log.debug("[Firewall] Violation: prohibited token '{}' in text", token);
                return false;
            }
        }
        for (Pattern latin : LATIN_TOKEN_BLACKLIST) {
            if (latin.matcher(text).find()) {
                log.debug("[Firewall] Violation: prohibited Latin token '{}' in text", latin.pattern());
                return false;
            }
        }

        // Gate 2 (optional): LLM judge
        if (llmJudgeEnabled) {
            return llmJudgeIsClean(text);
        }

        return true;
    }

    /**
     * Returns {@code text} unchanged if it passes the firewall, or throws
     * {@link IllegalStateException} if any violation is detected.
     *
     * <p>Callers (e.g. {@code PatternDiscoveryService}) should catch the
     * exception, regenerate the summary (up to the spec-mandated 2 retries),
     * and discard the whole instance if still failing.
     *
     * @param text the text to validate
     * @return {@code text} unmodified, guaranteed clean
     * @throws IllegalStateException if the text contains prohibited language
     */
    public String enforce(String text) {
        if (!isClean(text)) {
            log.warn("[Firewall] Language firewall violation detected; regeneration required. "
                    + "Offending text prefix: '{}'",
                    text.length() > 80 ? text.substring(0, 80) + "…" : text);
            throw new IllegalStateException("language firewall violation");
        }
        return text;
    }

    // -------------------------------------------------------------------------
    // LLM judge (optional second gate)
    // -------------------------------------------------------------------------

    /**
     * Cheap single-turn LLM verdict: asks the model whether the text contains
     * any diagnostic or clinical labelling language.
     *
     * <p>Returns {@code true} (clean) on any exception so that an LLM outage
     * does not block the engine — the regex gate remains the hard safety
     * boundary.
     */
    private boolean llmJudgeIsClean(String text) {
        String prompt = """
                You are a safety reviewer for an AI self-reflection app.
                The app is NOT allowed to use diagnostic, clinical, or medical labels.
                Prohibited: any DSM/ICD/MBTI references, disorder names, disease names,
                personality-type labels, or direct statements like "you have X disorder."
                Allowed: behavioural observations, pattern names in plain language,
                neutral first-person reflective framing.

                Does the following text contain any prohibited diagnostic language?
                Answer ONLY with: YES (contains prohibited language) or NO (clean).

                Text to check:
                %s
                """.formatted(text);

        try {
            String verdict = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            boolean clean = verdict != null && verdict.strip().toUpperCase().startsWith("NO");
            if (!clean) {
                log.debug("[Firewall] LLM judge flagged text as containing prohibited language");
            }
            return clean;
        } catch (Exception e) {
            log.warn("[Firewall] LLM judge unavailable, falling back to regex-only verdict: {}", e.getMessage());
            return true; // fail-open: regex gate is the hard boundary
        }
    }
}
