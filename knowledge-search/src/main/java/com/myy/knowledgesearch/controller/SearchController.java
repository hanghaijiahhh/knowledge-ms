package com.myy.knowledgesearch.controller;

import com.myy.common.dto.ResponseResult;
import com.myy.knowledgesearch.entity.DocDocument;
import com.myy.knowledgesearch.repository.DocSearchRepository;
import com.myy.knowledgesearch.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;
    private final StringRedisTemplate redisTemplate;
    private final DocSearchRepository docSearchRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public SearchController(SearchService searchService, StringRedisTemplate redisTemplate,
                            DocSearchRepository docSearchRepository) {
        this.searchService = searchService;
        this.redisTemplate = redisTemplate;
        this.docSearchRepository = docSearchRepository;
    }

    @GetMapping
    public ResponseResult<Map<String, Object>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request) {
        String userInfo = request.getHeader("X-User-Info");
        Map<String, Object> result = searchService.searchWithHighlight(keyword, pageNum, pageSize, userInfo);
        // 有搜索结果才计入热搜，避免无匹配关键词污染热搜榜
        Object total = result.get("total");
        if (total instanceof Number && ((Number) total).longValue() > 0) {
            searchService.recordSearch(keyword);
        }
        return ResponseResult.success(result);
    }

    @GetMapping("/vector")
    public ResponseResult<Map<String, Object>> vectorSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int topK,
            HttpServletRequest request) {
        String userInfo = request.getHeader("X-User-Info");
        Map<String, Object> result = searchService.vectorSearch(keyword, topK, userInfo);
        return ResponseResult.success(result);
    }

    @GetMapping("/suggest")
    public ResponseResult<List<String>> suggest(@RequestParam String prefix) {
        return ResponseResult.success(searchService.suggest(prefix));
    }

    @GetMapping("/hot")
    public ResponseResult<List<Map<String, Object>>> hot() {
        String today = LocalDate.now().format(DATE_FMT);
        String key = "search:hot:" + today;

        var top = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, 9);

        List<Map<String, Object>> list = new ArrayList<>();
        if (top != null) {
            for (var item : top) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("keyword", item.getValue());
                entry.put("count", item.getScore().intValue());
                list.add(entry);
            }
        }
        return ResponseResult.success(list);
    }

    /** 管理员重建索引（从请求体接收文档列表，批量写入 ES） */
    @PostMapping("/admin/reindex")
    public ResponseResult<?> reindex(@RequestBody List<Map<String, Object>> docs) {
        int count = 0;
        for (Map<String, Object> d : docs) {
            try {
                DocDocument doc = new DocDocument();
                doc.setDocId(toLong(d.get("docId")));
                doc.setTitle(toStr(d.get("title")));
                doc.setContent(toStr(d.get("content")));
                doc.setCategoryId(toLong(d.get("categoryId")));
                doc.setStatus(toStr(d.get("status")));
                doc.setCreatorId(toLong(d.get("creatorId")));
                doc.setCreatorName(toStr(d.get("creatorName")));
                Object ct = d.get("createTime");
                if (ct instanceof String s) {
                    doc.setCreateTime(LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                }
                docSearchRepository.save(doc);
                count++;
            } catch (Exception e) {
                log.warn("reindex doc failed: docId={}, err={}", d.get("docId"), e.getMessage());
            }
        }
        return ResponseResult.success("reindexed " + count + " docs");
    }

    private String toStr(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.valueOf(obj.toString()); } catch (Exception e) { return 0L; }
    }
}
