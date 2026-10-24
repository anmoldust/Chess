package com.ccmmd.chess.platform.amqp;

/** The names that make up the broker topology. Shared so producers and consumers agree. */
public final class RabbitNames {

    /** Topic exchange every domain event is published to; the routing key is the event type. */
    public static final String EVENTS_EXCHANGE = "chess.events";

    /** Dead-letter exchange. Every queue's DLQ is bound here so poison messages are visible. */
    public static final String DEAD_LETTER_EXCHANGE = "chess.dlx";

    /** Dead-letter routing key for a given event type. */
    public static String deadLetterRoutingKey(String eventType) {
        return "dlq." + eventType;
    }

    /** Dead-letter queue name for a given queue. */
    public static String deadLetterQueue(String queueName) {
        return queueName + ".dlq";
    }

    /** Conventional queue name for a service's consumer, e.g. {@code chess.notification-service}. */
    public static String serviceQueue(String serviceName) {
        return "chess." + serviceName;
    }

    private RabbitNames() {
    }
}
