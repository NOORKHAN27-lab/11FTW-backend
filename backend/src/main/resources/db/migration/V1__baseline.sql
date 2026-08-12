-- ============================================================================
-- 11FTW — Flyway baseline
-- Matches what Hibernate's entity classes actually map to (enums as
-- VARCHAR via @Enumerated(EnumType.STRING), not native Postgres enum
-- types — that's a deliberate difference from docs/11FTW_schema.sql,
-- which is a hand-written design reference, not the literal DDL).
--
-- If you're running this against a database that Hibernate's old
-- ddl-auto=update already created, set:
--   spring.flyway.baseline-on-migrate=true
-- (already set in application.properties) so Flyway marks that existing
-- database as "already at V1" instead of trying to recreate it.
-- ============================================================================

-- ---------- Users ----------
CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    google_id              VARCHAR(255) UNIQUE,
    password_hash          VARCHAR(255),
    email                  VARCHAR(255) NOT NULL UNIQUE,
    username               VARCHAR(255) NOT NULL UNIQUE,
    phone_number           VARCHAR(255) NOT NULL,
    profile_photo_url      VARCHAR(255),
    home_province          VARCHAR(20),
    email_verified         BOOLEAN NOT NULL DEFAULT false,
    failed_login_attempts  INTEGER NOT NULL DEFAULT 0,
    locked_until           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL
);

-- ---------- Matches ----------
CREATE TABLE matches (
    id                BIGSERIAL PRIMARY KEY,
    created_by        BIGINT NOT NULL REFERENCES users(id),

    sport             VARCHAR(20) NOT NULL,
    football_format   VARCHAR(20),
    cricket_format    VARCHAR(20),

    category_gender   VARCHAR(10) NOT NULL,
    category_age      VARCHAR(10) NOT NULL,

    province          VARCHAR(20) NOT NULL,
    address_text      TEXT NOT NULL,
    latitude          DOUBLE PRECISION,
    longitude         DOUBLE PRECISION,

    match_date        DATE NOT NULL,
    start_time        TIME NOT NULL,
    end_time          TIME NOT NULL,

    max_players       INTEGER NOT NULL,
    fee_text          VARCHAR(255),

    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_matches_province_sport_status ON matches (province, sport, status);
CREATE INDEX idx_matches_end_time ON matches (match_date, end_time);

-- ---------- Match participants ----------
CREATE TABLE match_participants (
    id          BIGSERIAL PRIMARY KEY,
    match_id    BIGINT NOT NULL REFERENCES matches(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    joined_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (match_id, user_id)
);

CREATE INDEX idx_match_participants_match ON match_participants (match_id);

-- ---------- Password reset tokens ----------
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL
);

-- ---------- Email verification tokens ----------
CREATE TABLE email_verification_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL
);

-- ---------- Refresh tokens ----------
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL
);
