CREATE TABLE IF NOT EXISTS vocabulary_card (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    card_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    language VARCHAR(16) NOT NULL DEFAULT 'en',
    original_term VARCHAR(255) NOT NULL,
    normalized_term VARCHAR(255) NOT NULL,
    display_term VARCHAR(255) NOT NULL,
    template_key VARCHAR(32) NOT NULL,
    template_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    active_revision_uid VARCHAR(64) NULL,
    last_captured_at DATETIME NOT NULL,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_card_uid (card_uid),
    UNIQUE KEY uk_vocabulary_card_identity (user_id, language, normalized_term),
    KEY idx_vocabulary_card_user_status (user_id, status, updated_at),
    KEY idx_vocabulary_card_user_capture (user_id, last_captured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_card_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_uid VARCHAR(64) NOT NULL,
    card_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_ref VARCHAR(128) NULL,
    source_title VARCHAR(255) NULL,
    source_url VARCHAR(1024) NULL,
    context_text TEXT NULL,
    raw_term VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    captured_at DATETIME NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_source_uid (source_uid),
    UNIQUE KEY uk_vocabulary_source_idempotency (user_id, idempotency_key),
    KEY idx_vocabulary_source_card (card_uid, captured_at),
    CONSTRAINT fk_vocabulary_source_card FOREIGN KEY (card_uid) REFERENCES vocabulary_card(card_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_card_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    revision_uid VARCHAR(64) NOT NULL,
    card_uid VARCHAR(64) NOT NULL,
    base_revision_uid VARCHAR(64) NULL,
    author_type VARCHAR(24) NOT NULL,
    template_key VARCHAR(32) NOT NULL,
    template_version INT NOT NULL,
    content_json JSON NOT NULL,
    change_summary VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_revision_uid (revision_uid),
    KEY idx_vocabulary_revision_card (card_uid, created_at),
    CONSTRAINT fk_vocabulary_revision_card FOREIGN KEY (card_uid) REFERENCES vocabulary_card(card_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_vocabulary_preference (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    default_template_key VARCHAR(32) NOT NULL DEFAULT 'basic',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_vocabulary_preference (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS vocabulary_generation_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_uid VARCHAR(64) NOT NULL,
    card_uid VARCHAR(64) NOT NULL,
    base_revision_uid VARCHAR(64) NULL,
    template_key VARCHAR(32) NOT NULL,
    template_version INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    request_json JSON NOT NULL,
    result_revision_uid VARCHAR(64) NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL,
    available_at DATETIME NOT NULL,
    started_at DATETIME NULL,
    lease_token VARCHAR(64) NULL,
    lease_expires_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_vocabulary_job_uid (job_uid),
    KEY idx_vocabulary_job_claim (status, available_at, id),
    KEY idx_vocabulary_job_lease (status, lease_expires_at, attempt_count),
    KEY idx_vocabulary_job_card (card_uid, created_at),
    CONSTRAINT fk_vocabulary_job_card FOREIGN KEY (card_uid) REFERENCES vocabulary_card(card_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
