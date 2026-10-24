-- Clocks and end reasons.
--
-- Time controls were previously only a label on a matchmaking entry, so "Blitz" meant nothing to
-- the game. These columns make it a real clock: a remaining budget per side, an increment, and the
-- instant the side to move started thinking.
ALTER TABLE games ADD COLUMN white_time_ms BIGINT;
ALTER TABLE games ADD COLUMN black_time_ms BIGINT;
ALTER TABLE games ADD COLUMN initial_ms BIGINT;
ALTER TABLE games ADD COLUMN increment_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE games ADD COLUMN clock_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE games ADD COLUMN end_reason VARCHAR(32);
