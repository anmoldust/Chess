package com.ccmmd.chess.authservice.security;

import java.time.Duration;

/**
 * Delivers a password-reset link to the account owner.
 *
 * <p>This is the seam for email delivery. A real deployment plugs in a mail sender here; until one
 * is chosen, {@link LoggingResetNotifier} writes the link to the log, which keeps the reset flow
 * complete and testable without pretending to send mail it cannot send.
 */
public interface ResetNotifier {

    void deliver(String email, String resetUrl, Duration validFor);
}
