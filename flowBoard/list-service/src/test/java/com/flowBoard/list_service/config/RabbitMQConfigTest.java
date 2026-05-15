package com.flowBoard.list_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RabbitMQConfigTest {

    @Mock
    private ConnectionFactory connectionFactory;

    @Test
    void beans_areConfigured() {
        RabbitMQConfig config = new RabbitMQConfig();

        TopicExchange exchange = config.flowboardExchange();
        Jackson2JsonMessageConverter converter = config.jsonMessageConverter();
        RabbitTemplate template = config.rabbitTemplate(connectionFactory);

        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.FLOWBOARD_EXCHANGE);
        assertThat(converter).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(connectionFactory);
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
