package com.myy.knowledgeagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class KnowledgeAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeAgentApplication.class, args);
    }
}
