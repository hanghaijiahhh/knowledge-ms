package com.myy.knowledgedoc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.myy.common.constant.RabbitMQConstant;
import com.myy.common.exception.BizException;
import com.myy.knowledgedoc.dto.DocCreateDTO;
import com.myy.knowledgedoc.dto.DocUpdateDTO;
import com.myy.knowledgedoc.entity.DocInfo;
import com.myy.knowledgedoc.entity.DocVersion;
import com.myy.knowledgedoc.mapper.DocInfoMapper;
import com.myy.knowledgedoc.mapper.DocVersionMapper;
import com.myy.knowledgedoc.service.DocInfoService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocInfoServiceImpl extends ServiceImpl<DocInfoMapper, DocInfo>
        implements DocInfoService {

    private final DocVersionMapper versionMapper;
    private final RabbitTemplate rabbitTemplate;

    public DocInfoServiceImpl(DocVersionMapper versionMapper, RabbitTemplate rabbitTemplate) {
        this.versionMapper = versionMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public DocInfo createDoc(DocCreateDTO dto, Long userId, String username) {
        DocInfo doc = new DocInfo();
        doc.setTitle(dto.getTitle());
        doc.setContent(dto.getContent());
        doc.setCategoryId(dto.getCategoryId());
        doc.setFileName(dto.getFileName());
        doc.setFileUrl(dto.getFileUrl());
        doc.setFileSize(dto.getFileSize());
        doc.setFileType(dto.getFileType());
        doc.setFileMd5(dto.getFileMd5());
        doc.setVersion(1);
        doc.setStatus(dto.getStatus() != null ? dto.getStatus() : "PUBLISHED");
        doc.setCreatorId(userId);
        doc.setCreatorName(username);
        save(doc);

        // 保存版本 1
        saveVersion(doc.getId(), 1, doc.getTitle(), doc.getContent(),
                doc.getFileUrl(), doc.getFileSize(), "初始版本", userId);

        // 发布文档创建事件 → 搜索服务消费、审计服务消费
        publishEvent("doc.create", buildEventPayload(doc, userId, username));

        return doc;
    }

    @Override
    @Transactional
    public DocInfo updateDoc(Long docId, DocUpdateDTO dto, Long userId) {
        DocInfo doc = getById(docId);
        if (doc == null) throw new BizException("文档不存在");

        int newVersion = doc.getVersion() + 1;

        // 更新文件信息（如有新文件）
        if (StringUtils.hasText(dto.getFileUrl())) {
            doc.setFileName(dto.getFileName());
            doc.setFileUrl(dto.getFileUrl());
            doc.setFileSize(dto.getFileSize());
            doc.setFileType(dto.getFileType());
            doc.setFileMd5(dto.getFileMd5());
        }

        if (StringUtils.hasText(dto.getTitle())) doc.setTitle(dto.getTitle());
        if (dto.getContent() != null) doc.setContent(dto.getContent());
        if (dto.getCategoryId() != null) doc.setCategoryId(dto.getCategoryId());

        doc.setVersion(newVersion);
        updateById(doc);

        // 保存版本记录
        saveVersion(docId, newVersion, doc.getTitle(), doc.getContent(),
                doc.getFileUrl(), doc.getFileSize(),
                dto.getChangeDesc() != null ? dto.getChangeDesc() : "更新文档", userId);

        publishEvent("doc.update", buildEventPayload(doc, userId, doc.getCreatorName()));

        return doc;
    }

    @Override
    @Transactional
    public void deleteDoc(Long docId, Long userId) {
        DocInfo doc = getById(docId);
        if (doc == null) throw new BizException("文档不存在");
        removeById(docId);

        publishEvent("doc.delete", buildEventPayload(doc, userId, doc.getCreatorName()));
    }

    @Override
    public IPage<DocInfo> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                     List<Long> categoryIds, String status) {
        LambdaQueryWrapper<DocInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(DocInfo::getTitle, keyword);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(DocInfo::getCategoryId, categoryIds);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DocInfo::getStatus, status);
        }
        wrapper.orderByDesc(DocInfo::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public List<DocVersion> getVersions(Long docId) {
        return versionMapper.selectList(
                new LambdaQueryWrapper<DocVersion>()
                        .eq(DocVersion::getDocId, docId)
                        .orderByDesc(DocVersion::getVersion));
    }

    @Override
    @Transactional
    public DocInfo revertVersion(Long docId, Integer targetVersion, Long userId) {
        DocInfo doc = getById(docId);
        if (doc == null) throw new BizException("文档不存在");

        DocVersion targetVer = versionMapper.selectOne(
                new LambdaQueryWrapper<DocVersion>()
                        .eq(DocVersion::getDocId, docId)
                        .eq(DocVersion::getVersion, targetVersion));
        if (targetVer == null) throw new BizException("目标版本不存在");

        // 用目标版本的内容覆盖当前文档
        doc.setTitle(targetVer.getTitle());
        doc.setContent(targetVer.getContent());
        doc.setFileUrl(targetVer.getFileUrl());
        doc.setFileSize(targetVer.getFileSize());
        doc.setVersion(doc.getVersion() + 1);
        updateById(doc);

        // 保存回滚操作作为一个新版本
        saveVersion(docId, doc.getVersion(), doc.getTitle(), doc.getContent(),
                targetVer.getFileUrl(), targetVer.getFileSize(),
                "回滚到版本 " + targetVersion, userId);

        publishEvent("doc.update", buildEventPayload(doc, userId, doc.getCreatorName()));

        return doc;
    }

    private void saveVersion(Long docId, int version, String title, String content,
                              String fileUrl, Long fileSize, String changeDesc, Long userId) {
        DocVersion v = new DocVersion();
        v.setDocId(docId);
        v.setVersion(version);
        v.setTitle(title);
        v.setContent(content);
        v.setFileUrl(fileUrl);
        v.setFileSize(fileSize);
        v.setChangeDesc(changeDesc);
        v.setCreatorId(userId);
        versionMapper.insert(v);
    }

    private Map<String, Object> buildEventPayload(DocInfo doc, Long userId, String username) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("docId", doc.getId());
        payload.put("title", doc.getTitle());
        payload.put("content", doc.getContent());
        payload.put("categoryId", doc.getCategoryId());
        payload.put("status", doc.getStatus());
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    private void publishEvent(String routingKey, Map<String, Object> payload) {
        rabbitTemplate.convertAndSend(RabbitMQConstant.DOC_EXCHANGE, routingKey, payload);
    }
}
