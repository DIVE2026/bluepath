DO $$
DECLARE
  needs_legacy_backfill BOOLEAN;
BEGIN
  SELECT NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'video_learning_evidence'
      AND column_name = 'completed_at'
  ) INTO needs_legacy_backfill;

  ALTER TABLE video_learning_evidence
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reflection TEXT NOT NULL DEFAULT '';

  IF needs_legacy_backfill THEN
    UPDATE video_learning_evidence
    SET completed_at = verified_at
    WHERE completed_at IS NULL;
  END IF;
END $$;
