package com.myy.knowledgedoc.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.myy.knowledgedoc.dto.DocCreateDTO;
import com.myy.knowledgedoc.dto.DocUpdateDTO;
import com.myy.knowledgedoc.entity.DocInfo;
import com.myy.knowledgedoc.entity.DocVersion;

import java.util.List;

public interface DocInfoService extends IService<DocInfo> {

    DocInfo createDoc(DocCreateDTO dto, Long userId, String username);

    DocInfo updateDoc(Long docId, DocUpdateDTO dto, Long userId);

    void deleteDoc(Long docId, Long userId);

    IPage<DocInfo> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                             List<Long> categoryIds, String status);

    List<DocVersion> getVersions(Long docId);

    DocInfo revertVersion(Long docId, Integer targetVersion, Long userId);
}
