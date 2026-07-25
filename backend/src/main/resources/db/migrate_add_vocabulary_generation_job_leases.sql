SET @lease_token_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'vocabulary_generation_job'
      AND COLUMN_NAME = 'lease_token'
);
SET @lease_token_column_ddl = IF(
    @lease_token_column_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD COLUMN lease_token VARCHAR(64) NULL AFTER started_at',
    'SELECT 1'
);
PREPARE vocabulary_lease_migration_stmt FROM @lease_token_column_ddl;
EXECUTE vocabulary_lease_migration_stmt;
DEALLOCATE PREPARE vocabulary_lease_migration_stmt;

SET @lease_expires_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'vocabulary_generation_job'
      AND COLUMN_NAME = 'lease_expires_at'
);
SET @lease_expires_column_ddl = IF(
    @lease_expires_column_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD COLUMN lease_expires_at DATETIME NULL AFTER lease_token',
    'SELECT 1'
);
PREPARE vocabulary_lease_migration_stmt FROM @lease_expires_column_ddl;
EXECUTE vocabulary_lease_migration_stmt;
DEALLOCATE PREPARE vocabulary_lease_migration_stmt;

SET @lease_index_exists = (
    SELECT COUNT(DISTINCT INDEX_NAME)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'vocabulary_generation_job'
      AND INDEX_NAME = 'idx_vocabulary_job_lease'
);
SET @lease_index_ddl = IF(
    @lease_index_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD KEY idx_vocabulary_job_lease (status, lease_expires_at, attempt_count)',
    'SELECT 1'
);
PREPARE vocabulary_lease_migration_stmt FROM @lease_index_ddl;
EXECUTE vocabulary_lease_migration_stmt;
DEALLOCATE PREPARE vocabulary_lease_migration_stmt;

UPDATE vocabulary_generation_job
SET lease_expires_at = COALESCE(started_at, available_at, created_at, CURRENT_TIMESTAMP)
WHERE status = 'running'
  AND lease_expires_at IS NULL;
