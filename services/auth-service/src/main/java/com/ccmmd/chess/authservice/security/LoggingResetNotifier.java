package com.ccmmd.chess.authservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Writes the reset link to the log instead of emailing it.
 *
 * <p>Honest about the gap: there is no email provider configured, and inventing one is an external
 * decision, not a coding one. The link is logged so the flow works end to end in development and is
 * observable in production until a mail sender replaces this bean.
 */
@Component
public class LoggingResetNotifier implements ResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingResetNotifier.class);

    @Override
    public void deliver(String email, String resetUrl, Duration validFor) {
        log.info("password reset requested for {} (link valid {}): {}", email, validFor, resetUrl);
    }
}
