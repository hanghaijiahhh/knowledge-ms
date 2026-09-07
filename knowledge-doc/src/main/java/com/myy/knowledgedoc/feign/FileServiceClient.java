package com.myy.knowledgedoc.feign;

import com.myy.common.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 调用 file 服务
 */
@FeignClient(name = "knowledge-file", fallbackFactory = FileServiceClientFallback.class)
public interface FileServiceClient {

    @GetMapping("/file/info/{id}")
    ResponseResult<Object> getFileInfo(@PathVariable Long id);
}
