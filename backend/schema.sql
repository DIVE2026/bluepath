-- BluePath PostgreSQL / Supabase schema
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(320) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120) NOT NULL DEFAULT 'BluePath Learner',
  role VARCHAR(40) NOT NULL DEFAULT 'learner',
  guardian_email VARCHAR(320),
  guardian_consent BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_password_reset_user_expiry
  ON password_reset_tokens(user_id, expires_at DESC);

CREATE TABLE IF NOT EXISTS user_profiles (
  user_id VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS learning_records (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_record_id VARCHAR(80) NOT NULL,
  record_type VARCHAR(40) NOT NULL,
  target_id VARCHAR(160) NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  status VARCHAR(80) NOT NULL DEFAULT '',
  client_updated_at BIGINT NOT NULL DEFAULT 0,
  synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id, client_record_id)
);

CREATE TABLE IF NOT EXISTS contents (
  id VARCHAR(160) PRIMARY KEY,
  title TEXT NOT NULL,
  content_type VARCHAR(40) NOT NULL DEFAULT 'video',
  source VARCHAR(240) NOT NULL DEFAULT '',
  url TEXT NOT NULL DEFAULT '',
  difficulty VARCHAR(40) NOT NULL DEFAULT '',
  required_tier VARCHAR(40) NOT NULL DEFAULT '브론즈',
  topic VARCHAR(120) NOT NULL DEFAULT '해양교육',
  career_tag VARCHAR(160) NOT NULL DEFAULT '',
  minutes INT NOT NULL DEFAULT 0,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS quiz_bank_items (
  id VARCHAR(160) PRIMARY KEY,
  tier VARCHAR(40) NOT NULL,
  topic VARCHAR(120) NOT NULL DEFAULT '해양교육',
  question TEXT NOT NULL,
  options JSONB NOT NULL,
  answer_index INT NOT NULL CHECK (answer_index BETWEEN 0 AND 3),
  explanation TEXT NOT NULL,
  source_title TEXT NOT NULL DEFAULT '',
  source_url TEXT NOT NULL DEFAULT '',
  active BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_quiz_tier_active
  ON quiz_bank_items(tier, active, updated_at DESC);

CREATE TABLE IF NOT EXISTS knowledge_chunks (
  id VARCHAR(160) PRIMARY KEY,
  title TEXT NOT NULL,
  organization VARCHAR(240) NOT NULL DEFAULT '',
  url TEXT NOT NULL DEFAULT '',
  content TEXT NOT NULL,
  topic VARCHAR(120) NOT NULL DEFAULT '해양교육',
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  embedding vector(1536),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_knowledge_embedding_hnsw
  ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_contents_topic_tier
  ON contents(topic, required_tier, difficulty);
CREATE INDEX IF NOT EXISTS idx_learning_user_type
  ON learning_records(user_id, record_type, synced_at DESC);

CREATE TABLE IF NOT EXISTS diamond_evidence (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  evidence_type VARCHAR(40) NOT NULL,
  title TEXT NOT NULL,
  evidence_url TEXT NOT NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'pending',
  review_note TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  reviewed_at TIMESTAMPTZ,
  UNIQUE(user_id, evidence_type)
);

CREATE TABLE IF NOT EXISTS reminders (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title VARCHAR(240) NOT NULL,
  remind_at TIMESTAMPTZ NOT NULL,
  reminder_type VARCHAR(40) NOT NULL DEFAULT 'learning',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
