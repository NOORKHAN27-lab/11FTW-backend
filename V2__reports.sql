CREATE TABLE reports (
    id           BIGSERIAL PRIMARY KEY,
    reporter_id  BIGINT NOT NULL REFERENCES users(id),
    target_type  VARCHAR(10) NOT NULL,
    target_id    BIGINT NOT NULL,
    reason       VARCHAR(30) NOT NULL,
    details      TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_reports_status ON reports (status);
