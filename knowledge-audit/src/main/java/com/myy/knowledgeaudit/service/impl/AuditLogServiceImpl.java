package com.myy.knowledgeaudit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.knowledgeaudit.entity.AuditLog;
import com.myy.knowledgeaudit.mapper.AuditLogMapper;
import com.myy.knowledgeaudit.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {

    @Override
    public void saveFromEvent(Map<String, Object> payload) {
        AuditLog log = new AuditLog();
        log.setUserId(toLong(payload.get("userId")));
        log.setUsername(toStr(payload.get("username")));
        log.setModule("doc");
        log.setAction(extractAction(payload));
        log.setTargetId(toLong(payload.get("docId")));
        log.setTargetName(toStr(payload.get("title")));
        log.setRequestParams(payload.toString());
        log.setResponseStatus(200);
        log.setCreateTime(LocalDateTime.now());
        save(log);
    }

    @Override
    public IPage<AuditLog> pageQuery(int pageNum, int pageSize,
                                      String module, String action, Long userId) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null) wrapper.eq(AuditLog::getModule, module);
        if (action != null) wrapper.eq(AuditLog::getAction, action);
        if (userId != null) wrapper.eq(AuditLog::getUserId, userId);
        wrapper.orderByDesc(AuditLog::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    private String extractAction(Map<String, Object> payload) {
        // routing key 在消息头中，这里根据 payload 推断
        if (payload.containsKey("version")) return "UPDATE";
        return "CREATE";
    }

    private String toStr(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try { return Long.valueOf(obj.toString()); } catch (Exception e) { return null; }
    }
}
