package com.myy.knowledgefile.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MinioBucketInitializer {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioBucketInitializer(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket [{}] 创建成功", bucket);
            } else {
                log.info("MinIO bucket [{}] 已存在", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO bucket 初始化失败: {}", e.getMessage());
        }
    }
}
