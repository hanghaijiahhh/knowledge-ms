package com.myy.knowledgedoc.config;

import com.myy.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：文档变更事件
 * <p>
 * 使用 TopicExchange，按事件类型分 routing key：
 * doc.create / doc.update / doc.delete
 * <p>
 * 当前消费者：search 服务（索引同步）、audit 服务（操作记录）
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange docExchange() {
        return new TopicExchange(RabbitMQConstant.DOC_EXCHANGE);
    }

    @Bean
    public Queue docCreateQueue() {
        return new Queue(RabbitMQConstant.DOC_CREATE_QUEUE, true);
    }

    @Bean
    public Queue docUpdateQueue() {
        return new Queue(RabbitMQConstant.DOC_UPDATE_QUEUE, true);
    }

    @Bean
    public Queue docDeleteQueue() {
        return new Queue(RabbitMQConstant.DOC_DELETE_QUEUE, true);
    }

    @Bean
    public Queue auditQueue() {
        return new Queue(RabbitMQConstant.AUDIT_QUEUE, true);
    }

    @Bean
    public Queue docEmbedQueue() {
        return new Queue(RabbitMQConstant.DOC_EMBED_QUEUE, true);
    }

    @Bean
    public Binding bindingCreate() {
        return BindingBuilder.bind(docCreateQueue()).to(docExchange())
                .with("doc.create");
    }

    @Bean
    public Binding bindingUpdate() {
        return BindingBuilder.bind(docUpdateQueue()).to(docExchange())
                .with("doc.update");
    }

    @Bean
    public Binding bindingDelete() {
        return BindingBuilder.bind(docDeleteQueue()).to(docExchange())
                .with("doc.delete");
    }

    @Bean
    public Binding bindingAudit() {
        return BindingBuilder.bind(auditQueue()).to(docExchange())
                .with("doc.*");
    }

    @Bean
    public Binding bindingEmbed() {
        return BindingBuilder.bind(docEmbedQueue()).to(docExchange())
                .with("doc.create");
    }

    @Bean
    public Binding bindingEmbedUpdate() {
        return BindingBuilder.bind(docEmbedQueue()).to(docExchange())
                .with("doc.update");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
