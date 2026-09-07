package com.myy.knowledgeauth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.common.dto.ResponseResult;
import com.myy.common.exception.BizException;
import com.myy.knowledgeauth.dto.LoginRequest;
import com.myy.knowledgeauth.dto.LoginResponse;
import com.myy.knowledgeauth.dto.RegisterRequest;
import com.myy.knowledgeauth.entity.SysUser;
import com.myy.knowledgeauth.entity.SysUserRole;
import com.myy.knowledgeauth.mapper.SysRoleMapper;
import com.myy.knowledgeauth.mapper.SysUserMapper;
import com.myy.knowledgeauth.mapper.SysUserRoleMapper;
import com.myy.knowledgeauth.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                          SysRoleMapper roleMapper, PasswordEncoder passwordEncoder,
                          AuthenticationManager authenticationManager,
                          JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 登录
     * <p>
     * 流程：接收 username+password → AuthenticationManager 做认证
     * → Spring Security 自动调用 UserDetailsServiceImpl.loadUserByUsername()
     * → 验证密码（BCrypt）→ 成功则生成 JWT 返回
     */
    @PostMapping("/login")
    public ResponseResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // 1. 认证（内部会调 UserDetailsServiceImpl 查用户、验密码）
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword()));

        // 2. 获取认证通过的用户信息
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));

        // 3. 生成 JWT
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return ResponseResult.success(
                new LoginResponse(token, user.getUsername(), user.getRealName()));
    }

    /**
     * 注册
     */
    @PostMapping("/register")
    public ResponseResult<?> register(@Valid @RequestBody RegisterRequest request) {
        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }

        // 创建用户
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1);
        userMapper.insert(user);

        // 默认分配 USER 角色
        var userRole = roleMapper.selectOne(
                new LambdaQueryWrapper<com.myy.knowledgeauth.entity.SysRole>()
                        .eq(com.myy.knowledgeauth.entity.SysRole::getRoleCode, "USER"));
        if (userRole != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(userRole.getId());
            userRoleMapper.insert(ur);
        }

        return ResponseResult.success("注册成功");
    }

    /**
     * 获取用户列表（供权限管理等模块使用）
     */
    @GetMapping("/users")
    public ResponseResult<List<Map<String, Object>>> listUsers() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1));
        List<Map<String, Object>> list = users.stream().map(u -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            return m;
        }).toList();
        return ResponseResult.success(list);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public ResponseResult<Map<String, Object>> getCurrentUser(Authentication authentication) {
        String username = authentication.getName();
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        var roles = roleMapper.selectRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream()
                .map(com.myy.knowledgeauth.entity.SysRole::getRoleCode).toList();

        Map<String, Object> info = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "realName", user.getRealName() != null ? user.getRealName() : "",
                "email", user.getEmail() != null ? user.getEmail() : "",
                "roles", roleCodes
        );
        return ResponseResult.success(info);
    }
}
