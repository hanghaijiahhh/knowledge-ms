package com.myy.knowledgeembedding.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 调用 Embedding API 进行文本向量化（支持 OpenAI 兼容格式）
 */
@Slf4j
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.model}")
    private String model;

    public float[] embed(String text) {
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

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.getFloatValue(i);
            }
            return vector;
        } catch (Exception e) {
            log.error("Embedding API 调用失败: {}", e.getMessage());
            throw new RuntimeException("Embedding 向量化失败: " + e.getMessage(), e);
        }
    }
}
