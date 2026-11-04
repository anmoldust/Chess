package com.ccmmd.chess.authservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpResetNotifierTest {

    private final MailSender mailSender = mock(MailSender.class);
    private final SmtpResetNotifier notifier = new SmtpResetNotifier(mailSender, "no-reply@ccmmd.local");

    @Test
    @DisplayName("emails the reset link to the account owner")
    void emailsTheLinkToTheAccountOwner() {
        notifier.deliver("player@example.com", "https://ccmmd.example/#/reset?token=abc", Duration.ofMinutes(30));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("player@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@ccmmd.local");
        assertThat(message.getText())
                .contains("https://ccmmd.example/#/reset?token=abc")
                .contains("30 minutes");
    }

    @Test
    @DisplayName("a mail failure is logged, not thrown — the response must not reveal the account exists")
    void aDeliveryFailureIsLoggedNotThrown() {
        doThrow(new MailSendException("smtp is down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> notifier.deliver(
                "player@example.com", "https://ccmmd.example/#/reset?token=abc", Duration.ofMinutes(30)))
                .doesNotThrowAnyException();
    }
}
