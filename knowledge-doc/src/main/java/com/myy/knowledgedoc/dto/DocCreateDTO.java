package com.myy.knowledgedoc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocCreateDTO {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    private Long categoryId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private String fileMd5;
    private String status;
}
