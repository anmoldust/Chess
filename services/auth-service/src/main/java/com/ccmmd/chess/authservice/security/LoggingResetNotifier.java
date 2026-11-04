package com.ccmmd.chess.authservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Writes the reset link to the log instead of emailing it; the fallback chosen by
 * {@link ResetNotifierConfig} when no mail server is configured.
 *
 * <p>The link is logged so the flow works end to end in development and stays observable in an
 * environment that has not yet been pointed at an SMTP relay.
 */
public class LoggingResetNotifier implements ResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingResetNotifier.class);

    @Override
    public void deliver(String email, String resetUrl, Duration validFor) {
        log.info("password reset requested for {} (link valid {}): {}", email, validFor, resetUrl);
    }
}
