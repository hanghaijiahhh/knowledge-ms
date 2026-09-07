package com.myy.knowledgedoc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.myy.knowledgedoc.entity.DocAcl;

import java.util.List;

public interface DocAclService extends IService<DocAcl> {

    /** 为文档设置用户权限（如已存在则更新） */
    void setPermission(Long docId, Long userId, Integer permRead,
                       Integer permWrite, Integer permDownload, Long grantedBy);

    /** 查询文档的所有 ACL 记录 */
    List<DocAcl> getByDocId(Long docId);

    /** 删除某用户对文档的权限 */
    void removePermission(Long docId, Long userId);

    /** 校验用户对文档是否有指定权限（creator 始终有权限） */
    boolean hasPermission(Long docId, Long userId, String permission);

    /** 校验无权限则抛异常 */
    void checkPermission(Long docId, Long userId, String permission);

    /** 获取用户可见的所有文档ID（自己创建的 + ACL授权的） */
    List<Long> getVisibleDocIds(Long userId);
}
