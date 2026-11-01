-- Rating history: one row per player per game, so a profile can show progression.
--
-- The unique key is the idempotency backstop — a redelivered game.ended cannot move a rating, or
-- record its change, twice.
CREATE TABLE rating_change (
    id         VARCHAR(36) PRIMARY KEY,
    game_id    VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36) NOT NULL,
    old_rating INTEGER NOT NULL,
    new_rating INTEGER NOT NULL,
    played_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_rating_change_game_user UNIQUE (game_id, user_id)
);

CREATE INDEX ix_rating_change_user ON rating_change (user_id, played_at DESC);
