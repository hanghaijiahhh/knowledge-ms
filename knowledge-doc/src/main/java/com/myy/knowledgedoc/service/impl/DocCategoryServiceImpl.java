package com.myy.knowledgedoc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.common.exception.BizException;
import com.myy.knowledgedoc.entity.DocCategory;
import com.myy.knowledgedoc.mapper.DocCategoryMapper;
import com.myy.knowledgedoc.service.DocCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocCategoryServiceImpl extends ServiceImpl<DocCategoryMapper, DocCategory>
        implements DocCategoryService {

    @Override
    public List<DocCategory> getCategoryTree() {
        // 先查所有分类，再内存组装树
        List<DocCategory> all = list(new LambdaQueryWrapper<DocCategory>()
                .orderByAsc(DocCategory::getSort));
        Map<Long, List<DocCategory>> childrenMap = all.stream()
                .filter(c -> c.getParentId() != 0)
                .collect(Collectors.groupingBy(DocCategory::getParentId));

        List<DocCategory> roots = new ArrayList<>();
        for (DocCategory c : all) {
            if (c.getParentId() == 0) {
                roots.add(c);
                buildChildren(c, childrenMap);
            }
        }
        return roots;
    }

    private void buildChildren(DocCategory parent, Map<Long, List<DocCategory>> childrenMap) {
        List<DocCategory> children = childrenMap.get(parent.getId());
        if (children != null) {
            parent.setChildren(children);
            for (DocCategory child : children) {
                buildChildren(child, childrenMap);
            }
        }
    }

    @Override
    public DocCategory addCategory(String name, Long parentId, Long userId) {
        if (parentId != null && parentId > 0) {
            DocCategory parent = getById(parentId);
            if (parent == null) throw new BizException("父分类不存在");
        }
        DocCategory category = new DocCategory();
        category.setName(name);
        category.setParentId(parentId != null ? parentId : 0);
        category.setCreatorId(userId);
        category.setSort(0);
        save(category);
        return category;
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        // 递归删除子分类
        List<DocCategory> children = list(
                new LambdaQueryWrapper<DocCategory>().eq(DocCategory::getParentId, id));
        for (DocCategory child : children) {
            deleteCategory(child.getId());
        }
        removeById(id);
    }
}
