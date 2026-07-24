SET @card_blocks_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_card_revision'
      AND column_name = 'card_blocks_json'
);
SET @card_blocks_column_ddl = IF(
    @card_blocks_column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN card_blocks_json JSON NULL',
    'SELECT 1'
);
PREPARE vocabulary_card_blocks_migration_stmt FROM @card_blocks_column_ddl;
EXECUTE vocabulary_card_blocks_migration_stmt;
DEALLOCATE PREPARE vocabulary_card_blocks_migration_stmt;

SET @card_blocks_version_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'vocabulary_card_revision'
      AND column_name = 'card_blocks_schema_version'
);
SET @card_blocks_version_column_ddl = IF(
    @card_blocks_version_column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN card_blocks_schema_version INT NULL',
    'SELECT 1'
);
PREPARE vocabulary_card_blocks_migration_stmt FROM @card_blocks_version_column_ddl;
EXECUTE vocabulary_card_blocks_migration_stmt;
DEALLOCATE PREPARE vocabulary_card_blocks_migration_stmt;
