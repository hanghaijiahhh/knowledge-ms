package com.myy.knowledgeagent.service;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 会话短时工作记忆
 * — 仅保留最近 N 轮对话，不做长时向量记忆
 * — TTL 自动过期，防止内存泄漏
 */
@Slf4j
@Service
public class SessionMemoryService {

    private final StringRedisTemplate redisTemplate;

    @Value("${agent.session-ttl-minutes:30}")
    private int ttlMinutes;

    private static final String KEY_PREFIX = "agent:session:";
    private static final int MAX_HISTORY = 20;

    public SessionMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getHistory(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return new ArrayList<>();
        }
        return JSON.parseObject(json, List.class);
    }

    public void saveHistory(String sessionId, List<Map<String, Object>> messages) {
        // 只保留最近 N 条
        if (messages.size() > MAX_HISTORY) {
            messages = messages.subList(messages.size() - MAX_HISTORY, messages.size());
        }
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, JSON.toJSONString(messages),
                ttlMinutes, TimeUnit.MINUTES);
    }

    public void clear(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }
}
