CREATE TABLE app_users (
    id            VARCHAR(36) PRIMARY KEY,
    email         VARCHAR(190) NOT NULL UNIQUE,
    display_name  VARCHAR(60) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL
);
