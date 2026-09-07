package com.myy.knowledgeagent.llm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Function Call 参数解析与校验
 * — DeepSeek 偶发把 arguments 返回为 JSON 字符串，需兼容
 * — 模型可能幻觉参数，做白名单过滤
 */
@Slf4j
public class ToolCallParser {

    public static Map<String, Object> parse(String rawArguments) {
        try {
            JSONObject obj = JSON.parseObject(rawArguments);
            return obj.getInnerMap();
        } catch (Exception e) {
            log.warn("ToolCall参数JSON解析失败: {}", rawArguments);
            return Map.of();
        }
    }
}
