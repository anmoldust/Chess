package com.ccmmd.chess.gameservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class,
        scanBasePackages = {
                "com.ccmmd.chess.gameservice",
                "com.ccmmd.chess.platform"
        })
@EntityScan({
        "com.ccmmd.chess.gameservice",
        "com.ccmmd.chess.platform.messaging"
})
@EnableJpaRepositories({
        "com.ccmmd.chess.gameservice",
        "com.ccmmd.chess.platform.messaging"
})
public class GameServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServiceApplication.class, args);
    }
}
