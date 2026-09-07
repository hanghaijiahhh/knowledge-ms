package com.myy.knowledgeaudit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myy.knowledgeaudit.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
