package com.myy.knowledgeaudit.config;

import com.myy.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消费者端声明审计队列，防止 audit 服务先于 doc 服务启动时 404
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange docExchange() {
        return new TopicExchange(RabbitMQConstant.DOC_EXCHANGE);
    }

    @Bean
    public Queue auditQueue() {
        return new Queue(RabbitMQConstant.AUDIT_QUEUE, true);
    }

    @Bean
    public Binding bindingAudit() {
        return BindingBuilder.bind(auditQueue()).to(docExchange()).with("doc.*");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
