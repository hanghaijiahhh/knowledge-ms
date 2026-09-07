package com.myy.knowledgeagent.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.myy.knowledgeagent.dto.ChatResponse;
import com.myy.knowledgeagent.llm.LLMClient;
import com.myy.knowledgeagent.llm.LLMResponse;
import com.myy.knowledgeagent.llm.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 主循环 — 有限轮次 Function Calling
 * <p>
 * 流程: LLM判断→调工具→拿结果→LLM再判断→...→生成最终回答
 */
@Slf4j
@Service
public class AgentOrchestrator {

    private final LLMClient llmClient;
    private final ToolRegistry toolRegistry;
    private final SessionMemoryService memoryService;

    @Value("${agent.max-turns:3}")
    private int maxTurns;

    @Value("${agent.max-context-chars:8000}")
    private int maxContextChars;

    public AgentOrchestrator(LLMClient llmClient, ToolRegistry toolRegistry,
                             SessionMemoryService memoryService) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.memoryService = memoryService;
    }

    public ChatResponse execute(String question, String sessionId, Long userId, String token) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        List<Map<String, Object>> messages = memoryService.getHistory(sessionId);

        // 系统提示词（每次放入第一条，不持久化到 Redis）
        messages.add(0, buildSystemMessage());

        // 用户问题
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", question);
        messages.add(userMsg);

        List<ChatResponse.ThoughtStep> trace = new ArrayList<>();

        for (int turn = 0; turn < maxTurns; turn++) {
            log.info("Agent turn {}/{}", turn + 1, maxTurns);

            LLMResponse llmResp = llmClient.chat(messages, toolRegistry.getDefinitions());

            // 模型直接回答（不需要调工具）
            if (llmResp.isFinished() && llmResp.getContent() != null) {
                log.info("Agent finished at turn {}", turn + 1);
                ChatResponse cr = new ChatResponse();
                cr.setAnswer(llmResp.getContent());
                cr.setThoughtTrace(trace);
                cr.setTurnCount(turn + 1);
                cr.setSessionId(sessionId);

                // 保存对话历史（不含 system prompt）
                messages.remove(0); // remove system
                messages.add(Map.of("role", "assistant", "content", llmResp.getContent()));
                memoryService.saveHistory(sessionId, messages);
                return cr;
            }

            // 模型发起 Function Call
            List<ToolCall> toolCalls = llmResp.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // TODO: 没有 tool call 也没有 content，强制追加 prompt 引导
                log.warn("Turn {}: 无tool call且无content，追加引导提示", turn);
                messages.add(Map.of("role", "user",
                        "content", "请基于知识库检索工具获取信息后再回答。如果检索无结果，直接告知用户。"));
                continue;
            }

            // 执行工具调用
            for (ToolCall tc : toolCalls) {
                // ★ Schema校验 + 白名单过滤
                ToolCall validCall = validateAndSanitize(tc);
                if (validCall == null) {
                    messages.add(Map.of("role", "user",
                            "content", "工具调用参数格式错误，请检查后重试。"));
                    continue;
                }

                // ★ 注入权限上下文
                validCall.getParams().put("userId", userId);
                validCall.getParams().put("accessToken", token);

                log.info("Agent turn={}, tool={}", turn, validCall.getName());
                String toolResult = toolRegistry.execute(
                        validCall.getName(), validCall.getParams());

                trace.add(buildThoughtStep(turn, validCall, toolResult));

                // 将工具调用和结果反馈给 LLM
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", null);
                assistantMsg.put("tool_calls", buildToolCallMsg(validCall));
                messages.add(assistantMsg);

                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", validCall.getId());
                toolMsg.put("content", toolResult);
                messages.add(toolMsg);
            }

            // ★ 上下文截断
            messages = truncateMessages(messages);
        }

        // 达到最大轮次，强制生成最终回答
        return forceFinalAnswer(messages, sessionId, trace);
    }

    private ToolCall validateAndSanitize(ToolCall raw) {
        // ① 白名单：只允许已注册的工具
        if (!toolRegistry.contains(raw.getName())) {
            log.warn("幻觉工具调用: {}", raw.getName());
            return null;
        }
        // ② 必填参数检查
        Set<String> required = toolRegistry.getRequiredParams(raw.getName());
        Map<String, Object> params = raw.getParams();
        if (params == null) {
            params = new HashMap<>();
            raw.setParsedArguments(params);
        }
        for (String r : required) {
            if (!params.containsKey(r)) {
                log.warn("工具 {} 缺少必填参数: {}", raw.getName(), r);
                return null;
            }
        }
        // ③ 移除多余参数（模型幻觉加的）
        Set<String> allowed = toolRegistry.getAllowedParams(raw.getName());
        params.keySet().retainAll(allowed);
        return raw;
    }

    private List<Map<String, Object>> buildToolCallMsg(ToolCall tc) {
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", tc.getName());
        func.put("arguments", tc.getRawArguments() != null ? tc.getRawArguments()
                : JSON.toJSONString(tc.getParams()));
        Map<String, Object> tcMsg = new LinkedHashMap<>();
        tcMsg.put("id", tc.getId());
        tcMsg.put("type", "function");
        tcMsg.put("function", func);
        return List.of(tcMsg);
    }

    private ChatResponse.ThoughtStep buildThoughtStep(int turn, ToolCall tc, String result) {
        ChatResponse.ThoughtStep step = new ChatResponse.ThoughtStep();
        step.setTurn(turn);
        step.setAction(tc.getName());
        step.setParams(tc.getParams());
        // 截断观测结果
        step.setObservation(result != null && result.length() > 500
                ? result.substring(0, 500) + "..." : result);
        return step;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> truncateMessages(List<Map<String, Object>> messages) {
        // TODO: 用 Tokenizer 精确算 token 数，目前按字符数粗略估计
        int totalChars = 0;
        for (Map<String, Object> msg : messages) {
            totalChars += JSON.toJSONString(msg).length();
        }
        // 保留并裁剪，从头部去掉最早的非 system 消息
        while (totalChars > maxContextChars && messages.size() > 2) {
            // 跳过 system message (index 0)
            Map<String, Object> removed = messages.remove(1);
            totalChars -= JSON.toJSONString(removed).length();
        }
        return messages;
    }

    private ChatResponse forceFinalAnswer(List<Map<String, Object>> messages,
                                           String sessionId,
                                           List<ChatResponse.ThoughtStep> trace) {
        // 追加强制结束提示
        messages.add(Map.of("role", "user",
                "content", "请基于以上检索结果，生成最终回答。如果信息不足，请如实告知知识库中暂无相关内容。请附上来源引用。"));
        LLMResponse llmResp = llmClient.chat(messages, null); // 不带工具

        ChatResponse cr = new ChatResponse();
        cr.setAnswer(llmResp.getContent() != null ? llmResp.getContent() : "抱歉，处理超时，请稍后重试。");
        cr.setThoughtTrace(trace);
        cr.setTurnCount(maxTurns);
        cr.setSessionId(sessionId);

        // 保存精简后的对话历史
        messages.remove(0); // remove system
        messages.add(Map.of("role", "assistant", "content", cr.getAnswer()));
        memoryService.saveHistory(sessionId, messages);

        return cr;
    }

    private Map<String, Object> buildSystemMessage() {
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", """
                你是企业内部知识库助手。你只能基于工具返回的知识库内容回答问题。
                规则：
                1. 回答前先判断是否需要检索：
                   - 需要查资料 → 调用 knowledge_search
                   - 需要了解分类结构 → 调用 list_knowledge_tree
                   - 简单寒暄 / 与知识库无关 → 直接回答
                2. 收到检索结果后判断信息是否充足：
                   - 充足 → 引用具体来源，给出答案
                   - 不充足 → 如实告知"知识库中暂无相关内容"
                3. 回答末尾附上引用来源：`[来源: 文档标题]`
                4. 不要编造知识库中没有的内容。""");
        return sys;
    }
}
