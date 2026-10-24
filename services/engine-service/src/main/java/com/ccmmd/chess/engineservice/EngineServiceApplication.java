package com.ccmmd.chess.engineservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Must scan the platform package: this service has no database, but it does need
 * {@code RabbitTopologyConfig} from common-amqp for the exchanges, the JSON converter and the
 * RabbitTemplate. Without it the queue bindings have no exchange to bind to and the context fails.
 */
@SpringBootApplication(scanBasePackages = {
        "com.ccmmd.chess.engineservice",
        "com.ccmmd.chess.platform"
})
public class EngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineServiceApplication.class, args);
    }
}
