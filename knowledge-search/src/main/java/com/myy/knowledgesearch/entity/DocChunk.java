package com.myy.knowledgesearch.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 文档分块（对应 knowledge_chunks 索引）—— 仅用于向量检索结果映射
 * <p>
 * 不含 embedding 字段：dense_vector 默认不参与结果返回，且查询侧无需向量。
 */
@Data
@Document(indexName = "knowledge_chunks")
public class DocChunk {

    @Id
    private String chunkId;

    @Field(type = FieldType.Long)
    private Long docId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    @Field(type = FieldType.Text)
    private String chunkText;

    @Field(type = FieldType.Long)
    private Long categoryId;
}
