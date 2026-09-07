package com.myy.knowledgeagent.service;

import com.alibaba.fastjson.JSONObject;
import com.myy.knowledgeagent.tools.AgentTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工具注册表 — 只注册 knowledge_search 和 list_knowledge_tree
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.getName(), tool);
        }
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<JSONObject> getDefinitions() {
        List<JSONObject> defs = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            JSONObject def = new JSONObject();
            def.put("type", "function");
            JSONObject function = new JSONObject();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParametersSchema());
            def.put("function", function);
            defs.add(def);
        }
        return defs;
    }

    public String execute(String toolName, Map<String, Object> params) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            return "错误: 未知工具 " + toolName;
        }
        return tool.execute(params);
    }

    public Set<String> getAllowedParams(String toolName) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) return Set.of();
        JSONObject schema = tool.getParametersSchema();
        JSONObject props = schema.getJSONObject("properties");
        return props != null ? props.keySet() : Set.of();
    }

    public Set<String> getRequiredParams(String toolName) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) return Set.of();
        JSONObject schema = tool.getParametersSchema();
        // "required" is a JSONArray of strings
        if (schema.containsKey("required")) {
            return new HashSet<>(schema.getJSONArray("required").toJavaList(String.class));
        }
        return Set.of();
    }
}
