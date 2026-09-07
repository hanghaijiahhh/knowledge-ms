package com.myy.knowledgeembedding.config;

import com.myy.common.constant.RabbitMQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange docExchange() {
        return new TopicExchange(RabbitMQConstant.DOC_EXCHANGE);
    }

    @Bean
    public Queue docEmbedQueue() {
        return new Queue(RabbitMQConstant.DOC_EMBED_QUEUE, true);
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
