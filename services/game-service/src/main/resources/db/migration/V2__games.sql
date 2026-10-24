CREATE TABLE games (
    id            VARCHAR(36) PRIMARY KEY,
    mode          VARCHAR(24) NOT NULL,
    status        VARCHAR(24) NOT NULL,
    fen           VARCHAR(120) NOT NULL,
    white_user_id VARCHAR(36),
    white_name    VARCHAR(60),
    black_user_id VARCHAR(36),
    black_name    VARCHAR(60),
    ai_color      VARCHAR(8),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT NOT NULL
);

CREATE TABLE game_moves (
    game_id     VARCHAR(36) NOT NULL,
    move_index  INTEGER NOT NULL,
    ply_number  INTEGER NOT NULL,
    from_square VARCHAR(2) NOT NULL,
    to_square   VARCHAR(2) NOT NULL,
    promotion   VARCHAR(1),
    uci         VARCHAR(5) NOT NULL,
    fen_after   VARCHAR(120) NOT NULL,
    CONSTRAINT pk_game_moves PRIMARY KEY (game_id, move_index),
    CONSTRAINT fk_game_moves_game FOREIGN KEY (game_id) REFERENCES games (id)
);
