package com.ling.linginnerflow.pet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Red-team for the anti-dependency guard: dependency-creating greetings (EN+ZH)
 * must be rejected; mirror-style and self-efficacy greetings must pass — including
 * the double-edged "you don't need me" affirmation, which must NOT false-trigger.
 */
class PetGreetingGuardTest {

    private final PetGreetingGuard guard = new PetGreetingGuard();

    @Test
    @DisplayName("dependency-creating greetings are rejected (English)")
    void rejectsDependencyEnglish() {
        for (String bad : new String[]{
                "I really missed you! Come back to me soon.",
                "I need you here, please don't leave.",
                "You need me to feel better, so come talk to me.",
                "I've been so lonely without you.",
                "I was waiting for you all day.",
        }) {
            PetGreetingGuard.Verdict v = guard.check(bad);
            assertThat(v.ok()).as("should reject: %s", bad).isFalse();
            assertThat(v.violations()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("dependency-creating greetings are rejected (Chinese)")
    void rejectsDependencyChinese() {
        for (String bad : new String[]{
                "我好想你呀,快来陪我玩。",
                "没有你我好孤单,你需要我。",
                "别走,我一直在等你。",
        }) {
            assertThat(guard.check(bad).ok()).as("should reject: %s", bad).isFalse();
        }
    }

    @Test
    @DisplayName("mirror / self-efficacy greetings pass")
    void passesMirrorGreetings() {
        for (String ok : new String[]{
                "You leaned on your own breathing exercises this week — that's your strength showing.",
                "I'm here. No need to say anything yet.",
                "慢慢来,你自己在一点点好起来。",
                "Whenever you're ready, this is a space to check in with yourself.",
        }) {
            assertThat(guard.check(ok).ok()).as("should pass: %s", ok).isTrue();
        }
    }

    @Test
    @DisplayName("self-efficacy affirmation 'you don't need me' is NOT false-flagged")
    void doesNotFalseFlagNegatedNeed() {
        assertThat(guard.check("You don't need me to tell you you're doing okay.").ok()).isTrue();
    }

    @Test
    @DisplayName("null / blank is treated as clean (no crash)")
    void handlesNull() {
        assertThat(guard.check(null).ok()).isTrue();
        assertThat(guard.check("   ").ok()).isTrue();
    }
}
