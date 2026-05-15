package com.flowboard.notification_service.config;

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
    @DisplayName("queue and exchange beans are configured as expected")
    void queueAndExchangeBeans_areConfigured() {
        TopicExchange exchange = config.flowboardExchange();
        Queue assignment = config.cardAssignmentQueue();
        Queue dueDate = config.dueDateQueue();
        Queue invite = config.inviteQueue();
        Queue boardEvents = config.boardEventsQueue();

        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.FLOWBOARD_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(assignment.getName()).isEqualTo(RabbitMQConfig.CARD_ASSIGNMENT_QUEUE);
        assertThat(assignment.getArguments()).containsEntry("x-dead-letter-exchange", RabbitMQConfig.FLOWBOARD_EXCHANGE + ".dlx");
        assertThat(dueDate.getName()).isEqualTo(RabbitMQConfig.DUE_DATE_QUEUE);
        assertThat(invite.getName()).isEqualTo(RabbitMQConfig.INVITE_QUEUE);
        assertThat(boardEvents.getName()).isEqualTo(RabbitMQConfig.BOARD_EVENTS_QUEUE);
    }

    @Test
    @DisplayName("bindings and converter beans are configured")
    void bindingsAndConverterBeans_areConfigured() {
        Binding assignment = config.assignmentBinding();
        Binding dueDate = config.dueDateBinding();
        Binding invite = config.inviteBinding();
        Binding boardEvents = config.boardEventsBinding();
        Jackson2JsonMessageConverter converter = config.jsonMessageConverter();
        RabbitTemplate template = config.rabbitTemplate(mock(ConnectionFactory.class));

        assertThat(assignment.getRoutingKey()).isEqualTo(RabbitMQConfig.ASSIGNMENT_KEY);
        assertThat(dueDate.getRoutingKey()).isEqualTo(RabbitMQConfig.DUE_DATE_KEY);
        assertThat(invite.getRoutingKey()).isEqualTo(RabbitMQConfig.INVITE_KEY);
        assertThat(boardEvents.getRoutingKey()).isEqualTo(RabbitMQConfig.BOARD_CHANGE_KEY);
        assertThat(converter).isNotNull();
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
