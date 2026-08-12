-- Account deletion (soft delete / anonymization — see UserService#deleteAccount)
ALTER TABLE users ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;

-- Block/mute a user
CREATE TABLE blocked_users (
    id          BIGSERIAL PRIMARY KEY,
    blocker_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX idx_blocked_users_blocker ON blocked_users (blocker_id);
