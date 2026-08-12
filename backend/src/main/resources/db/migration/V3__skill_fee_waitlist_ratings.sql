-- Skill level + structured fee split
ALTER TABLE matches ADD COLUMN skill_level VARCHAR(20) NOT NULL DEFAULT 'ANY';
ALTER TABLE matches ADD COLUMN total_fee_amount INTEGER;

-- Waitlist: CONFIRMED participants count toward max_players, WAITLISTED don't
ALTER TABLE match_participants ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
CREATE INDEX idx_match_participants_status ON match_participants (match_id, status);

-- Post-match ratings (attendance + punctuality) feeding User#reliabilityScore
CREATE TABLE match_ratings (
    id             BIGSERIAL PRIMARY KEY,
    match_id       BIGINT NOT NULL REFERENCES matches(id),
    rater_id       BIGINT NOT NULL REFERENCES users(id),
    rated_user_id  BIGINT NOT NULL REFERENCES users(id),
    attended       BOOLEAN NOT NULL,
    punctual       BOOLEAN NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (match_id, rater_id, rated_user_id)
);

CREATE INDEX idx_match_ratings_rated_user ON match_ratings (rated_user_id);
