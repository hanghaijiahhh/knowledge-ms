package com.myy.knowledgesearch.service;

import com.myy.knowledgesearch.entity.DocDocument;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public interface SearchService {

    /** 全文检索（含权限过滤+高亮） */
    Map<String, Object> searchWithHighlight(String keyword, int pageNum, int pageSize, String userInfo);

    /** 全文检索（分页，含权限过滤） */
    Page<DocDocument> search(String keyword, int pageNum, int pageSize, String userInfo);

    /** 向量语义检索（kNN，含权限过滤，按文档聚合） */
    Map<String, Object> vectorSearch(String keyword, int topK, String userInfo);

    /** 搜索建议（title 前缀匹配） */
    List<String> suggest(String prefix);

    /** 记录搜索词到热门统计 */
    void recordSearch(String keyword);
}
