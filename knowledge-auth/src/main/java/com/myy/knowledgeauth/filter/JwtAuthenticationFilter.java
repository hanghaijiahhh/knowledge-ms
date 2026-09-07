package com.myy.knowledgeauth.filter;

import com.alibaba.fastjson.JSON;
import com.myy.common.dto.ResponseResult;
import com.myy.knowledgeauth.security.JwtTokenProvider;
import com.myy.knowledgeauth.security.LoginUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * <p>
 * 继承 OncePerRequestFilter：保证每个请求只过滤一次
 * 职责：
 * 1. 从 Authorization Header 提取 Bearer Token
 * 2. 用 JwtTokenProvider 校验
 * 3. 校验通过 → 构造 Authentication 塞入 SecurityContextHolder
 * 4. 校验失败 → 直接返回 401（不继续走后续 Filter）
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 提取 Token
        String token = extractToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 校验 Token
        if (!jwtTokenProvider.validateToken(token)) {
            writeUnauthorized(response, "Token无效或已过期");
            return;
        }

        // 3. 解析 Claims，构造 Authentication
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();

            // 构造一个简化的 LoginUser（不含完整权限，仅用于身份识别）
            // 完整权限由 UserDetailsServiceImpl 加载
            LoginUser loginUser = new LoginUser(null, Collections.emptyList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            loginUser, null, loginUser.getAuthorities());
            authentication.setDetails(userId); // 把 userId 存入 details 方便后续取用

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception e) {
            writeUnauthorized(response, "Token解析失败");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从 Header 提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 返回 401 JSON 错误
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                JSON.toJSONString(ResponseResult.fail(401, message)));
    }
}
