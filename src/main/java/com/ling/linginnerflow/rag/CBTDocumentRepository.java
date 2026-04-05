package com.ling.linginnerflow.rag;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ES数据访问层
 * 继承ElasticsearchRepository，内置基础CRUD
 */
@Repository
public interface CBTDocumentRepository
        extends ElasticsearchRepository<CBTDocument, String> {

    // 全文检索content字段
    List<CBTDocument> findByContentContaining(String keyword);
}