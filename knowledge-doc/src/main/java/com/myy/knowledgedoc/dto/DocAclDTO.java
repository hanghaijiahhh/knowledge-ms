package com.myy.knowledgedoc.dto;

import lombok.Data;

@Data
public class DocAclDTO {
    private Long userId;
    private Integer permRead;
    private Integer permWrite;
    private Integer permDownload;
}
