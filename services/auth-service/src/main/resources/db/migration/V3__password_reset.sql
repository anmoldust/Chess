-- Password reset: single-use, expiring tokens.
--
-- Only the SHA-256 of a token is stored, never the token itself, so a database leak does not hand
-- an attacker a working reset link.
CREATE TABLE password_reset_token (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_password_reset_user ON password_reset_token (user_id);
