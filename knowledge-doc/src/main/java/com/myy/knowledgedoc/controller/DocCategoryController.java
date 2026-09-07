package com.myy.knowledgedoc.controller;

import com.myy.common.dto.ResponseResult;
import com.myy.common.config.UserContextUtil;
import com.myy.knowledgedoc.entity.DocCategory;
import com.myy.knowledgedoc.service.DocCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/doc/category")
public class DocCategoryController {

    private final DocCategoryService categoryService;
    private final UserContextUtil userContextUtil;

    public DocCategoryController(DocCategoryService categoryService, UserContextUtil userContextUtil) {
        this.categoryService = categoryService;
        this.userContextUtil = userContextUtil;
    }

    @GetMapping("/tree")
    public ResponseResult<List<DocCategory>> tree() {
        return ResponseResult.success(categoryService.getCategoryTree());
    }

    @PostMapping
    public ResponseResult<DocCategory> add(@RequestParam String name,
                                            @RequestParam(required = false) Long parentId) {
        return ResponseResult.success(
                categoryService.addCategory(name, parentId, userContextUtil.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseResult.success("删除成功");
    }
}
