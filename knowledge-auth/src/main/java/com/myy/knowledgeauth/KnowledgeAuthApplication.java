package com.myy.knowledgeauth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.myy.knowledgeauth.mapper")
public class KnowledgeAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAuthApplication.class, args);
    }

}
