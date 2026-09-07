package com.myy.knowledgedoc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myy.common.dto.ResponseResult;
import com.myy.knowledgedoc.annotation.CheckDocPermission;
import com.myy.common.config.UserContextUtil;
import com.myy.knowledgedoc.dto.DocCreateDTO;
import com.myy.knowledgedoc.dto.DocUpdateDTO;
import com.myy.knowledgedoc.entity.DocInfo;
import com.myy.knowledgedoc.entity.DocVersion;
import com.myy.knowledgedoc.service.DocAclService;
import com.myy.knowledgedoc.service.DocInfoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/doc")
public class DocInfoController {

    private final DocInfoService docInfoService;
    private final DocAclService docAclService;
    private final UserContextUtil userContextUtil;

    public DocInfoController(DocInfoService docInfoService, DocAclService docAclService,
                             UserContextUtil userContextUtil) {
        this.docInfoService = docInfoService;
        this.docAclService = docAclService;
        this.userContextUtil = userContextUtil;
    }

    @PostMapping
    public ResponseResult<DocInfo> create(@Valid @RequestBody DocCreateDTO dto) {
        Long userId = userContextUtil.getCurrentUserId();
        String username = userContextUtil.getCurrentUsername();
        return ResponseResult.success(docInfoService.createDoc(dto, userId, username));
    }

    @PutMapping("/{id}")
    @CheckDocPermission("write")
    public ResponseResult<DocInfo> update(@PathVariable Long id,
                                           @RequestBody DocUpdateDTO dto) {
        Long userId = userContextUtil.getCurrentUserId();
        return ResponseResult.success(docInfoService.updateDoc(id, dto, userId));
    }

    @DeleteMapping("/{id}")
    @CheckDocPermission("write")
    public ResponseResult<?> delete(@PathVariable Long id) {
        Long userId = userContextUtil.getCurrentUserId();
        docInfoService.deleteDoc(id, userId);
        return ResponseResult.success("删除成功");
    }

    @GetMapping("/{id}")
    @CheckDocPermission("read")
    public ResponseResult<DocInfo> getById(@PathVariable Long id) {
        return ResponseResult.success(docInfoService.getById(id));
    }

    @GetMapping("/page")
    public ResponseResult<IPage<DocInfo>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) String status) {
        return ResponseResult.success(
                docInfoService.pageQuery(pageNum, pageSize, keyword, categoryIds, status));
    }

    @GetMapping("/{id}/versions")
    @CheckDocPermission("read")
    public ResponseResult<List<DocVersion>> getVersions(@PathVariable Long id) {
        return ResponseResult.success(docInfoService.getVersions(id));
    }

    @PostMapping("/{id}/revert/{version}")
    @CheckDocPermission("write")
    public ResponseResult<DocInfo> revertVersion(@PathVariable Long id,
                                                  @PathVariable Integer version) {
        Long userId = userContextUtil.getCurrentUserId();
        return ResponseResult.success(docInfoService.revertVersion(id, version, userId));
    }

    /** 获取当前用户可见的所有文档ID（供搜索服务 Feign 调用） */
    @GetMapping("/visible-ids")
    public ResponseResult<List<Long>> getVisibleDocIds() {
        Long userId = userContextUtil.getCurrentUserId();
        return ResponseResult.success(docAclService.getVisibleDocIds(userId));
    }
}
