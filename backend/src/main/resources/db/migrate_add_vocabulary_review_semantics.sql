SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_card'
      AND column_name = 'conflict_candidate_revision_uid'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card ADD COLUMN conflict_candidate_revision_uid VARCHAR(64) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_generation_job'
      AND column_name = 'generation_outcome'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD COLUMN generation_outcome VARCHAR(24) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_generation_job'
      AND column_name = 'warning'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD COLUMN warning VARCHAR(64) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;
