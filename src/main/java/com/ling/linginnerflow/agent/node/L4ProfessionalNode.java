package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
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

    public EmotionState process(EmotionState state) {

        // 检索相关CBT知识
        // 这里也改成hybrid research
        String cbtKnowledge = hybridSearchService.hybridSearch(state.getUserInput());

        String prompt;

        if (cbtKnowledge != null && !cbtKnowledge.isEmpty()) {
            log.info("L4节点：命中CBT知识库");
            prompt = """
                你是一个温暖专业的心理支持助手。
                
                以下是与用户情况相关的CBT专业知识：
                【CBT知识参考】
                %s
                
                用户正在经历严重的情绪困扰，请给予支持。
                
                要求：
                1. 先真诚表达理解和关怀（1-2句）
                2. 结合CBT知识给予一个具体的即时缓解建议（1-2句）
                3. 温和但明确地建议寻求专业帮助，并提供热线（1句）
                4. 语气温暖有力量，控制在150字以内
                
                危机热线（必须提供）：
                - 全国心理援助热线：400-161-9995
                - 北京心理危机热线：010-82951332
                
                用户说：%s
                """.formatted(cbtKnowledge, state.getUserInput());
        } else {
            log.info("L4节点：未命中知识库，降级纯LLM");
            prompt = """
                你是一个温暖专业的心理支持助手。
                用户正在经历严重的情绪困扰。
                
                要求：
                1. 真诚表达理解和关怀（1-2句）
                2. 给予一个即时缓解建议（1句）
                3. 明确建议寻求专业帮助（1句）
                4. 提供热线：全国心理援助热线400-161-9995
                5. 控制在150字以内
                
                用户说：%s
                """.formatted(state.getUserInput());
        }

        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        state.setResponse(response);
        log.info("L4 RAG回复生成完成");
        return state;
    }
}