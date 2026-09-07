package com.myy.common.config;

import com.alibaba.fastjson.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Base64;

/**
 * 从 Gateway 注入的 X-User-Info Header 中提取当前用户信息
 * <p>
 * Gateway AuthFilter 验完 JWT 后把 claims Base64 编码写入 X-User-Info 头，
 * 下游服务直接解码取用。
 */
@Component
public class UserContextUtil {

    public Long getCurrentUserId() {
        String val = getClaim("userId");
        return val != null && !val.isEmpty() ? Long.valueOf(val) : 0L;
    }

    public String getCurrentUsername() {
        return getClaim("username");
    }

    public String getCurrentUserInfo() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes()).getRequest();
            return request.getHeader("X-User-Info");
        } catch (Exception e) {
            return "";
        }
    }

    private String getClaim(String key) {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.getRequestAttributes()).getRequest();
            String encoded = request.getHeader("X-User-Info");
            if (encoded == null) return "";

            String json = new String(Base64.getDecoder().decode(encoded));
            var map = JSON.parseObject(json);
            Object val = map.get(key);
            return val != null ? val.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
