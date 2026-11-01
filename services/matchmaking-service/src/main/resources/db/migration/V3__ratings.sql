-- Ratings: the number matchmaking pairs on.
--
-- Owned here rather than in auth-service, because the rating exists to decide who plays whom and
-- the matcher lives in this service. It is written from the game.ended event, so game-service
-- remains the only thing that decides a result.
CREATE TABLE ratings (
    user_id      VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(60),
    rating       INTEGER NOT NULL,
    games_played INTEGER NOT NULL,
    wins         INTEGER NOT NULL,
    losses       INTEGER NOT NULL,
    draws        INTEGER NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    version      BIGINT NOT NULL
);

-- The leaderboard reads this in rating order.
CREATE INDEX ix_ratings_rating ON ratings (rating DESC);
