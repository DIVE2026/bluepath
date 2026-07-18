CREATE TABLE IF NOT EXISTS mission_qr_nonces (
    nonce VARCHAR(64) PRIMARY KEY,
    exhibit_code VARCHAR(120) NOT NULL,
    exhibit_title VARCHAR(240) NOT NULL DEFAULT '',
    session_id VARCHAR(80) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    signature VARCHAR(128) NOT NULL,
    mission_id VARCHAR(36) UNIQUE REFERENCES mission_evidence(id) ON DELETE SET NULL,
    used_at TIMESTAMPTZ,
    issued_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_mission_qr_nonces_exhibit_code ON mission_qr_nonces (exhibit_code);
CREATE INDEX IF NOT EXISTS ix_mission_qr_nonces_session_id ON mission_qr_nonces (session_id);
CREATE INDEX IF NOT EXISTS ix_mission_qr_nonces_expires_at ON mission_qr_nonces (expires_at);
CREATE INDEX IF NOT EXISTS ix_mission_qr_nonces_used_at ON mission_qr_nonces (used_at);

CREATE TABLE IF NOT EXISTS program_participation (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_id VARCHAR(160) NOT NULL,
    program_title TEXT NOT NULL DEFAULT '',
    status VARCHAR(40) NOT NULL DEFAULT 'enrolled',
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attended_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    pre_assessment INTEGER,
    post_assessment INTEGER,
    metadata_json JSON NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_program_participation_user_program UNIQUE (user_id, program_id),
    CONSTRAINT ck_program_participation_pre_score CHECK (pre_assessment IS NULL OR pre_assessment BETWEEN 0 AND 100),
    CONSTRAINT ck_program_participation_post_score CHECK (post_assessment IS NULL OR post_assessment BETWEEN 0 AND 100)
);

CREATE INDEX IF NOT EXISTS ix_program_participation_program_id ON program_participation (program_id);
CREATE INDEX IF NOT EXISTS ix_program_participation_status ON program_participation (status);
CREATE INDEX IF NOT EXISTS ix_program_participation_attended_at ON program_participation (attended_at);
