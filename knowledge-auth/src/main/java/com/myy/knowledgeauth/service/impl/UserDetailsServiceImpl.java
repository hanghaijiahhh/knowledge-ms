package com.myy.knowledgeauth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.knowledgeauth.entity.SysRole;
import com.myy.knowledgeauth.entity.SysUser;
import com.myy.knowledgeauth.mapper.SysRoleMapper;
import com.myy.knowledgeauth.mapper.SysUserMapper;
import com.myy.knowledgeauth.security.LoginUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security 加载用户的入口
 * — DaoAuthenticationProvider 会调用 loadUserByUsername()
 * — 从数据库查用户 → 查角色 → 组装成 LoginUser
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public UserDetailsServiceImpl(SysUserMapper sysUserMapper, SysRoleMapper sysRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. 查用户
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 2. 查角色 → 权限标识
        List<SysRole> roles = sysRoleMapper.selectRolesByUserId(user.getId());
        List<String> permissions = roles.stream()
                .map(SysRole::getRoleCode)
                .toList();

        return new LoginUser(user, permissions);
    }
}
