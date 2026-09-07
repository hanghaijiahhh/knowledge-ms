package com.myy.knowledgedoc.dto;

import lombok.Data;

@Data
public class DocUpdateDTO {
    private String title;
    private String content;
    private Long categoryId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private String fileMd5;
    private String changeDesc;  // 版本变更说明
}
