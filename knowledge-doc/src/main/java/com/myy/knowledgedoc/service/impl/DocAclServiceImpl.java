package com.myy.knowledgedoc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.common.exception.BizException;
import com.myy.knowledgedoc.entity.DocAcl;
import com.myy.knowledgedoc.entity.DocInfo;
import com.myy.knowledgedoc.mapper.DocAclMapper;
import com.myy.knowledgedoc.mapper.DocInfoMapper;
import com.myy.knowledgedoc.service.DocAclService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DocAclServiceImpl extends ServiceImpl<DocAclMapper, DocAcl>
        implements DocAclService {

    private final DocInfoMapper docInfoMapper;

    // 每种权限对应的 ACL 字段名和中文名
    private static final Map<String, String> PERM_FIELDS = Map.of(
            "read", "permRead",
            "write", "permWrite",
            "download", "permDownload"
    );

    public DocAclServiceImpl(DocInfoMapper docInfoMapper) {
        this.docInfoMapper = docInfoMapper;
    }

    @Override
    @Transactional
    public void setPermission(Long docId, Long userId, Integer permRead,
                              Integer permWrite, Integer permDownload, Long grantedBy) {
        DocAcl acl = getOne(new LambdaQueryWrapper<DocAcl>()
                .eq(DocAcl::getDocId, docId)
                .eq(DocAcl::getUserId, userId));

        if (acl == null) {
            acl = new DocAcl();
            acl.setDocId(docId);
            acl.setUserId(userId);
        }
        acl.setPermRead(permRead != null ? permRead : 0);
        acl.setPermWrite(permWrite != null ? permWrite : 0);
        acl.setPermDownload(permDownload != null ? permDownload : 0);
        acl.setGrantedBy(grantedBy);
        saveOrUpdate(acl);
    }

    @Override
    public List<DocAcl> getByDocId(Long docId) {
        return list(new LambdaQueryWrapper<DocAcl>().eq(DocAcl::getDocId, docId));
    }

    @Override
    public void removePermission(Long docId, Long userId) {
        remove(new LambdaQueryWrapper<DocAcl>()
                .eq(DocAcl::getDocId, docId)
                .eq(DocAcl::getUserId, userId));
    }

    @Override
    public boolean hasPermission(Long docId, Long userId, String permission) {
        DocInfo doc = docInfoMapper.selectById(docId);
        if (doc == null) throw new BizException("文档不存在");

        // 创建者拥有全部权限
        if (doc.getCreatorId().equals(userId)) return true;

        // 查 ACL 表
        DocAcl acl = getOne(new LambdaQueryWrapper<DocAcl>()
                .eq(DocAcl::getDocId, docId)
                .eq(DocAcl::getUserId, userId));
        if (acl == null) return false;

        return switch (permission) {
            case "read" -> acl.getPermRead() != null && acl.getPermRead() == 1;
            case "write" -> acl.getPermWrite() != null && acl.getPermWrite() == 1;
            case "download" -> acl.getPermDownload() != null && acl.getPermDownload() == 1;
            default -> false;
        };
    }

    @Override
    public void checkPermission(Long docId, Long userId, String permission) {
        if (!hasPermission(docId, userId, permission)) {
            String permName = PERM_FIELDS.getOrDefault(permission, permission);
            throw new BizException("无权" + permName + "此文档");
        }
    }

    @Override
    public List<Long> getVisibleDocIds(Long userId) {
        // 1. 自己创建的文档ID
        List<Long> ownIds = docInfoMapper.selectList(
                new LambdaQueryWrapper<DocInfo>()
                        .eq(DocInfo::getCreatorId, userId)
                        .eq(DocInfo::getIsDeleted, 0))
                .stream().map(DocInfo::getId).collect(java.util.stream.Collectors.toList());

        // 2. ACL 授权可读的文档ID
        List<Long> aclIds = list(new LambdaQueryWrapper<DocAcl>()
                .eq(DocAcl::getUserId, userId)
                .eq(DocAcl::getPermRead, 1))
                .stream().map(DocAcl::getDocId).collect(java.util.stream.Collectors.toList());

        // 合并去重
        java.util.Set<Long> all = new java.util.LinkedHashSet<>(ownIds);
        all.addAll(aclIds);
        return new java.util.ArrayList<>(all);
    }
}
