package com.myy.knowledgedoc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.myy.knowledgedoc.mapper")
public class KnowledgeDocApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeDocApplication.class, args);
    }

}
