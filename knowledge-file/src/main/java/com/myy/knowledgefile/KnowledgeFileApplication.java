package com.myy.knowledgefile;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.myy.knowledgefile.mapper")
public class KnowledgeFileApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeFileApplication.class, args);
    }

}
