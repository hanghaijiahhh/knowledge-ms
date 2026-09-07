package com.myy.knowledgeembedding.repository;

import com.myy.knowledgeembedding.entity.DocChunkDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocChunkRepository extends ElasticsearchRepository<DocChunkDocument, String> {

    List<DocChunkDocument> findByDocId(Long docId);

    void deleteByDocId(Long docId);
}
