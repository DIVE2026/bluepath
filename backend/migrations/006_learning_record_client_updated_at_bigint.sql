-- Millisecond Unix timestamps exceed PostgreSQL INTEGER (int4) range.
-- Keep this migration idempotent so it can run safely on new and existing databases.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'learning_records'
      AND column_name = 'client_updated_at'
      AND data_type = 'integer'
  ) THEN
    ALTER TABLE learning_records
      ALTER COLUMN client_updated_at TYPE BIGINT
      USING client_updated_at::BIGINT;
  END IF;
END $$;
