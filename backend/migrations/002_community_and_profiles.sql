ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url TEXT NOT NULL DEFAULT '';
CREATE UNIQUE INDEX IF NOT EXISTS ix_users_nickname ON users (nickname);
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nickname_ci ON users (lower(nickname));

CREATE TABLE IF NOT EXISTS follows (
  id VARCHAR(36) PRIMARY KEY,
  follower_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  following_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_follow_pair UNIQUE (follower_id, following_id),
  CONSTRAINT ck_follow_not_self CHECK (follower_id <> following_id)
);
CREATE INDEX IF NOT EXISTS ix_follows_follower_id ON follows(follower_id);
CREATE INDEX IF NOT EXISTS ix_follows_following_id ON follows(following_id);

CREATE TABLE IF NOT EXISTS community_posts (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  category VARCHAR(20) NOT NULL DEFAULT 'free',
  title VARCHAR(240) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_community_posts_category ON community_posts(category);
CREATE INDEX IF NOT EXISTS ix_community_posts_created_at ON community_posts(created_at);

CREATE TABLE IF NOT EXISTS community_comments (
  id VARCHAR(36) PRIMARY KEY,
  post_id VARCHAR(36) NOT NULL REFERENCES community_posts(id) ON DELETE CASCADE,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  parent_id VARCHAR(36) REFERENCES community_comments(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_community_comments_post_id ON community_comments(post_id);
CREATE INDEX IF NOT EXISTS ix_community_comments_parent_id ON community_comments(parent_id);

CREATE TABLE IF NOT EXISTS community_reactions (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_type VARCHAR(20) NOT NULL,
  target_id VARCHAR(36) NOT NULL,
  emoji VARCHAR(16) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_reaction_toggle UNIQUE (user_id, target_type, target_id, emoji),
  CONSTRAINT ck_reaction_target_type CHECK (target_type IN ('post', 'comment'))
);
CREATE INDEX IF NOT EXISTS ix_community_reactions_target ON community_reactions(target_type, target_id);
