package com.ccmmd.chess.authservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits repeated failed sign-ins for an account.
 *
 * <p>Deliberately small: a per-instance, in-memory sliding window. That is enough to blunt online
 * password guessing, and it is honest about its limit — it does <b>not</b> coordinate across
 * replicas, so N instances allow roughly N times the attempts. A shared counter in Redis is the
 * production answer; this is the correct behaviour with the smallest operational dependency.
 *
 * <p>Keyed on the account, not the IP: an attacker rotates IPs, and locking out a shared address
 * would punish everyone behind it.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${ccmmd.auth.login.max-attempts:5}") int maxAttempts,
            @Value("${ccmmd.auth.login.window:PT5M}") Duration window) {
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /** Throws when the account has exhausted its attempts for the current window. */
    public void checkAllowed(String accountKey) {
        if (recentFailures(accountKey) >= maxAttempts) {
            throw new TooManyLoginAttemptsException(
                    "Too many failed sign-in attempts for this account; try again later");
        }
    }

    public void recordFailure(String accountKey) {
        Deque<Instant> attempts = failures.computeIfAbsent(accountKey, key -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(Instant.now());
            if (attempts.size() >= maxAttempts) {
                // Keep the deque from growing without bound on repeated abuse.
                while (attempts.size() > maxAttempts) {
                    attempts.removeFirst();
                }
            }
        }
    }

    /** A successful sign-in clears the record, so one forgotten password does not linger. */
    public void reset(String accountKey) {
        failures.remove(accountKey);
    }

    private int recentFailures(String accountKey) {
        Deque<Instant> attempts = failures.get(accountKey);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            prune(attempts);
            return attempts.size();
        }
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }
}
