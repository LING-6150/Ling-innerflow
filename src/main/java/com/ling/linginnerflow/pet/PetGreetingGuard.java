package com.ling.linginnerflow.pet;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 反依赖守卫 —— 把"镜子,不是奶嘴"从 prompt 约束升级成可验证的后置不变量。
 *
 * <p>{@link PetGreetingService} 的硬规则之一是:问候<b>绝不</b>制造依赖
 * ("我想你 / 快来陪我 / 你需要我")。该规则原本只写在 prompt 里,LLM 完全
 * 可能违反。本守卫在生成后做一道确定性 lint:命中依赖性措辞即判定违规,调用方
 * 据此 <b>fail-safe 回退</b>到安全模板,而不是把违规问候发给用户。
 *
 * <p>边界:只拦<b>无歧义</b>的依赖性措辞(故意排除"you don't need me"这类
 * 反向肯定句,避免误杀)。"问候是否只用了记忆里的事实"(grounding / 编造亲密)
 * 是上下文相关的,留给带 LLM 的 greeting eval 度量,不在此确定性层。
 */
public final class PetGreetingGuard {

    // 无歧义的"制造依赖"措辞(英 + 中)。均为连续子串,且不与反向肯定句冲突。
    private static final List<String> DEPENDENCY_MARKERS = List.of(
            // English
            "miss you", "missed you", "i miss you", "missing you",
            "come talk to me", "come back to me", "come see me", "please come",
            "i need you", "you need me", "don't leave", "do not leave",
            "stay with me", "i'm lonely", "i am lonely", "lonely without you",
            "waiting for you", "i was waiting", "can't wait to see you",
            "i was worried about you", "i'll be lonely", "without you i",
            // 中文
            "我想你", "想你了", "好想你", "我好想你", "来陪我", "陪陪我",
            "别走", "别离开", "我一直在等你", "我好孤单", "快来", "来看我",
            "离不开你", "我需要你", "你需要我", "想念你",
            // 危机期卖萌(L5 下任何"开心蹦跶"都是有害的)——保守拦一类自指卖萌
            "play with me"
    );

    private static final Pattern WS = Pattern.compile("\\s+");

    public Verdict check(String greeting) {
        if (greeting == null) {
            return new Verdict(true, List.of());
        }
        String normalized = WS.matcher(greeting).replaceAll(" ").trim().toLowerCase();
        List<String> hits = DEPENDENCY_MARKERS.stream()
                .filter(normalized::contains)
                .toList();
        return new Verdict(hits.isEmpty(), hits);
    }

    /** ok=true 表示无依赖性违规;violations 列出命中的措辞。 */
    public record Verdict(boolean ok, List<String> violations) {
    }
}
