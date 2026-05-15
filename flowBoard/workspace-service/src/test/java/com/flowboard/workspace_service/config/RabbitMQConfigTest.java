package com.flowboard.workspace_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("rabbit beans are configured")
    void rabbitBeans_areConfigured() {
        TopicExchange exchange = config.flowboardExchange();
        Queue queue = config.inviteQueue();
        Binding binding = config.inviteBinding();
        Jackson2JsonMessageConverter converter = config.jsonMessageConverter();
        RabbitTemplate template = config.rabbitTemplate(mock(ConnectionFactory.class));

        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.FLOWBOARD_EXCHANGE);
        assertThat(queue.getName()).isEqualTo(RabbitMQConfig.INVITE_QUEUE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitMQConfig.INVITE_KEY);
        assertThat(converter).isNotNull();
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
