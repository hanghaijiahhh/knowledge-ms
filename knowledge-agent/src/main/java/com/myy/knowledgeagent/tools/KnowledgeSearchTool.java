package com.myy.knowledgeagent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 工具① knowledge_search — 带权限过滤的知识库检索
 */
@Slf4j
@Component
public class KnowledgeSearchTool implements AgentTool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "knowledge_search";
    }

    @Override
    public String getDescription() {
        return "在知识库中检索文档，返回相关片段。仅返回当前用户有权限查看的内容。";
    }

    @Override
    public JSONObject getParametersSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject props = new JSONObject();
        JSONObject queryProp = new JSONObject();
        queryProp.put("type", "string");
        queryProp.put("description", "检索关键词或问题");
        props.put("query", queryProp);
        JSONObject topKProp = new JSONObject();
        topKProp.put("type", "integer");
        topKProp.put("description", "返回片段数量，默认5");
        props.put("topK", topKProp);
        schema.put("properties", props);
        schema.put("required", new String[]{"query"});
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        Long userId = (Long) params.get("userId");
        String token = (String) params.get("accessToken");
        int topK = params.containsKey("topK") ? ((Number) params.get("topK")).intValue() : 5;

        log.info("knowledge_search: query={}, userId={}, topK={}", query, userId, topK);

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "http://localhost:8080/search/vector?keyword=" + encodedQuery + "&topK=" + topK;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JSONObject result = JSON.parseObject(resp.getBody());
            if (result.getInteger("code") == 200 && result.get("data") != null) {
                return JSON.toJSONString(result.get("data"));
            }
            return "检索无结果";
        } catch (Exception e) {
            log.error("knowledge_search 失败", e);
            return "检索服务暂时不可用: " + e.getMessage();
        }
    }
}
