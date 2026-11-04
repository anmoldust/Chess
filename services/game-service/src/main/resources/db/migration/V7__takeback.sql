-- Takeback: which side has an open request to undo the last move, cleared when accepted/declined.
ALTER TABLE games ADD COLUMN takeback_offer_by VARCHAR(8);
