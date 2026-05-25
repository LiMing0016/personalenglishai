CREATE TABLE IF NOT EXISTS writing_learning_asset_preview_run (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_uid VARCHAR(96) NOT NULL,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'completed' COMMENT 'completed | failed',
    model VARCHAR(128) NULL,
    summary VARCHAR(1000) NULL,
    result_json JSON NULL,
    error_message TEXT NULL,
    input_token_count BIGINT NULL,
    output_token_count BIGINT NULL,
    item_count INT NOT NULL DEFAULT 0,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_learning_asset_preview_run_uid (run_uid),
    KEY idx_writing_learning_asset_preview_run_doc_time (document_id, generated_at),
    KEY idx_writing_learning_asset_preview_run_user_time (user_id, generated_at),
    CONSTRAINT fk_writing_learning_asset_preview_run_doc
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_writing_learning_asset_preview_run_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='writing asset DeepSeek learning preview runs';

CREATE TABLE IF NOT EXISTS writing_learning_asset_preview_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    item_uid VARCHAR(96) NOT NULL,
    run_uid VARCHAR(96) NOT NULL,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    asset_type VARCHAR(32) NOT NULL COMMENT 'word | phrase | sentence | grammar | writing_strategy',
    source_type VARCHAR(32) NOT NULL COMMENT 'user_focus | coach_feedback | system_discovered',
    display_text VARCHAR(1000) NOT NULL,
    original_text VARCHAR(1000) NULL,
    recommended_text VARCHAR(1000) NULL,
    meaning_zh VARCHAR(500) NULL,
    explanation TEXT NULL,
    value_reason_for_user TEXT NULL,
    how_to_reuse TEXT NULL,
    review_prompt TEXT NULL,
    source_question VARCHAR(500) NULL,
    source_excerpt TEXT NULL,
    confidence DECIMAL(6,4) NULL,
    learning_value_score DECIMAL(6,4) NULL,
    promotion_status VARCHAR(32) NOT NULL DEFAULT 'preview' COMMENT 'preview | promoted | ignored',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_learning_asset_preview_item_uid (item_uid),
    KEY idx_writing_learning_asset_preview_item_run (run_uid),
    KEY idx_writing_learning_asset_preview_item_doc_type (document_id, asset_type),
    KEY idx_writing_learning_asset_preview_item_user_status (user_id, promotion_status),
    CONSTRAINT fk_writing_learning_asset_preview_item_doc
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_writing_learning_asset_preview_item_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='writing asset DeepSeek learning preview items';
