-- Add an optional image URL to community posts.
-- This migration is idempotent and preserves existing posts.
ALTER TABLE community_posts
  ADD COLUMN IF NOT EXISTS image_url TEXT NOT NULL DEFAULT '';
