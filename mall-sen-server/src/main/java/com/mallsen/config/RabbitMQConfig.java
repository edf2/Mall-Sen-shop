package com.mallsen.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public DirectExchange stockWarningExchange() {
        return new DirectExchange("stock.warning.exchange", true, false);
    }

    @Bean
    public Queue stockWarningQueue() {
        return new Queue("stock.warning.queue", true);
    }

    @Bean
    public Binding stockWarningBinding(DirectExchange stockWarningExchange, Queue stockWarningQueue) {
        return BindingBuilder.bind(stockWarningQueue).to(stockWarningExchange).with("stock.warning");
    }

    @Bean
    public DirectExchange orderAsyncExchange() {
        return new DirectExchange("order.async.exchange", true, false);
    }

    @Bean
    public Queue orderAsyncQueue() {
        return new Queue("order.async.queue", true);
    }

    @Bean
    public Binding orderAsyncBinding(DirectExchange orderAsyncExchange, Queue orderAsyncQueue) {
        return BindingBuilder.bind(orderAsyncQueue).to(orderAsyncExchange).with("order.async");
    }

    @Bean
    public DirectExchange aiPushExchange() {
        return new DirectExchange("ai.push.exchange", true, false);
    }

    @Bean
    public Queue aiPushQueue() {
        return new Queue("ai.push.queue", true);
    }

    @Bean
    public Binding aiPushBinding(DirectExchange aiPushExchange, Queue aiPushQueue) {
        return BindingBuilder.bind(aiPushQueue).to(aiPushExchange).with("ai.push");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
