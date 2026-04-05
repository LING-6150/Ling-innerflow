package com.ling.linginnerflow.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

/**
 * 混合检索服务
 * 结合Pinecone向量检索 + ES关键词检索，用RRF算法融合结果
 *
 * 为什么要混合检索：
 * - 纯向量检索：擅长语义相似，但对精确关键词不敏感
 *   例如：用户说"我总是全或无地看待事情"，向量检索能找到语义相关的内容
 * - ES关键词检索：擅长精确匹配
 *   例如：用户直接说"全或无思维"，ES能精确命中CBT-001
 * - 混合：两者互补，召回更全面
 *
 * RRF（Reciprocal Rank Fusion）算法：
 * score = Σ 1/(k + rank)
 * k=60是经验值，rank是文档在各路检索中的排名
 * 排名越靠前，得分越高；两路都命中的文档得分叠加
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final CBTKnowledgeService cbtKnowledgeService;
    private final CBTDocumentRepository cbtDocumentRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // RRF的k值，经验值60，防止排名靠后的文档得分过高
    private static final int RRF_K = 60;

    // 最终返回的文档数量
    private static final int TOP_N = 3;

    /**
     * 混合检索主入口
     * 1. Pinecone向量检索
     * 2. ES关键词检索
     * 3. RRF融合排名
     * 4. 返回TopN结果
     */
    public String hybridSearch(String userInput) {
        try {
            // 第一路：Pinecone向量检索，返回文档ID列表（按相似度排序）
            List<String> pineconeIds = cbtKnowledgeService
                    .retrieveRelevantCBTIds(userInput);
            log.info("Pinecone命中: {}", pineconeIds);

            // 第二路：ES关键词检索，提取关键词做全文搜索
            List<String> esIds = esKeywordSearch(userInput);
            log.info("ES命中: {}", esIds);

            // 第三步：RRF融合
            List<String> mergedIds = rrfMerge(pineconeIds, esIds);
            log.info("RRF融合后排序: {}", mergedIds);

            // 第四步：从ES取出文档内容拼接返回
            return fetchContent(mergedIds);

        } catch (Exception e) {
            log.error("混合检索失败，降级为纯向量检索: {}", e.getMessage());
            // 降级：直接用原来的Pinecone检索
            return cbtKnowledgeService.retrieveRelevantCBT(userInput);
        }
    }

    /**
     * ES关键词检索
     * 把用户输入直接作为关键词搜索content字段
     */
    private List<String> esKeywordSearch(String userInput) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q
                            .match(m -> m
                                    .field("content")
                                    .query(userInput)
                                    .minimumShouldMatch("30%")
                            )
                    )
                    .withMaxResults(5)
                    .build();

            SearchHits<CBTDocument> hits = elasticsearchOperations
                    .search(query, CBTDocument.class);

            List<String> ids = new ArrayList<>();
            hits.forEach(hit -> ids.add(hit.getId()));

            log.info("ES全文检索命中: {} 条", ids.size());
            return ids;

        } catch (Exception e) {
            log.error("ES检索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * RRF融合算法
     * score(doc) = 1/(k + rank_pinecone) + 1/(k + rank_es)
     * 如果某路没有该文档，不计分
     */
    private List<String> rrfMerge(List<String> pineconeIds,
                                  List<String> esIds) {

        // 收集所有候选文档ID
        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(pineconeIds);
        allIds.addAll(esIds);

        // 计算每个文档的RRF分数
        Map<String, Double> scores = new HashMap<>();
        for (String id : allIds) {
            double score = 0.0;

            // Pinecone那路的贡献
            int pineconeRank = pineconeIds.indexOf(id);
            if (pineconeRank >= 0) {
                score += 1.0 / (RRF_K + pineconeRank + 1);
            }

            // ES那路的贡献
            int esRank = esIds.indexOf(id);
            if (esRank >= 0) {
                score += 1.0 / (RRF_K + esRank + 1);
            }

            scores.put(id, score);
        }

        // 按分数降序排序
        List<String> sorted = new ArrayList<>(scores.keySet());
        sorted.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));

        // 返回TopN
        return sorted.subList(0, Math.min(TOP_N, sorted.size()));
    }

    /**
     * 根据ID列表从ES取出文档内容
     */
    private String fetchContent(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            cbtDocumentRepository.findById(id).ifPresent(doc -> {
                sb.append(doc.getContent()).append("\n---\n");
            });
        }
        return sb.toString();
    }
}