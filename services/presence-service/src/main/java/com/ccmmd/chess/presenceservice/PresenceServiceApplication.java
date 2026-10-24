package com.ccmmd.chess.presenceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Scans the platform package for {@code RabbitTopologyConfig}: this service has no database, but it
 * does need the exchanges, the JSON converter and the RabbitTemplate.
 */
@SpringBootApplication(scanBasePackages = {
        "com.ccmmd.chess.presenceservice",
        "com.ccmmd.chess.platform"
})
public class PresenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PresenceServiceApplication.class, args);
    }
}
