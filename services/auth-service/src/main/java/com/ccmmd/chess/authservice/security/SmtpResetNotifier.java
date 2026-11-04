package com.ccmmd.chess.authservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.time.Duration;

/**
 * Delivers the reset link by email over SMTP; used whenever {@code spring.mail.host} is configured.
 *
 * <p>Delivery failures are logged, not thrown. Requesting a reset answers the same way whether or
 * not the address is registered, and that uniformity is what stops the endpoint being an
 * account-enumeration oracle. If a mail outage propagated as an error it would fail for a real
 * account and succeed for an unknown one — reintroducing exactly the signal the flow avoids.
 */
public class SmtpResetNotifier implements ResetNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmtpResetNotifier.class);

    private final MailSender mailSender;
    private final String from;

    public SmtpResetNotifier(MailSender mailSender, String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void deliver(String email, String resetUrl, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your CCMMD password");
        message.setText("""
                Someone asked to reset the password for this address.

                Choose a new password here (the link is valid for %s):

                %s

                If this wasn't you, ignore this message and nothing changes.
                """.formatted(describe(validFor), resetUrl));

        try {
            mailSender.send(message);
        } catch (MailException mailFailure) {
            // Never let a mail problem change the response — see the class comment.
            log.error("could not email a password reset to {}: {}", email, mailFailure.getMessage());
        }
    }

    private static String describe(Duration validFor) {
        long minutes = validFor.toMinutes();
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
