CREATE TABLE IF NOT EXISTS translation_document_parse_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(96) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    ocr_status VARCHAR(32) NULL,
    provider VARCHAR(64) NULL,
    parse_mode VARCHAR(32) NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    page_count INT NOT NULL DEFAULT 0,
    block_count INT NOT NULL DEFAULT 0,
    response_json JSON NOT NULL,
    diagnosis_json JSON NULL,
    quality_json JSON NULL,
    language_profile_json JSON NULL,
    parse_job_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_translation_parse_snapshot_document (document_id),
    KEY idx_translation_parse_snapshot_status (parse_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='translation document parse snapshots';

CREATE TABLE IF NOT EXISTS translation_document_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(96) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    sha256 CHAR(64) NOT NULL,
    storage_provider VARCHAR(32) NOT NULL DEFAULT 'local',
    storage_key VARCHAR(512) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_translation_document_file_document (document_id),
    KEY idx_translation_document_file_sha256 (sha256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='translation document original file metadata';

CREATE TABLE IF NOT EXISTS translation_document_element (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(96) NOT NULL,
    element_id VARCHAR(128) NOT NULL,
    element_type VARCHAR(32) NOT NULL,
    element_order INT NOT NULL,
    page_number INT NOT NULL DEFAULT 0,
    text LONGTEXT NOT NULL,
    bbox JSON NULL,
    provider VARCHAR(64) NULL,
    confidence DECIMAL(6,4) NULL,
    recognition_status VARCHAR(32) NULL,
    quality_score DECIMAL(6,4) NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_translation_element_document_element (document_id, element_id),
    KEY idx_translation_element_document_page (document_id, page_number, element_order),
    CONSTRAINT fk_translation_element_snapshot FOREIGN KEY (document_id)
        REFERENCES translation_document_parse_snapshot (document_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='translation document normalized elements';

CREATE TABLE IF NOT EXISTS translation_knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(96) NOT NULL,
    chunk_id VARCHAR(128) NOT NULL,
    chunk_order INT NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    summary VARCHAR(512) NULL,
    source_element_ids_json JSON NOT NULL,
    page_numbers_json JSON NOT NULL,
    first_page_number INT NULL,
    token_count INT NOT NULL DEFAULT 0,
    quality_score DECIMAL(6,4) NULL,
    embedding_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED',
    granularity VARCHAR(32) NOT NULL DEFAULT 'small',
    start_element_order INT NOT NULL DEFAULT 0,
    end_element_order INT NOT NULL DEFAULT 0,
    section_path_json JSON NULL,
    parent_chunk_id VARCHAR(128) NULL,
    prev_chunk_id VARCHAR(128) NULL,
    next_chunk_id VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_translation_chunk_document_chunk (document_id, chunk_id),
    KEY idx_translation_chunk_document_page (document_id, first_page_number, chunk_order),
    KEY idx_translation_chunk_document_order (document_id, chunk_order),
    CONSTRAINT fk_translation_chunk_snapshot FOREIGN KEY (document_id)
        REFERENCES translation_document_parse_snapshot (document_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='translation reusable knowledge chunks';

CREATE TABLE IF NOT EXISTS translation_document_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id VARCHAR(96) NOT NULL,
    asset_id VARCHAR(128) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    page_number INT NOT NULL DEFAULT 0,
    source_element_id VARCHAR(128) NULL,
    bbox JSON NULL,
    recognized_text LONGTEXT NULL,
    provider VARCHAR(64) NULL,
    recognition_status VARCHAR(32) NULL,
    confidence DECIMAL(6,4) NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_translation_asset_document_asset (document_id, asset_id),
    KEY idx_translation_asset_document_page (document_id, page_number),
    KEY idx_translation_asset_document_type (document_id, asset_type),
    CONSTRAINT fk_translation_asset_snapshot FOREIGN KEY (document_id)
        REFERENCES translation_document_parse_snapshot (document_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='translation document assets for OCR/table/formula iterations';
