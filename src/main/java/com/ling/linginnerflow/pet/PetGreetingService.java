// src/main/java/com/ling/linginnerflow/pet/PetGreetingService.java
package com.ling.linginnerflow.pet;

import com.ling.linginnerflow.memory.MemoryService;
import com.ling.linginnerflow.memory.UserMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 宠物「记忆感问候」—— 镜子模型。
 *
 * 设计原则（CBT 自我效能，而非依赖）：
 *   宠物是用户成长的「镜子」，不是用户情绪的「奶嘴」。
 *   问候把用户【自己的应对方式 / 进展】反射回去，强化"是你在变好"，
 *   绝不说"我想你 / 快来陪我"这类制造依赖的话。
 *
 * Wiki 有料 → LLM 生成镜子问候；Wiki 还空着 → 温和模板兜底，绝不编造亲密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetGreetingService {

    private final MemoryService memoryService;
    private final ChatClient.Builder chatClientBuilder;

    // 反依赖后置守卫：prompt 约束之外的一道确定性 fail-safe
    private final PetGreetingGuard guard = new PetGreetingGuard();

    public String greet(String userId) {
        UserMemory mem = memoryService.getLongMemory(userId);

        // 冷启动：没有记忆，或所有关键字段都空 → 温和模板，不假装认识用户
        if (mem == null || !hasMaterial(mem)) {
            return fallback(mem);
        }

        try {
            String prompt = buildMirrorPrompt(mem);
            String greeting = chatClientBuilder.build()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (!StringUtils.hasText(greeting)) {
                return fallback(mem);
            }
            // 去掉模型偶尔加的引号
            String cleaned = greeting.trim().replaceAll("^[\"“]|[\"”]$", "").trim();

            // 反依赖守卫:LLM 仍可能违反"绝不制造依赖"的硬规则 → fail-safe 回退
            PetGreetingGuard.Verdict verdict = guard.check(cleaned);
            if (!verdict.ok()) {
                log.warn("[PetGreeting] 反依赖守卫拦截,回退模板。violations={} greeting={}",
                        verdict.violations(), cleaned);
                return fallback(mem);
            }
            return cleaned;
        } catch (Exception e) {
            log.warn("[PetGreeting] LLM 生成失败，回退模板: {}", e.getMessage());
            return fallback(mem);
        }
    }

    /** Wiki 是否有足够的"自己的素材"可供反射 */
    private boolean hasMaterial(UserMemory m) {
        return StringUtils.hasText(m.getCoreStruggles())
                || StringUtils.hasText(m.getEffectiveCoping())
                || StringUtils.hasText(m.getProgressNotes())
                || StringUtils.hasText(m.getConversationSummary());
    }

    // package-private: 让同包的 greeting-eval 复用与生产完全一致的 prompt
    String buildMirrorPrompt(UserMemory m) {
        return """
        You are Flowy, a gentle companion in a CBT-based emotional support app.
        The user just opened your page. Greet them in their own emotional context.

        YOUR ROLE: you are a MIRROR of the user's own growth — never a substitute for it.

        HARD RULES (a violation breaks the product's purpose):
        - 1-2 short sentences, under 35 words total.
        - NEVER say you missed them. NEVER ask them to come talk to you.
          NEVER imply they need you or depend on you.
        - Reflect THEIR OWN coping and progress back to them, so they feel
          that THEY are the one growing — not that you are taking care of them.
        - Use ONLY the facts listed below. If a fact is missing, do not invent it.
        - Gently point back to their own capacity at the end, not to yourself.
        - Output ONLY the greeting text, in English. No quotes, no preamble.

        TONE: %s

        WHAT YOU REMEMBER ABOUT THEM (use selectively, naturally — not a list):
        - Core struggles: %s
        - What has worked for THEM (their own coping): %s
        - Recent progress: %s
        - Continuity: %s
        """.formatted(
                toneFor(m.getPersona()),
                orUnknown(m.getCoreStruggles()),
                orUnknown(m.getEffectiveCoping()),
                orUnknown(m.getProgressNotes()),
                continuityHint(m.getLastActiveAt())
        );
    }

    private String toneFor(String persona) {
        if (persona == null) persona = "WARM";
        return switch (persona) {
            case "QUIET"    -> "quiet and spare — very few words, calm and unintrusive presence";
            case "RATIONAL" -> "calm, clear and matter-of-fact, with light, grounded encouragement";
            default         -> "warm and tender, like a caring friend who truly knows them";
        };
    }

    /** 温和兜底，仍保留自我效能框架（"check in with yourself"），绝不假装熟络 */
    private String fallback(UserMemory mem) {
        String persona = mem != null ? mem.getPersona() : "WARM";
        if (persona == null) persona = "WARM";
        return switch (persona) {
            case "QUIET"    -> "I'm here. No need to say anything yet.";
            case "RATIONAL" -> "Hi, I'm Flowy. Whenever you're ready, this is a space to check in with yourself.";
            default         -> "Hi, I'm Flowy. There's no rush — I'm here whenever you'd like to check in with yourself.";
        };
    }

    private String orUnknown(String s) {
        return StringUtils.hasText(s) ? s.trim() : "(not known yet)";
    }

    /** 把 lastActiveAt 转成粗粒度连续性提示，避免模型精确报时 */
    private String continuityHint(LocalDateTime lastActive) {
        if (lastActive == null) return "(first time meeting — do not reference any past)";
        long days = Duration.between(lastActive, LocalDateTime.now()).toDays();
        if (days <= 0) return "you were both here earlier today";
        if (days == 1) return "it has been about a day since they last checked in";
        if (days <= 7) return "it has been a few days since they last checked in";
        return "it has been a while since they last checked in";
    }
}
