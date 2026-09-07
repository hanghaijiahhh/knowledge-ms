package com.myy.knowledgedoc.controller;

import com.myy.common.dto.ResponseResult;
import com.myy.knowledgedoc.annotation.CheckDocPermission;
import com.myy.common.config.UserContextUtil;
import com.myy.knowledgedoc.dto.DocAclDTO;
import com.myy.knowledgedoc.entity.DocAcl;
import com.myy.knowledgedoc.service.DocAclService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/doc/{id}/acl")
public class DocAclController {

    private final DocAclService docAclService;
    private final UserContextUtil userContextUtil;

    public DocAclController(DocAclService docAclService, UserContextUtil userContextUtil) {
        this.docAclService = docAclService;
        this.userContextUtil = userContextUtil;
    }

    /** 为文档授权某个用户 */
    @PostMapping
    @CheckDocPermission(value = "write")
    public ResponseResult<?> grant(@PathVariable Long id, @RequestBody DocAclDTO dto) {
        Long currentUserId = userContextUtil.getCurrentUserId();
        docAclService.setPermission(id, dto.getUserId(), dto.getPermRead(),
                dto.getPermWrite(), dto.getPermDownload(), currentUserId);
        return ResponseResult.success("授权成功");
    }

    /** 查看文档的所有 ACL 记录 */
    @GetMapping
    @CheckDocPermission(value = "write")
    public ResponseResult<List<DocAcl>> list(@PathVariable Long id) {
        return ResponseResult.success(docAclService.getByDocId(id));
    }

    /** 撤销某用户对文档的权限 */
    @DeleteMapping("/{userId}")
    @CheckDocPermission(value = "write")
    public ResponseResult<?> revoke(@PathVariable Long id, @PathVariable Long userId) {
        docAclService.removePermission(id, userId);
        return ResponseResult.success("撤销成功");
    }
}
