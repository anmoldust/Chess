CREATE TABLE notifications (
    id         VARCHAR(36) PRIMARY KEY,
    user_id    VARCHAR(36) NOT NULL,
    kind       VARCHAR(32) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_notifications_user ON notifications (user_id, created_at);
