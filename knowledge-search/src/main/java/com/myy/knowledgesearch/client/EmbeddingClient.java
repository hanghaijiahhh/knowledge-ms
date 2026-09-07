package com.myy.knowledgesearch.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询侧向量化 —— 调用 Embedding API 将 query 转为向量
 * <p>
 * 与 embedding 服务的 EmbeddingService 逻辑一致，但场景不同：
 * 入库是批量异步向量化，查询是实时同步向量化，故在 search 服务内聚实现。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.model}")
    private String model;

    public List<Float> embed(String text) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    baseUrl + "/v1/embeddings",
                    HttpMethod.POST,
                    new HttpEntity<>(body.toJSONString(), headers),
                    String.class);

            JSONObject result = JSON.parseObject(resp.getBody());
            JSONArray data = result.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("Embedding API 返回空 data");
            }
            JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
            if (embedding == null) {
                throw new RuntimeException("Embedding API 返回空 embedding");
            }

            List<Float> vector = new ArrayList<>(embedding.size());
            for (int i = 0; i < embedding.size(); i++) {
                vector.add(embedding.getFloatValue(i));
            }
            return vector;
        } catch (Exception e) {
            log.error("query 向量化失败: {}", e.getMessage());
            throw new RuntimeException("query 向量化失败: " + e.getMessage(), e);
        }
    }
}
