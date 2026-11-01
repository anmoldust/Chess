package com.ccmmd.chess.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class User {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    /** BCrypt hash. The raw password is never stored, logged, or published. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String id, String email, String displayName, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    /** Replaces the stored password hash; used by password reset. */
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
