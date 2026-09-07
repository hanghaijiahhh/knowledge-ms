package com.myy.knowledgeaudit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.myy.knowledgeaudit.mapper")
public class KnowledgeAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAuditApplication.class, args);
    }

}
