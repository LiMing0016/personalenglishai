CREATE TABLE IF NOT EXISTS dictionary_library (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dictionary_uid VARCHAR(96) NOT NULL,
    source_uid VARCHAR(96) NULL,
    dictionary_code VARCHAR(96) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    format VARCHAR(64) NOT NULL DEFAULT 'Mdict' COMMENT 'Mdict | built_in | online',
    engine_version VARCHAR(64) NULL,
    required_engine_version VARCHAR(64) NULL,
    encoding VARCHAR(64) NULL,
    entry_count BIGINT NULL,
    resource_count BIGINT NULL,
    mdx_file_name VARCHAR(255) NULL,
    mdd_file_name VARCHAR(255) NULL,
    cover_image_path VARCHAR(1000) NULL,
    mdx_size_bytes BIGINT NULL,
    mdd_size_bytes BIGINT NULL,
    examples_count BIGINT NULL,
    license_status VARCHAR(32) NOT NULL DEFAULT 'unknown',
    storage_type VARCHAR(32) NOT NULL DEFAULT 'local' COMMENT 'local | online | built_in',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'installed' COMMENT 'installed | importing | imported | failed | disabled',
    metadata_json JSON NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_library_uid (dictionary_uid),
    UNIQUE KEY uk_dictionary_library_code (dictionary_code),
    KEY idx_dictionary_library_source (source_uid),
    KEY idx_dictionary_library_status (status, enabled),
    KEY idx_dictionary_library_sort (sort_order, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='installed dictionary library registry';

CREATE TABLE IF NOT EXISTS dictionary_entry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_uid VARCHAR(96) NOT NULL,
    dictionary_uid VARCHAR(96) NOT NULL,
    headword VARCHAR(255) NOT NULL,
    normalized_headword VARCHAR(255) NOT NULL,
    source_entry_id VARCHAR(255) NULL,
    part_of_speech VARCHAR(128) NULL,
    raw_html MEDIUMTEXT NULL,
    clean_text MEDIUMTEXT NULL,
    quality_score INT NOT NULL DEFAULT 0,
    metadata_json JSON NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_entry_uid (entry_uid),
    UNIQUE KEY uk_dictionary_entry_source (dictionary_uid, source_entry_id),
    KEY idx_dictionary_entry_lookup (normalized_headword),
    KEY idx_dictionary_entry_dictionary (dictionary_uid, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary headword entries';

CREATE TABLE IF NOT EXISTS dictionary_pronunciation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_uid VARCHAR(96) NOT NULL,
    region VARCHAR(32) NULL COMMENT 'uk | us | other',
    phonetic VARCHAR(255) NOT NULL,
    audio_resource_uid VARCHAR(96) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_dictionary_pronunciation_entry (entry_uid, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary pronunciations';

CREATE TABLE IF NOT EXISTS dictionary_sense (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sense_uid VARCHAR(96) NOT NULL,
    entry_uid VARCHAR(96) NOT NULL,
    part_of_speech VARCHAR(128) NULL,
    definition_en TEXT NULL,
    definition_zh TEXT NULL,
    grammar_label VARCHAR(255) NULL,
    register_label VARCHAR(255) NULL,
    metadata_json JSON NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_sense_uid (sense_uid),
    KEY idx_dictionary_sense_entry (entry_uid, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary senses';

CREATE TABLE IF NOT EXISTS dictionary_example (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    example_uid VARCHAR(96) NOT NULL,
    entry_uid VARCHAR(96) NOT NULL,
    sense_uid VARCHAR(96) NULL,
    text_en TEXT NOT NULL,
    text_zh TEXT NULL,
    source VARCHAR(64) NULL,
    difficulty VARCHAR(32) NULL,
    metadata_json JSON NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_example_uid (example_uid),
    KEY idx_dictionary_example_entry (entry_uid, sort_order),
    KEY idx_dictionary_example_sense (sense_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary examples';

CREATE TABLE IF NOT EXISTS dictionary_phrase (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phrase_uid VARCHAR(96) NOT NULL,
    entry_uid VARCHAR(96) NOT NULL,
    phrase_text VARCHAR(512) NOT NULL,
    definition_en TEXT NULL,
    definition_zh TEXT NULL,
    metadata_json JSON NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_phrase_uid (phrase_uid),
    KEY idx_dictionary_phrase_entry (entry_uid, sort_order),
    KEY idx_dictionary_phrase_text (phrase_text)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary phrases and idioms';

CREATE TABLE IF NOT EXISTS dictionary_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_uid VARCHAR(96) NOT NULL,
    dictionary_uid VARCHAR(96) NOT NULL,
    resource_key VARCHAR(1000) NOT NULL,
    resource_type VARCHAR(64) NULL COMMENT 'image | audio | css | other',
    file_name VARCHAR(255) NULL,
    mime_type VARCHAR(128) NULL,
    storage_path VARCHAR(1000) NOT NULL,
    size_bytes BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_resource_uid (resource_uid),
    UNIQUE KEY uk_dictionary_resource_key (dictionary_uid, resource_key(255)),
    KEY idx_dictionary_resource_dictionary (dictionary_uid, resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary mdd resources';

CREATE TABLE IF NOT EXISTS dictionary_import_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    import_job_uid VARCHAR(96) NOT NULL,
    dictionary_uid VARCHAR(96) NOT NULL,
    source_uid VARCHAR(96) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending | running | completed | failed',
    import_limit INT NULL,
    processed_entries INT NOT NULL DEFAULT 0,
    imported_entries INT NOT NULL DEFAULT 0,
    failed_entries INT NOT NULL DEFAULT 0,
    imported_examples INT NOT NULL DEFAULT 0,
    imported_phrases INT NOT NULL DEFAULT 0,
    error_message TEXT NULL,
    result_json JSON NULL,
    created_by BIGINT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dictionary_import_job_uid (import_job_uid),
    KEY idx_dictionary_import_job_dictionary (dictionary_uid, created_at),
    KEY idx_dictionary_import_job_status (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='dictionary content import jobs';
