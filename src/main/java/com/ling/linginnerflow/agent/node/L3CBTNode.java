package com.ling.linginnerflow.agent.node;

import com.ling.linginnerflow.agent.state.EmotionState;
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

    public EmotionState process(EmotionState state) {

        // 第一步：从Pinecone检索相关CBT知识
        // 这里修改成了 hybrid research
        String cbtKnowledge = hybridSearchService.hybridSearch(state.getUserInput());


        String prompt;

        if (cbtKnowledge != null && !cbtKnowledge.isEmpty()) {
            // 有检索结果：基于CBT知识库回复（GraphRAG模式）
            log.info("L3节点：命中CBT知识库，基于知识库生成回复");
            prompt = """
                你是一个温暖专业的心理支持助手，擅长认知行为疗法（CBT）。
                
                以下是与用户情况相关的CBT专业知识，请基于这些知识给予支持：
                
                【CBT知识参考】
                %s
                
                用户正在经历中度情绪困扰，请基于以上CBT知识给予回复。
                
                要求：
                1. 先表达理解和共情（1-2句）
                2. 结合CBT知识中的具体方法引导用户（1-2句，要自然融入，不要生硬引用）
                3. 给出一个具体可操作的小行动建议（1句）
                4. 语气温暖，像朋友在聊天，控制在120字以内
                
                用户说：%s
                """.formatted(cbtKnowledge, state.getUserInput());
        } else {
            // 没有检索结果：降级为纯LLM回复
            log.info("L3节点：未命中知识库，降级为纯LLM回复");
            prompt = """
                你是一个温暖专业的心理支持助手，擅长认知行为疗法（CBT）。
                
                用户正在经历中度情绪困扰，请给予CBT风格的支持。
                
                要求：
                1. 先表达理解和共情（1-2句）
                2. 用CBT思路引导用户识别负面思维（1-2句）
                3. 给出一个具体可操作的小行动建议（1句）
                4. 语气温暖，控制在120字以内
                
                用户说：%s
                """.formatted(state.getUserInput());
        }

        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content();

        state.setResponse(response);
        log.info("L3 CBT RAG回复生成完成");
        return state;
    }
}