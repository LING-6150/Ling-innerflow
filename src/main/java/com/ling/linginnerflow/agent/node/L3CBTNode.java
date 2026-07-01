package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
import com.ling.linginnerflow.config.Observations;
import com.ling.linginnerflow.rag.CBTKnowledgeService;
import com.ling.linginnerflow.rag.HybridSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * L3节点：中度困扰，接入CBT知识库RAG
 *
 * 流程：
 * 1. 用用户输入检索Pinecone，找相关CBT知识
 * 2. 把检索到的CBT知识注入Prompt
 * 3. LLM基于CBT知识生成有依据的回复
 * 4. 如果检索为空，降级为纯LLM回复
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L3CBTNode {

    private final ChatClient.Builder chatClientBuilder;
    private final HybridSearchService hybridSearchService;
    private final Observations observations;

    public EmotionState process(EmotionState state) {

        // 第一步：从Pinecone检索相关CBT知识
        // 这里修改成了 hybrid research
        String cbtKnowledge = hybridSearchService.hybridSearch(state.getUserInput());


        String prompt;

        if (cbtKnowledge != null && !cbtKnowledge.isEmpty()) {
            // 有检索结果：基于CBT知识库回复（GraphRAG模式）
            log.info("L3 node: CBT knowledge found, generating RAG response");
            prompt = """
    You are a warm, knowledgeable mental health companion.
    
    Here is relevant CBT knowledge that may apply to this situation:
    
    [CBT Reference]
    %s
    
    The user is experiencing moderate emotional distress.
    
    Guidelines:
    1. Empathize first — make them feel seen (1-2 sentences, natural)
    2. Weave in a relevant CBT insight naturally — don't quote it directly
    3. Offer one small, specific action they can try right now (1 sentence)
    4. Warm, conversational tone. Under 120 words.
    
    User said: %s
    """.formatted(cbtKnowledge, state.getUserInput());
        } else {
            // 没有检索结果：降级为纯LLM回复
            log.info("L3 node: no CBT knowledge found, falling back to LLM");
            prompt = """
    You are a warm, knowledgeable mental health companion.
    
    The user is experiencing moderate emotional distress.
    
    Guidelines:
    1. Empathize genuinely (1-2 sentences)
    2. Gently help them notice a thought pattern without labeling it (1-2 sentences)
    3. Offer one small, concrete action (1 sentence)
    4. Warm tone, under 120 words
    
    User said: %s
    """.formatted(state.getUserInput());
        }

        observations.tagPrompt("emotion.l3.cbt", "v1");
        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        state.setResponse(response);
        log.info("L3 CBT RAG response generated.");
        return state;
    }
}
