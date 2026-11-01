package com.ccmmd.chess.authservice.repository;

import com.ccmmd.chess.authservice.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Drops any outstanding tokens for an account, so a new request invalidates the previous link. */
    void deleteByUserId(String userId);
}
