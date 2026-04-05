package com.ling.linginnerflow.rag;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * ES中存储的CBT知识文档
 * @Document对应ES的索引名innerflow-cbt-text
 */
@Data
@Document(indexName = "innerflow-cbt-text")
public class CBTDocument {

    @Id
    private String id;

    // 全文检索字段，使用ik中文分词
    // type=Text表示会被分词，适合全文搜索
    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    // 类别标签，type=Keyword不分词，适合精确匹配
    @Field(type = FieldType.Keyword)
    private String category;
}