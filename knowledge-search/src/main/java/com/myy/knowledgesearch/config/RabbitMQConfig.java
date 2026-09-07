package com.myy.knowledgesearch.config;

import com.myy.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消费者端也声明队列/交换机/绑定
 * <p>
 * 防止 search 服务先于 doc 服务启动时，队列不存在导致 404 NOT_FOUND
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
    public Binding bindingCreate() {
        return BindingBuilder.bind(docCreateQueue()).to(docExchange()).with("doc.create");
    }

    @Bean
    public Binding bindingUpdate() {
        return BindingBuilder.bind(docUpdateQueue()).to(docExchange()).with("doc.update");
    }

    @Bean
    public Binding bindingDelete() {
        return BindingBuilder.bind(docDeleteQueue()).to(docExchange()).with("doc.delete");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
