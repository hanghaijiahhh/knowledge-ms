package com.myy.knowledgeauth.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.myy.knowledgeauth.entity.*;
import com.myy.knowledgeauth.mapper.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化默认管理员账号和角色数据
 * — 只在数据为空时才插入，已有数据则跳过
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 初始化角色
        SysRole adminRole = createRoleIfAbsent("ADMIN", "管理员");
        createRoleIfAbsent("USER", "普通用户");

        // 初始化管理员用户
        SysUser admin = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (admin == null) {
            admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRealName("管理员");
            admin.setEmail("admin@knowledge.com");
            admin.setStatus(1);
            userMapper.insert(admin);

            // 绑定管理员角色
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(adminRole.getId());
            userRoleMapper.insert(userRole);

            System.out.println(">>> 初始化管理员账号: admin / admin123");
        }
    }

    private SysRole createRoleIfAbsent(String roleCode, String roleName) {
        SysRole role = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, roleCode));
        if (role == null) {
            role = new SysRole();
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setStatus(1);
            roleMapper.insert(role);
        }
        return role;
    }
}
