-- BluePath PostgreSQL / Supabase schema
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(320) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(120) NOT NULL DEFAULT 'BluePath Learner',
  nickname VARCHAR(40) UNIQUE,
  profile_image_url TEXT NOT NULL DEFAULT '',
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

-- BluePath 1.3 community, following, profile, and reactions
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nickname_ci
  ON users (lower(nickname));

CREATE TABLE IF NOT EXISTS follows (
  id VARCHAR(36) PRIMARY KEY,
  follower_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  following_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_follow_pair UNIQUE (follower_id, following_id),
  CONSTRAINT ck_follow_not_self CHECK (follower_id <> following_id)
);
CREATE INDEX IF NOT EXISTS idx_follows_follower ON follows(follower_id);
CREATE INDEX IF NOT EXISTS idx_follows_following ON follows(following_id);

CREATE TABLE IF NOT EXISTS community_posts (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category VARCHAR(20) NOT NULL DEFAULT 'free' CHECK (category IN ('free', 'question')),
  title VARCHAR(240) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_community_posts_category_created
  ON community_posts(category, created_at DESC);

CREATE TABLE IF NOT EXISTS community_comments (
  id VARCHAR(36) PRIMARY KEY,
  post_id VARCHAR(36) NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  parent_id VARCHAR(36) REFERENCES community_comments(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_community_comments_post_created
  ON community_comments(post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_community_comments_parent
  ON community_comments(parent_id);

CREATE TABLE IF NOT EXISTS community_reactions (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('post', 'comment')),
  target_id VARCHAR(36) NOT NULL,
  emoji VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_reaction_toggle UNIQUE (user_id, target_type, target_id, emoji)
);
CREATE INDEX IF NOT EXISTS idx_community_reactions_target
  ON community_reactions(target_type, target_id);

-- BluePath 1.4 voyage twin, family missions, and outcome analytics
CREATE TABLE IF NOT EXISTS route_plans (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_career VARCHAR(200) NOT NULL,
  route_type VARCHAR(40) NOT NULL DEFAULT 'balanced',
  summary TEXT NOT NULL DEFAULT '',
  coach_message TEXT NOT NULL DEFAULT '',
  readiness_before INT NOT NULL DEFAULT 0,
  readiness_after INT NOT NULL DEFAULT 0,
  estimated_minutes INT NOT NULL DEFAULT 0,
  estimated_days INT NOT NULL DEFAULT 0,
  generated_by VARCHAR(40) NOT NULL DEFAULT 'rules',
  status VARCHAR(40) NOT NULL DEFAULT 'active',
  context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_route_plans_user_status
  ON route_plans(user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS route_nodes (
  id VARCHAR(36) PRIMARY KEY,
  plan_id VARCHAR(36) NOT NULL REFERENCES route_plans(id) ON DELETE CASCADE,
  order_index INT NOT NULL DEFAULT 0,
  node_type VARCHAR(40) NOT NULL,
  target_id VARCHAR(160) NOT NULL DEFAULT '',
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  source VARCHAR(240) NOT NULL DEFAULT '',
  topic VARCHAR(120) NOT NULL DEFAULT '해양교육',
  minutes INT NOT NULL DEFAULT 0,
  expected_skill_gain INT NOT NULL DEFAULT 0,
  readiness_gain INT NOT NULL DEFAULT 0,
  schedule_status VARCHAR(40) NOT NULL DEFAULT 'available',
  action_url TEXT NOT NULL DEFAULT '',
  why_this_order TEXT NOT NULL DEFAULT '',
  reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  evidence_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  competencies_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_route_nodes_plan_order
  ON route_nodes(plan_id, order_index);
CREATE INDEX IF NOT EXISTS idx_route_nodes_topic_type
  ON route_nodes(topic, node_type);

CREATE TABLE IF NOT EXISTS mission_evidence (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  mission_key VARCHAR(120) NOT NULL,
  exhibit_code VARCHAR(120) NOT NULL DEFAULT '',
  title TEXT NOT NULL,
  badge VARCHAR(160) NOT NULL DEFAULT '',
  participants INT NOT NULL DEFAULT 1,
  status VARCHAR(40) NOT NULL DEFAULT 'generated',
  completion_note TEXT NOT NULL DEFAULT '',
  skill_gains_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  mission_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  verified_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_mission_evidence_user_key UNIQUE (user_id, mission_key)
);
CREATE INDEX IF NOT EXISTS idx_mission_evidence_status
  ON mission_evidence(status, verified_at DESC);

CREATE TABLE IF NOT EXISTS route_outcome_events (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  route_plan_id VARCHAR(36) REFERENCES route_plans(id) ON DELETE SET NULL,
  node_id VARCHAR(36) REFERENCES route_nodes(id) ON DELETE SET NULL,
  event_type VARCHAR(60) NOT NULL,
  value DOUBLE PRECISION NOT NULL DEFAULT 1,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_route_outcomes_type_created
  ON route_outcome_events(event_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_route_outcomes_plan_node
  ON route_outcome_events(route_plan_id, node_id);

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
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mission_qr_nonces_expires
  ON mission_qr_nonces(expires_at, used_at);

CREATE TABLE IF NOT EXISTS program_participation (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  program_id VARCHAR(160) NOT NULL,
  program_title TEXT NOT NULL DEFAULT '',
  status VARCHAR(40) NOT NULL DEFAULT 'enrolled',
  enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  attended_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  pre_assessment INT,
  post_assessment INT,
  metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_program_participation_user_program UNIQUE (user_id, program_id),
  CONSTRAINT ck_program_participation_pre_score CHECK (pre_assessment IS NULL OR pre_assessment BETWEEN 0 AND 100),
  CONSTRAINT ck_program_participation_post_score CHECK (post_assessment IS NULL OR post_assessment BETWEEN 0 AND 100)
);
CREATE INDEX IF NOT EXISTS idx_program_participation_program_status
  ON program_participation(program_id, status);

-- BluePath community moderation and user blocking
CREATE TABLE IF NOT EXISTS community_reports (
  id VARCHAR(36) PRIMARY KEY,
  reporter_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('post', 'comment', 'user')),
  target_id VARCHAR(36) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'pending',
  reviewed_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
  review_note TEXT NOT NULL DEFAULT '',
  reviewed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_community_report UNIQUE (reporter_id, target_type, target_id)
);
CREATE INDEX IF NOT EXISTS idx_community_reports_status
  ON community_reports(status, created_at DESC);

CREATE TABLE IF NOT EXISTS community_blocks (
  id VARCHAR(36) PRIMARY KEY,
  blocker_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_community_block UNIQUE (blocker_id, blocked_id),
  CONSTRAINT ck_community_block_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX IF NOT EXISTS idx_community_blocks_blocker
  ON community_blocks(blocker_id);
CREATE INDEX IF NOT EXISTS idx_community_blocks_blocked
  ON community_blocks(blocked_id);
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
  UNIQUE (user_id, content_id)
);
