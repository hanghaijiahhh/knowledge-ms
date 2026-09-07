package com.myy.knowledgeagent.tools;

import com.alibaba.fastjson.JSONObject;
import java.util.Map;

public interface AgentTool {
    String getName();
    String getDescription();
    JSONObject getParametersSchema();
    String execute(Map<String, Object> params);
}
