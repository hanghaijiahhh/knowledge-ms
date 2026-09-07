package com.myy.knowledgeagent.llm;

import lombok.Data;
import java.util.Map;

@Data
public class ToolCall {
    private String id;
    private String name;
    private String rawArguments;
    private Map<String, Object> parsedArguments;

    public Map<String, Object> getParams() {
        return parsedArguments;
    }
}
