-- Rematch: which side has an open rematch offer, and the game a rematch produced.
ALTER TABLE games ADD COLUMN rematch_offer_by VARCHAR(8);
ALTER TABLE games ADD COLUMN rematch_game_id VARCHAR(36);
