package com.ling.linginnerflow.rag;

import com.google.protobuf.Struct;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * CBT知识库服务
 * 职责：把CBT内容向量化存入Pinecone，以及根据用户输入检索相关CBT知识
 *
 * 工作流程：
 * 1. 上传阶段：CBT文本 → OpenAI Embedding → 向量 → 存入Pinecone
 * 2. 检索阶段：用户输入 → OpenAI Embedding → 向量 → Pinecone相似度查询 → 返回相关CBT内容
 */
@Slf4j
@Service
public class CBTKnowledgeService {

    // Spring AI的Embedding模型，用于把文本转成向量
    private final EmbeddingModel embeddingModel;

    // Pinecone索引连接
    private final Index pineconeIndex;

    // 从配置文件读取，方便调优
    private final int topK;
    private final double scoreThreshold;

    /**
     * 构造函数注入
     * 用全限定名@org.springframework...Value避免和protobuf.Value命名冲突
     */
    public CBTKnowledgeService(
            EmbeddingModel embeddingModel,
            @org.springframework.beans.factory.annotation.Value("${pinecone.api-key}")
            String apiKey,
            @org.springframework.beans.factory.annotation.Value("${pinecone.index-name}")
            String indexName,
            @org.springframework.beans.factory.annotation.Value("${rag.pinecone.top-k:3}")
            int topK,
            @org.springframework.beans.factory.annotation.Value("${rag.pinecone.score-threshold:0.7}")
            double scoreThreshold) {

        this.embeddingModel = embeddingModel;
        this.topK = topK;
        this.scoreThreshold = scoreThreshold;

        // 初始化Pinecone客户端并获取索引连接
        Pinecone pinecone = new Pinecone.Builder(apiKey).build();
        this.pineconeIndex = pinecone.getIndexConnection(indexName);
    }

    /**
     * 根据用户输入检索相关CBT知识
     *
     * @param userInput 用户输入的情绪描述
     * @return 相关CBT知识文本，如果没找到返回空字符串
     */
    public String retrieveRelevantCBT(String userInput) {
        try {
            // 第一步：把用户输入向量化
            List<Float> embeddingList = toFloatList(
                    embeddingModel.embed(userInput));

            // 第二步：Pinecone相似度查询
            // topK从配置读取，默认3条
            QueryResponseWithUnsignedIndices response =
                    pineconeIndex.query(topK, embeddingList, null, null, null,
                            null, null, true, true);

            // 第三步：过滤并拼接结果
            // scoreThreshold从配置读取，默认0.7，低于此值说明不够相关
            StringBuilder sb = new StringBuilder();
            response.getMatchesList().forEach(match -> {
                if (match.getScore() > scoreThreshold) {
                    Struct metadata = match.getMetadata();
                    String text = metadata.getFieldsMap()
                            .getOrDefault("text",
                                    com.google.protobuf.Value.newBuilder()
                                            .setStringValue("").build())
                            .getStringValue();
                    sb.append(text).append("\n---\n");
                }
            });

            String result = sb.toString();
            log.info("CBT检索完成，命中 {} 条，相似度>{} 的内容长度: {} 字符",
                    response.getMatchesList().size(), scoreThreshold, result.length());
            return result.isEmpty() ? "" : result;

        } catch (Exception e) {
            // 检索失败不影响主流程，降级为纯LLM回复
            log.error("Pinecone检索失败，降级为纯LLM回复: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 上传CBT知识内容到Pinecone
     *
     * @param id   唯一标识，如 "CBT-001"
     * @param text CBT知识文本内容
     */
    public void upsertCBTContent(String id, String text) {
        try {
            List<Float> embeddingList = toFloatList(
                    embeddingModel.embed(text));

            // 原始文本存在metadata里，检索时取出来用
            Struct metadata = Struct.newBuilder()
                    .putFields("text",
                            com.google.protobuf.Value.newBuilder()
                                    .setStringValue(text).build())
                    .build();

            // upsert：有就更新，没有就插入
            pineconeIndex.upsert(id, embeddingList,
                    null, null, metadata, null);

            log.info("CBT内容上传成功: id={}, 文本长度={}字符",
                    id, text.length());
        } catch (Exception e) {
            log.error("Pinecone上传失败: id={}, 错误={}", id, e.getMessage());
        }
    }

    /**
     * 检索相关CBT知识，返回ID列表（供混合检索使用）
     */
    public List<String> retrieveRelevantCBTIds(String userInput) {
        return retrieveIdsByVector(toFloatList(embeddingModel.embed(userInput)), topK);
    }

    /**
     * Pinecone search using a pre-computed vector (used by HyDE so the caller
     * can supply the hypothetical-document vector instead of the raw query vector).
     *
     * @param vector  pre-computed embedding
     * @param k       number of candidates to retrieve
     */
    public List<String> retrieveIdsByVector(List<Float> vector, int k) {
        try {
            QueryResponseWithUnsignedIndices response =
                    pineconeIndex.query(k, vector, null, null, null,
                            null, null, true, true);

            List<String> ids = new ArrayList<>();
            response.getMatchesList().forEach(match -> {
                if (match.getScore() > scoreThreshold) {
                    ids.add(match.getId());
                }
            });

            log.info("Pinecone向量检索命中ID (k={}): {}", k, ids);
            return ids;

        } catch (Exception e) {
            log.error("Pinecone ID检索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 工具方法：float[]转List<Float>
     * 原因：Pinecone Java客户端接受List<Float>而不是float[]
     */
    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>();
        for (float f : arr) list.add(f);
        return list;
    }
}