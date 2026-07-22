ALTER TABLE users ADD COLUMN IF NOT EXISTS guardian_consent_version VARCHAR(40) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS guardian_consented_at TIMESTAMPTZ;
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE learning_records ADD COLUMN IF NOT EXISTS device_id VARCHAR(80) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS user_progress (
  user_id VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  xp INTEGER NOT NULL DEFAULT 0,
  quiz_tier_rank INTEGER NOT NULL DEFAULT 1,
  diamond_advanced_quiz_passed BOOLEAN NOT NULL DEFAULT FALSE,
  skill_mastery_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  skill_evidence_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_user_progress_quiz_rank CHECK (quiz_tier_rank BETWEEN 1 AND 4),
  CONSTRAINT ck_user_progress_xp CHECK (xp >= 0)
);

CREATE TABLE IF NOT EXISTS quiz_sessions (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  tier VARCHAR(40) NOT NULL,
  source VARCHAR(80) NOT NULL DEFAULT 'server',
  questions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  expires_at TIMESTAMPTZ NOT NULL,
  submitted_at TIMESTAMPTZ,
  result_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_user_created ON quiz_sessions(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_expires ON quiz_sessions(expires_at);

CREATE TABLE IF NOT EXISTS video_learning_evidence (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content_id VARCHAR(160) NOT NULL,
  duration_seconds INTEGER NOT NULL DEFAULT 0,
  watched_seconds INTEGER NOT NULL DEFAULT 0,
  coverage_percent INTEGER NOT NULL DEFAULT 0,
  intervals_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_video_evidence_user_content UNIQUE (user_id, content_id),
  CONSTRAINT ck_video_evidence_coverage CHECK (coverage_percent BETWEEN 0 AND 100)
);
CREATE INDEX IF NOT EXISTS idx_video_evidence_user ON video_learning_evidence(user_id, verified_at DESC);

CREATE TABLE IF NOT EXISTS guardian_consent_requests (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  guardian_email VARCHAR(320) NOT NULL,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  consent_version VARCHAR(40) NOT NULL DEFAULT '2026-07',
  expires_at TIMESTAMPTZ NOT NULL,
  consented_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_guardian_consent_user ON guardian_consent_requests(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_guardian_consent_email ON guardian_consent_requests(guardian_email);

CREATE TABLE IF NOT EXISTS portfolio_credentials (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  signature VARCHAR(128) NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_portfolio_credentials_user ON portfolio_credentials(user_id, issued_at DESC);

CREATE TABLE IF NOT EXISTS paper_learning_evidence (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  content_id VARCHAR(160) NOT NULL,
  reflection TEXT NOT NULL,
  paper_status VARCHAR(30) NOT NULL DEFAULT 'current',
  verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_paper_evidence_user_content UNIQUE (user_id, content_id)
);
CREATE INDEX IF NOT EXISTS idx_paper_evidence_user ON paper_learning_evidence(user_id, verified_at DESC);
