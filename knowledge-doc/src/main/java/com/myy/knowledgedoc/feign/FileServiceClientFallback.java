package com.myy.knowledgedoc.feign;

import com.myy.common.dto.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileServiceClientFallback implements FallbackFactory<FileServiceClient> {
    @Override
    public FileServiceClient create(Throwable cause) {
        log.error("文件服务调用失败: {}", cause.getMessage());
        return id -> ResponseResult.fail("文件服务暂不可用");
    }
}
