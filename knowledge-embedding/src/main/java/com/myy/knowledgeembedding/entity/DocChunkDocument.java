package com.myy.knowledgeembedding.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 文档分块 — 带向量嵌入的 ES 文档
 * <p>
 * embedding 字段通过 @Field(type = Dense_Vector, dims = 1024) 自动建为 dense_vector 映射。
 * 注意：若索引已按旧 mapping 创建过，需删除 knowledge_chunks 索引后重启服务重建。
 */
@Data
@Document(indexName = "knowledge_chunks")
public class DocChunkDocument {

    @Id
    private String chunkId;

    @Field(type = FieldType.Long)
    private Long docId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String chunkText;

    @Field(type = FieldType.Long)
    private Long categoryId;

    /**
     * 向量嵌入 — BGE-large-zh 模型输出 1024 维
     */
    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private float[] embedding;
}
