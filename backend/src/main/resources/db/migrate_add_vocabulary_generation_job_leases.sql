ALTER TABLE vocabulary_generation_job
    ADD COLUMN IF NOT EXISTS lease_token VARCHAR(64) NULL AFTER started_at,
    ADD COLUMN IF NOT EXISTS lease_expires_at DATETIME NULL AFTER lease_token,
    ADD KEY idx_vocabulary_job_lease (status, lease_expires_at, attempt_count);

UPDATE vocabulary_generation_job
SET lease_expires_at = COALESCE(started_at, available_at, created_at, CURRENT_TIMESTAMP)
WHERE status = 'running'
  AND lease_expires_at IS NULL;
