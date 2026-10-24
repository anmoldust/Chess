CREATE TABLE analysis_reports (
    id              VARCHAR(36) PRIMARY KEY,
    game_id         VARCHAR(36) NOT NULL UNIQUE,
    accuracy_white  DOUBLE PRECISION NOT NULL,
    accuracy_black  DOUBLE PRECISION NOT NULL,
    blunders_white  INTEGER NOT NULL,
    blunders_black  INTEGER NOT NULL,
    moves_analysed  INTEGER NOT NULL,
    engine_backed   BOOLEAN NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
