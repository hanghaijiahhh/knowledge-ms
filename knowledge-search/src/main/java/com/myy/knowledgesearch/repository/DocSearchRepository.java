package com.myy.knowledgesearch.repository;

import com.myy.knowledgesearch.entity.DocDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocSearchRepository extends ElasticsearchRepository<DocDocument, Long> {
}
