package com.myy.knowledgeagent.llm;

import lombok.Data;
import java.util.List;

@Data
public class LLMResponse {
    private boolean finished;
    private String content;
    private List<ToolCall> toolCalls;
}
