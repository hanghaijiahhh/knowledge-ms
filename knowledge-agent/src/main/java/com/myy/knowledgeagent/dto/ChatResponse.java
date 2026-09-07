package com.myy.knowledgeagent.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ChatResponse {
    private String sessionId;
    private String answer;
    private List<SourceRef> sources;
    private List<ThoughtStep> thoughtTrace;
    private int turnCount;

    @Data
    public static class SourceRef {
        private Long docId;
        private String title;
        private String categoryPath;
        private String excerpt;
        private double score;
    }

    @Data
    public static class ThoughtStep {
        private int turn;
        private String action;
        private Map<String, Object> params;
        private String observation;
    }
}
