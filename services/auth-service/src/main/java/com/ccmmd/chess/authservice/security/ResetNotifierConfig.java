package com.ccmmd.chess.authservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSender;

/**
 * How a reset link leaves the building.
 *
 * <p>With a mail server configured ({@code spring.mail.host} — Mailpit under {@code docker
 * compose}, or any SMTP relay in production) the link is emailed. Without one it is written to the
 * log, so the flow is complete and usable locally without pretending to send mail it cannot send.
 * Exactly one of the two beans exists.
 */
@Configuration
public class ResetNotifierConfig {

    /** Declared first so the logging fallback sees it through {@link ConditionalOnMissingBean}. */
    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    ResetNotifier smtpResetNotifier(MailSender mailSender,
            @Value("${ccmmd.auth.reset.mail-from:no-reply@ccmmd.local}") String from) {
        return new SmtpResetNotifier(mailSender, from);
    }

    @Bean
    @ConditionalOnMissingBean(ResetNotifier.class)
    ResetNotifier loggingResetNotifier() {
        return new LoggingResetNotifier();
    }
}
