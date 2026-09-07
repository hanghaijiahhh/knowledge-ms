package com.myy.knowledgeaudit.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myy.common.dto.ResponseResult;
import com.myy.knowledgeaudit.entity.AuditLog;
import com.myy.knowledgeaudit.service.AuditLogService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /** 分页查询审计日志 */
    @GetMapping("/page")
    public ResponseResult<IPage<AuditLog>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId) {
        return ResponseResult.success(
                auditLogService.pageQuery(pageNum, pageSize, module, action, userId));
    }

    /** 查看单条审计日志详情 */
    @GetMapping("/{id}")
    public ResponseResult<AuditLog> getById(@PathVariable Long id) {
        return ResponseResult.success(auditLogService.getById(id));
    }
}
