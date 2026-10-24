CREATE TABLE matchmaking_queue (
    id           VARCHAR(36) PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL,
    display_name VARCHAR(60),
    time_control VARCHAR(24) NOT NULL,
    queued_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    status       VARCHAR(16) NOT NULL,
    match_id     VARCHAR(36),
    version      BIGINT NOT NULL
);

CREATE INDEX ix_queue_waiting ON matchmaking_queue (status, queued_at);

CREATE TABLE matches (
    id            VARCHAR(36) PRIMARY KEY,
    game_id       VARCHAR(36) NOT NULL UNIQUE,
    white_user_id VARCHAR(36) NOT NULL,
    black_user_id VARCHAR(36) NOT NULL,
    time_control  VARCHAR(24) NOT NULL,
    matched_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
