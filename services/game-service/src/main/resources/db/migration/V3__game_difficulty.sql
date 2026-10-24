-- Difficulty is chosen when an AI game is created and travels with the engine request.
ALTER TABLE games ADD COLUMN difficulty VARCHAR(16) NOT NULL DEFAULT 'MEDIUM';
