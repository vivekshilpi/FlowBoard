package com.flowboard.card_service.config;

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
    void createsExpectedAmqpBeans() {
        TopicExchange exchange = config.flowboardExchange();
        Queue assignment = config.cardAssignmentQueue();
        Queue dueDate = config.dueDateQueue();
        Queue invite = config.inviteQueue();
        Binding assignmentBinding = config.assignmentBinding();
        Binding dueBinding = config.dueDateBinding();
        Binding inviteBinding = config.inviteBinding();

        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.FLOWBOARD_EXCHANGE);
        assertThat(assignment.getName()).isEqualTo(RabbitMQConfig.CARD_ASSIGNMENT_QUEUE);
        assertThat(dueDate.getName()).isEqualTo(RabbitMQConfig.DUE_DATE_QUEUE);
        assertThat(invite.getName()).isEqualTo(RabbitMQConfig.INVITE_QUEUE);
        assertThat(assignmentBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.ASSIGNMENT_KEY);
        assertThat(dueBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.DUE_DATE_KEY);
        assertThat(inviteBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.INVITE_KEY);
    }

    @Test
    void rabbitTemplateUsesJsonConverter() {
        RabbitTemplate template = config.rabbitTemplate(mock(ConnectionFactory.class));
        assertThat(config.jsonMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
