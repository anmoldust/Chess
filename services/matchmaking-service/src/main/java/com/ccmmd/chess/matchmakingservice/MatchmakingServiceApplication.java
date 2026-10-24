package com.ccmmd.chess.matchmakingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        exclude = UserDetailsServiceAutoConfiguration.class,
        scanBasePackages = {
                "com.ccmmd.chess.matchmakingservice",
                "com.ccmmd.chess.platform"
        })
@EntityScan({
        "com.ccmmd.chess.matchmakingservice",
        "com.ccmmd.chess.platform.messaging"
})
@EnableJpaRepositories({
        "com.ccmmd.chess.matchmakingservice",
        "com.ccmmd.chess.platform.messaging"
})
public class MatchmakingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchmakingServiceApplication.class, args);
    }
}
