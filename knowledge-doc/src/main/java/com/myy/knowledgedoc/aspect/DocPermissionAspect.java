package com.myy.knowledgedoc.aspect;

import com.myy.common.exception.BizException;
import com.myy.knowledgedoc.annotation.CheckDocPermission;
import com.myy.common.config.UserContextUtil;
import com.myy.knowledgedoc.service.DocAclService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;

@Aspect
@Component
public class DocPermissionAspect {

    private final DocAclService docAclService;
    private final UserContextUtil userContextUtil;

    public DocPermissionAspect(DocAclService docAclService, UserContextUtil userContextUtil) {
        this.docAclService = docAclService;
        this.userContextUtil = userContextUtil;
    }

    @Before("@annotation(checkPermission)")
    public void checkPermission(JoinPoint jp, CheckDocPermission checkPermission) {
        Long docId = extractDocId(jp, checkPermission.paramName());
        if (docId == null) return;

        Long userId = userContextUtil.getCurrentUserId();
        if (userId == null) throw new BizException("未登录");

        docAclService.checkPermission(docId, userId, checkPermission.value());
    }

    private Long extractDocId(JoinPoint jp, String paramName) {
        MethodSignature signature = (MethodSignature) jp.getSignature();
        Parameter[] params = signature.getMethod().getParameters();
        Object[] args = jp.getArgs();

        for (int i = 0; i < params.length; i++) {
            if (params[i].getName().equals(paramName) && args[i] instanceof Long) {
                return (Long) args[i];
            }
        }
        return null;
    }
}
