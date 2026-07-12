CREATE TABLE IF NOT EXISTS vocabulary_theme (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    theme_uid VARCHAR(64) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    user_id BIGINT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    current_version INT NOT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_theme_uid (theme_uid),
    KEY idx_vocabulary_theme_user_status (user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_theme_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    revision_uid VARCHAR(64) NOT NULL,
    theme_uid VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    name_snapshot VARCHAR(80) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    prompt_strategy_key VARCHAR(64) NOT NULL,
    content_format_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_theme_revision_uid (revision_uid),
    UNIQUE KEY uk_vocabulary_theme_version (theme_uid, version),
    CONSTRAINT fk_vocabulary_theme_revision_theme FOREIGN KEY (theme_uid) REFERENCES vocabulary_theme(theme_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_vocabulary_theme_recent (
    user_id BIGINT NOT NULL,
    theme_uid VARCHAR(64) NOT NULL,
    last_used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, theme_uid),
    KEY idx_vocabulary_theme_recent (user_id, last_used_at),
    CONSTRAINT fk_vocabulary_theme_recent_theme FOREIGN KEY (theme_uid) REFERENCES vocabulary_theme(theme_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card' AND column_name = 'theme_uid'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card ADD COLUMN theme_uid VARCHAR(64) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card' AND column_name = 'theme_version'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card ADD COLUMN theme_version INT NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_generation_job' AND column_name = 'theme_uid'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD COLUMN theme_uid VARCHAR(64) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_generation_job' AND column_name = 'theme_version'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_generation_job ADD COLUMN theme_version INT NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card_revision' AND column_name = 'theme_uid'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN theme_uid VARCHAR(64) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card_revision' AND column_name = 'theme_version'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN theme_version INT NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card_revision' AND column_name = 'core_json'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN core_json JSON NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card_revision' AND column_name = 'content_markdown'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN content_markdown MEDIUMTEXT NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'vocabulary_card_revision' AND column_name = 'content_format_version'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE vocabulary_card_revision ADD COLUMN content_format_version INT NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user_vocabulary_preference' AND column_name = 'default_theme_uid'
);
SET @sql = IF(@column_exists = 0,
    'ALTER TABLE user_vocabulary_preference ADD COLUMN default_theme_uid VARCHAR(64) NULL',
    'SELECT 1');
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;
