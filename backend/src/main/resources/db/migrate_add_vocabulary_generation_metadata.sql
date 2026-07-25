SET @generation_metadata_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_card_revision'
      AND column_name = 'generation_metadata_json'
);
SET @generation_metadata_column_ddl = IF(
    @generation_metadata_column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN generation_metadata_json JSON NULL',
    'SELECT 1'
);
PREPARE vocabulary_generation_metadata_migration_stmt FROM @generation_metadata_column_ddl;
EXECUTE vocabulary_generation_metadata_migration_stmt;
DEALLOCATE PREPARE vocabulary_generation_metadata_migration_stmt;
