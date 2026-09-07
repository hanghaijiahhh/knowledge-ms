package com.myy.knowledgeagent.llm;

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
import java.util.Map;

/**
 * DeepSeek LLM 客户端（兼容 OpenAI 格式）
 */
@Slf4j
@Component
public class LLMClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${llm.deepseek.api-key}")
    private String apiKey;

    @Value("${llm.deepseek.base-url}")
    private String baseUrl;

    @Value("${llm.deepseek.model}")
    private String model;

    @Value("${llm.deepseek.temperature:0.3}")
    private double temperature;

    @Value("${llm.deepseek.max-tokens:2048}")
    private int maxTokens;

    /**
     * 发起带 Function Calling 的 Chat 请求
     */
    public LLMResponse chat(List<Map<String, Object>> messages, List<JSONObject> tools) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        log.debug("LLM request: {}", body.toJSONString());

        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/v1/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(body.toJSONString(), headers),
                String.class);

        JSONObject result = JSON.parseObject(resp.getBody());
        return parseResponse(result);
    }

    private LLMResponse parseResponse(JSONObject result) {
        JSONArray choices = result.getJSONArray("choices");
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");

        LLMResponse response = new LLMResponse();

        // 判断是否完成（有 content 且无 tool_calls）
        String finishReason = choice.getString("finish_reason");
        if ("stop".equals(finishReason)) {
            response.setFinished(true);
            response.setContent(message.getString("content"));
        } else if ("tool_calls".equals(finishReason) || message.containsKey("tool_calls")) {
            response.setFinished(false);
            response.setToolCalls(parseToolCalls(message.getJSONArray("tool_calls")));
        } else {
            // 兼容：同时有 content 和 tool_calls
            response.setContent(message.getString("content"));
            if (message.containsKey("tool_calls")) {
                response.setToolCalls(parseToolCalls(message.getJSONArray("tool_calls")));
            }
            response.setFinished(message.getString("content") != null
                    && !message.containsKey("tool_calls"));
        }

        return response;
    }

    private List<ToolCall> parseToolCalls(JSONArray toolCallsJson) {
        List<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < toolCallsJson.size(); i++) {
            JSONObject tc = toolCallsJson.getJSONObject(i);
            JSONObject function = tc.getJSONObject("function");

            ToolCall call = new ToolCall();
            call.setId(tc.getString("id"));
            call.setName(function.getString("name"));
            call.setRawArguments(function.getString("arguments"));
            call.setParsedArguments(ToolCallParser.parse(function.getString("arguments")));
            calls.add(call);
        }
        return calls;
    }
}
