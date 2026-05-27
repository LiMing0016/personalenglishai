CREATE TABLE IF NOT EXISTS writing_document_conversation_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id',
    document_id BIGINT NOT NULL COMMENT 'documents.id',
    conversation_uid VARCHAR(64) NOT NULL COMMENT 'assistant_conversation.conversation_uid',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_doc_conversation (document_id, conversation_uid),
    INDEX idx_writing_doc_conversation_user_doc (user_id, document_id),
    INDEX idx_writing_doc_conversation_uid (conversation_uid),
    CONSTRAINT fk_writing_doc_conversation_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_writing_doc_conversation_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_writing_doc_conversation_assistant
        FOREIGN KEY (conversation_uid) REFERENCES assistant_conversation(conversation_uid)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='writing document to coach conversation links';

CREATE TABLE IF NOT EXISTS writing_document_asset_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT 'documents.id',
    user_id BIGINT NOT NULL COMMENT 'users.id',
    snapshot_uid VARCHAR(64) NOT NULL COMMENT 'stable generated snapshot uid',
    markdown_content LONGTEXT NOT NULL COMMENT 'readable markdown archive',
    snapshot_json LONGTEXT NOT NULL COMMENT 'machine-readable archive snapshot',
    latest_revision INT NOT NULL DEFAULT 1,
    evaluation_count INT NOT NULL DEFAULT 0,
    coach_message_count INT NOT NULL DEFAULT 0,
    generated_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_asset_snapshot_document (document_id),
    UNIQUE KEY uk_writing_asset_snapshot_uid (snapshot_uid),
    INDEX idx_writing_asset_snapshot_user (user_id, updated_at),
    CONSTRAINT fk_writing_asset_snapshot_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_writing_asset_snapshot_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='writing document asset snapshots';
