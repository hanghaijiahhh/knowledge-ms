package com.myy.knowledgegateway.config;

import com.alibaba.fastjson.JSON;
import com.myy.common.dto.ResponseResult;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 全局认证过滤器
 * <p>
 * 职责：
 * 1. 白名单放行（/auth/login、/auth/register 等无需 token）
 * 2. 从 Header 提取 JWT，校验签名和过期
 * 3. 校验通过后将用户信息 Base64 编码写入 X-User-Info 头，下游服务直接拿
 * 4. 校验失败返回 401
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:knowledge-ms-jwt-secret-key-2024}")
    private String jwtSecret;

    /**
     * 白名单路径，不需要鉴权
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/auth/login",
            "/auth/register",
            "/auth/logout",
            "/file/download/",
            "/file/preview/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String uri = request.getURI().getPath();

        // 1. 白名单放行
        if (WHITE_LIST.stream().anyMatch(uri::startsWith)) {
            return chain.filter(exchange);
        }

        // 2. Knife4j 文档接口放行
        if (uri.contains("/v3/api-docs") || uri.contains("/doc.html") || uri.contains("/webjars")) {
            return chain.filter(exchange);
        }

        // 3. 提取 Token
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(response, "未提供有效的认证Token");
        }
        String token = authHeader.substring(7);

        // 4. 校验 JWT
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 5. 将用户信息编码后写入 Header 传递给下游
            String userJson = JSON.toJSONString(claims);
            String encodedUser = Base64.getEncoder()
                    .encodeToString(userJson.getBytes(StandardCharsets.UTF_8));

            ServerHttpRequest mutableReq = request.mutate()
                    .header("X-User-Info", encodedUser)
                    .build();
            ServerWebExchange mutableExchange = exchange.mutate()
                    .request(mutableReq)
                    .build();

            log.debug("Token校验通过, uri={}, subject={}", uri, claims.getSubject());
            return chain.filter(mutableExchange);

        } catch (ExpiredJwtException e) {
            return unauthorized(response, "Token已过期，请重新登录");
        } catch (SignatureException e) {
            return unauthorized(response, "Token签名无效");
        } catch (Exception e) {
            return unauthorized(response, "Token解析失败");
        }
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        ResponseResult<?> result = ResponseResult.fail(401, message);
        byte[] bytes = JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Flux.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 高优先级，最早执行
    }
}
