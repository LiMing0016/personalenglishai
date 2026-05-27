CREATE TABLE IF NOT EXISTS data_cleaning_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_uid VARCHAR(96) NOT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'dictionary | corpus | import_file',
    source_code VARCHAR(96) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    license_status VARCHAR(32) NOT NULL DEFAULT 'unknown' COMMENT 'unknown | internal_only | licensed | blocked',
    mdx_path VARCHAR(1000) NULL,
    mdd_path VARCHAR(1000) NULL,
    examples_path VARCHAR(1000) NULL,
    cover_image_path VARCHAR(1000) NULL,
    metadata_json JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'registered' COMMENT 'registered | probed | imported | disabled',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_data_cleaning_source_uid (source_uid),
    UNIQUE KEY uk_data_cleaning_source_code (source_code),
    KEY idx_data_cleaning_source_type_status (source_type, status),
    KEY idx_data_cleaning_source_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='admin data cleaning source registry';

CREATE TABLE IF NOT EXISTS data_cleaning_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_uid VARCHAR(96) NOT NULL,
    source_uid VARCHAR(96) NOT NULL,
    job_type VARCHAR(64) NOT NULL COMMENT 'dictionary_probe | dictionary_import',
    status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending | running | completed | failed',
    progress_total INT NOT NULL DEFAULT 0,
    progress_done INT NOT NULL DEFAULT 0,
    result_json JSON NULL,
    error_message TEXT NULL,
    created_by BIGINT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_data_cleaning_job_uid (job_uid),
    KEY idx_data_cleaning_job_source_time (source_uid, created_at),
    KEY idx_data_cleaning_job_type_status (job_type, status),
    CONSTRAINT fk_data_cleaning_job_source
        FOREIGN KEY (source_uid) REFERENCES data_cleaning_source(source_uid)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='admin data cleaning job ledger';
