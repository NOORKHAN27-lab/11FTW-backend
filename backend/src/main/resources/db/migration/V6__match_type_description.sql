-- Match type (Friendly / Competitive / Tournament / Practice) + optional
-- free-text description — both surfaced on the new premium "Post a Match"
-- wizard.
ALTER TABLE matches ADD COLUMN match_type VARCHAR(20) NOT NULL DEFAULT 'FRIENDLY';
ALTER TABLE matches ADD COLUMN description VARCHAR(500);
