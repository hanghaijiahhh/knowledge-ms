package com.myy.knowledgeagent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 工具② list_knowledge_tree — 查询知识库目录结构
 */
@Slf4j
@Component
public class ListTreeTool implements AgentTool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "list_knowledge_tree";
    }

    @Override
    public String getDescription() {
        return "查询知识库的目录结构，了解有哪些分类和子分类，以及每个分类下的文档数量。";
    }

    @Override
    public JSONObject getParametersSchema() {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject props = new JSONObject();
        JSONObject parentProp = new JSONObject();
        parentProp.put("type", "integer");
        parentProp.put("description", "父分类ID，不传则返回根级");
        props.put("parentId", parentProp);
        schema.put("properties", props);
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        String token = (String) params.get("accessToken");
        log.info("list_knowledge_tree called");

        try {
            String url = "http://localhost:8080/doc/category/tree";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            JSONObject result = JSON.parseObject(resp.getBody());
            if (result.getInteger("code") == 200 && result.get("data") != null) {
                return JSON.toJSONString(result.get("data"));
            }
            return "无分类数据";
        } catch (Exception e) {
            log.error("list_knowledge_tree 失败", e);
            return "目录查询暂时不可用: " + e.getMessage();
        }
    }
}
