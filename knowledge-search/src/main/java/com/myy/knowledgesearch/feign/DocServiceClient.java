package com.myy.knowledgesearch.feign;

import com.myy.common.dto.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Collections;
import java.util.List;

@FeignClient(name = "knowledge-doc", fallbackFactory = DocServiceClientFallback.class)
public interface DocServiceClient {

    @GetMapping("/doc/visible-ids")
    ResponseResult<List<Long>> getVisibleDocIds(@RequestHeader("X-User-Info") String userInfo);
}

class DocServiceClientFallback implements org.springframework.cloud.openfeign.FallbackFactory<DocServiceClient> {
    @Override
    public DocServiceClient create(Throwable cause) {
        return userInfo -> ResponseResult.success(Collections.emptyList());
    }
}
