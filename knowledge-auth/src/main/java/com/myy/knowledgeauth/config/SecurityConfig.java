package com.myy.knowledgeauth.config;

import com.myy.knowledgeauth.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置
 * <p>
 * 核心改动：
 * 1. 关闭 CSRF（API 用 JWT，不需要 CSRF）
 * 2. 无状态 Session（不创建 HttpSession）
 * 3. 白名单放行 /auth/login、/auth/register
 * 4. 在 UsernamePasswordAuthenticationFilter 之前插入 JwtAuthenticationFilter
 * 5. 关闭默认表单登录页和 HTTP Basic
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)      // 开启 @PreAuthorize 注解
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 关闭 CSRF（显式 lambda，避免方法引用类型推断问题）
                .csrf(csrf -> csrf.disable())
                // 无状态
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 请求授权
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/auth/register").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/doc.html", "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                )
                // 关闭默认表单登录
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // 退出登录
                .logout(logout -> logout.disable())
                // JWT Filter 插在 UsernamePasswordAuthenticationFilter 前面
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
