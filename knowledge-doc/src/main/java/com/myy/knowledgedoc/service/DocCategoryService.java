package com.myy.knowledgedoc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.myy.knowledgedoc.entity.DocCategory;

import java.util.List;

public interface DocCategoryService extends IService<DocCategory> {

    /**
     * 获取分类树（递归）
     */
    List<DocCategory> getCategoryTree();

    /**
     * 新增分类
     */
    DocCategory addCategory(String name, Long parentId, Long userId);

    /**
     * 递归删除分类及子分类
     */
    void deleteCategory(Long id);
}
