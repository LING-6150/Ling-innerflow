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
 * L4节点：严重困扰
 * 结合CBT知识库给予专业支持，同时引导寻求专业帮助
 *
 * 流程：
 * 1. 检索Pinecone找相关CBT知识
 * 2. 基于知识库给予有依据的支持
 * 3. 明确引导寻求专业帮助
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class L4ProfessionalNode {

    private final ChatClient.Builder chatClientBuilder;
    private final HybridSearchService hybridSearchService;
    private final Observations observations;

    public EmotionState process(EmotionState state) {

        // 检索相关CBT知识
        // 这里也改成hybrid research
        String cbtKnowledge = hybridSearchService.hybridSearch(state.getUserInput());

        String prompt;

        if (cbtKnowledge != null && !cbtKnowledge.isEmpty()) {
            log.info("L4 node: CBT knowledge found");
            prompt = """
    You are a warm, steady mental health companion.
    
    Here is relevant CBT knowledge:
    [CBT Reference]
    %s
    
    The user is in significant distress. They need to feel supported and safe.
    
    Guidelines:
    1. Lead with genuine warmth and presence (1-2 sentences — slow, steady tone)
    2. Offer one grounding technique from the CBT knowledge, woven in naturally (1-2 sentences)
    3. Gently but clearly encourage professional support (1 sentence)
    4. Provide crisis resources (required):
       - 988 Suicide & Crisis Lifeline: call or text 988
       - Crisis Text Line: text HOME to 741741
    5. Under 150 words. No rushing. No lecturing.
    
    User said: %s
    """.formatted(cbtKnowledge, state.getUserInput());

        } else {
            log.info("L4 node: no CBT knowledge found, falling back to LLM");
            prompt = """
    You are a warm, steady mental health companion.
    The user is in significant distress.
    
    Guidelines:
    1. Lead with genuine warmth and presence (1-2 sentences)
    2. Offer one simple grounding action (1 sentence)
    3. Gently encourage professional support (1 sentence)
    4. Provide crisis resources:
       - 988 Suicide & Crisis Lifeline: call or text 988
       - Crisis Text Line: text HOME to 741741
    5. Under 150 words
    
    User said: %s
    """.formatted(state.getUserInput());
        }

        observations.tagPrompt("emotion.l4.professional", "v1");
        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        state.setResponse(response);
        log.info("L4 RAG response generated.");
        return state;
    }
}
