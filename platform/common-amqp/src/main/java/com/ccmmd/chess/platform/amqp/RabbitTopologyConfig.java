package com.ccmmd.chess.platform.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Broker topology and the JSON codec, for any service that speaks with the bus — including ones
 * with no database at all. Scheduling lives with the outbox in common-messaging, because that is
 * the only thing that needs a timer.
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange chessEventsExchange() {
        return ExchangeBuilder.topicExchange(RabbitNames.EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange chessDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(RabbitNames.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);

        // Restrict deserialisation to our own types: an untrusted __TypeId__ header must never be
        // able to name an arbitrary class.
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages(
                "com.ccmmd.chess.platform.events",
                "com.ccmmd.chess.platform.events.payload",
                "java.util",
                "java.time");
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
