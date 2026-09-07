package com.myy.knowledgedoc.dto;

import lombok.Data;

@Data
public class DocPageDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String keyword;      // 标题关键词
    private Long categoryId;
    private String status;
    private String orderBy;      // create_time / update_time
}
