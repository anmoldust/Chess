package com.ccmmd.chess.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Scanning is declared explicitly. The platform libraries are not auto-configurations, and two
 * {@code @EntityScan} sources silently replace one another — a service whose own entities quietly
 * disappear at runtime is a miserable bug to chase.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded on purpose: without a
 * {@code UserDetailsService} Spring Boot helpfully logs a generated password at every start,
 * which looks like a misconfiguration in production and is not one here — this service verifies
 * passwords itself.
 */
@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class,
        scanBasePackages = {
                "com.ccmmd.chess.authservice",
                "com.ccmmd.chess.platform"
        })
@EntityScan({
        "com.ccmmd.chess.authservice",
        "com.ccmmd.chess.platform.messaging"
})
@EnableJpaRepositories({
        "com.ccmmd.chess.authservice",
        "com.ccmmd.chess.platform.messaging"
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
