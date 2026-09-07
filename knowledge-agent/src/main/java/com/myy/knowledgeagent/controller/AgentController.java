package com.myy.knowledgeagent.controller;

import com.alibaba.fastjson.JSON;
import com.myy.common.dto.ResponseResult;
import com.myy.knowledgeagent.dto.ChatRequest;
import com.myy.knowledgeagent.dto.ChatResponse;
import com.myy.knowledgeagent.service.AgentOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/chat")
    public ResponseResult<ChatResponse> chat(@Valid @RequestBody ChatRequest req,
                                              HttpServletRequest request) {
        Long userId = extractUserId(request);
        String token = extractToken(request);

        log.info("Agent chat: userId={}, question={}", userId, req.getQuestion());
        ChatResponse resp = orchestrator.execute(req.getQuestion(), req.getSessionId(), userId, token);
        return ResponseResult.success(resp);
    }

    private Long extractUserId(HttpServletRequest request) {
        try {
            String encoded = request.getHeader("X-User-Info");
            if (encoded == null) return 0L;
            String json = new String(Base64.getDecoder().decode(encoded));
            Map<?, ?> map = JSON.parseObject(json);
            Object val = map.get("userId");
            return val != null ? Long.valueOf(val.toString()) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return "";
    }
}
