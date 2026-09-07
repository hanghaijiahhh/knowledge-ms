package com.myy.knowledgefile.dto;

import lombok.Data;

@Data
public class ChunkInitDTO {
    private String fileName;
    private Long fileSize;
    private String fileMd5;
    private Integer totalChunks;      // 总分片数
    private Long chunkSize;           // 每片大小(字节)
}
