-- 知识库数据库初始化脚本
CREATE DATABASE IF NOT EXISTS knowledge_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE knowledge_db;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(BCrypt)',
    real_name VARCHAR(64) COMMENT '真实姓名',
    email VARCHAR(128) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    status TINYINT DEFAULT 1 COMMENT '0-禁用 1-正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(64) NOT NULL UNIQUE COMMENT '角色编码',
    description VARCHAR(255) COMMENT '描述',
    status TINYINT DEFAULT 1 COMMENT '0-禁用 1-正常',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 菜单权限表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID',
    menu_name VARCHAR(64) NOT NULL COMMENT '菜单名称',
    path VARCHAR(255) COMMENT '路由路径',
    component VARCHAR(255) COMMENT '前端组件路径',
    perms VARCHAR(255) COMMENT '权限标识(如 doc:read)',
    type TINYINT DEFAULT 0 COMMENT '0-目录 1-菜单 2-按钮',
    icon VARCHAR(64) COMMENT '图标',
    sort INT DEFAULT 0 COMMENT '排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- 角色菜单关联
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    UNIQUE KEY uk_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

-- ========== 初始化数据 ==========

-- 密码: admin123 (BCrypt加密)
-- 密码: admin123 (BCrypt加密)
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('admin', '$2b$10$xdpi22VtaLer6cCID5Jpxu92cd0ahKvCzpPjAdQ0sj/d4USi1zMza', '管理员', 'admin@knowledge.com', 1);
-- 密码: user123 (BCrypt加密)
INSERT INTO sys_user (username, password, real_name, email, status) VALUES
('zhangsan', '$2b$10$pY3xAen8e4QT70ICNAvyF.1svIi8tgORxaKEj0ckYpqfoSlZylz1q', '张三', 'zhangsan@knowledge.com', 1);

INSERT INTO sys_role (role_name, role_code, description) VALUES
('管理员', 'ADMIN', '系统管理员'),
('普通用户', 'USER', '普通用户');

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 2);

INSERT INTO sys_menu (id, parent_id, menu_name, path, perms, type, icon, sort) VALUES
(1, 0, '文档管理', '/doc', '', 0, 'document', 1),
(2, 1, '文档列表', '/doc/list', 'doc:list', 1, '', 1),
(3, 1, '文档上传', '/doc/upload', 'doc:upload', 1, '', 2),
(4, 0, '系统管理', '/system', '', 0, 'setting', 2),
(5, 4, '用户管理', '/system/user', 'system:user', 1, '', 1),
(6, 4, '角色管理', '/system/role', 'system:role', 1, '', 2);

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6);

-- ========== 文档服务表 ==========

-- 文档分类表
CREATE TABLE IF NOT EXISTS doc_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT DEFAULT 0 COMMENT '父分类ID(0=根)',
    name VARCHAR(64) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 0 COMMENT '排序',
    creator_id BIGINT COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分类表';

-- 文档主表
CREATE TABLE IF NOT EXISTS doc_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL COMMENT '标题',
    content TEXT COMMENT '摘要/富文本内容',
    category_id BIGINT DEFAULT 0 COMMENT '分类ID',
    file_name VARCHAR(255) COMMENT '原始文件名',
    file_url VARCHAR(512) COMMENT 'MinIO存储路径',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    file_type VARCHAR(32) COMMENT '文件类型(MIME)',
    file_md5 VARCHAR(64) COMMENT '文件MD5(秒传用)',
    version INT DEFAULT 1 COMMENT '当前版本号',
    status VARCHAR(16) DEFAULT 'PUBLISHED' COMMENT '状态:DRAFT/PUBLISHED/ARCHIVED',
    creator_id BIGINT COMMENT '创建人ID',
    creator_name VARCHAR(64) COMMENT '创建人姓名',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0-正常 1-已删除',
    ext_metadata JSON COMMENT '扩展字段(档3预留)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_creator (creator_id),
    INDEX idx_status (status),
    FULLTEXT INDEX ft_title_content (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档主表';

-- 文档版本表
CREATE TABLE IF NOT EXISTS doc_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '文档ID',
    version INT NOT NULL COMMENT '版本号',
    title VARCHAR(200) COMMENT '该版本标题',
    content MEDIUMTEXT COMMENT '该版本内容',
    file_url VARCHAR(512) COMMENT '该版本文件路径',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小',
    change_desc VARCHAR(500) COMMENT '变更说明',
    creator_id BIGINT COMMENT '创建人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 文档权限表（ACL）
CREATE TABLE IF NOT EXISTS doc_acl (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL COMMENT '文档ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    perm_read TINYINT DEFAULT 0 COMMENT '读权限 0-无 1-有',
    perm_write TINYINT DEFAULT 0 COMMENT '写权限 0-无 1-有',
    perm_download TINYINT DEFAULT 0 COMMENT '下载权限 0-无 1-有',
    granted_by BIGINT COMMENT '授权人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_user (doc_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档权限表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(64) COMMENT '用户名',
    module VARCHAR(32) COMMENT '模块:doc/file/auth/search',
    action VARCHAR(32) COMMENT '操作:CREATE/UPDATE/DELETE/QUERY',
    target_id BIGINT COMMENT '操作对象ID',
    target_name VARCHAR(255) COMMENT '操作对象名称',
    ip_address VARCHAR(64) COMMENT '请求IP',
    user_agent VARCHAR(500) COMMENT '浏览器UA',
    request_params TEXT COMMENT '请求参数(JSON)',
    response_status INT COMMENT '响应状态码',
    cost_time BIGINT COMMENT '耗时(ms)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_module_action (module, action),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作审计日志表';

-- 文件信息表
CREATE TABLE IF NOT EXISTS file_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) COMMENT '原始文件名',
    file_key VARCHAR(512) NOT NULL COMMENT 'MinIO存储路径',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小(字节)',
    file_type VARCHAR(128) COMMENT 'MIME类型',
    file_md5 VARCHAR(64) COMMENT '文件MD5(秒传)',
    upload_status VARCHAR(16) DEFAULT 'COMPLETED' COMMENT '上传状态:UPLOADING/COMPLETED/FAILED',
    doc_id BIGINT COMMENT '关联文档ID',
    uploader_id BIGINT COMMENT '上传人ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_md5 (file_md5),
    INDEX idx_doc_id (doc_id),
    INDEX idx_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';
