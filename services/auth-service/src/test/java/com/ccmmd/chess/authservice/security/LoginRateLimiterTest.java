package com.ccmmd.chess.authservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: the limiter is constructed directly, with no Spring context. */
class LoginRateLimiterTest {

    @Test
    @DisplayName("failures up to the limit are allowed, the next one is blocked")
    void blocksAfterTheLimit() {
        LoginRateLimiter limiter = new LoginRateLimiter(3, Duration.ofMinutes(5));
        limiter.recordFailure("a@b.c");
        limiter.recordFailure("a@b.c");

        assertThatCode(() -> limiter.checkAllowed("a@b.c")).doesNotThrowAnyException();

        limiter.recordFailure("a@b.c");
        assertThatThrownBy(() -> limiter.checkAllowed("a@b.c"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("a successful sign-in clears the record")
    void resetClearsTheRecord() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, Duration.ofMinutes(5));
        limiter.recordFailure("a@b.c");
        limiter.recordFailure("a@b.c");

        limiter.reset("a@b.c");

        assertThatCode(() -> limiter.checkAllowed("a@b.c")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one account being locked out does not affect another")
    void isScopedPerAccount() {
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMinutes(5));
        limiter.recordFailure("a@b.c");

        assertThatThrownBy(() -> limiter.checkAllowed("a@b.c"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("other@b.c")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("failures older than the window stop counting")
    void oldFailuresExpire() throws InterruptedException {
        LoginRateLimiter limiter = new LoginRateLimiter(1, Duration.ofMillis(40));
        limiter.recordFailure("a@b.c");

        assertThatThrownBy(() -> limiter.checkAllowed("a@b.c"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        Thread.sleep(80);
        assertThatCode(() -> limiter.checkAllowed("a@b.c")).doesNotThrowAnyException();
    }
}
