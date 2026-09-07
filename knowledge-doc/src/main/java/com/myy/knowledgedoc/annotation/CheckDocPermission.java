package com.myy.knowledgedoc.annotation;

import java.lang.annotation.*;

/**
 * 文档权限校验注解
 * <p>
 * 标注在 Controller 方法上，AOP 切面自动从方法参数中提取 docId 并校验当前用户权限。
 * 文档创建者默认拥有全部权限，ACL 表中的授权作为补充。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CheckDocPermission {

    /** 需要的权限类型：read / write / download */
    String value() default "read";

    /** 方法参数中表示文档 ID 的参数名 */
    String paramName() default "id";
}
