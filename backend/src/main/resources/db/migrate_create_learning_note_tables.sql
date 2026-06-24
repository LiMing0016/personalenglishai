CREATE TABLE IF NOT EXISTS learning_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_uid VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content_markdown MEDIUMTEXT NOT NULL,
    structured_payload JSON NULL,
    source_conversation_uid VARCHAR(64) NULL,
    source_message_uid VARCHAR(64) NULL,
    source_text TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    UNIQUE KEY uk_learning_note_uid (note_uid),
    KEY idx_learning_note_user_type (user_id, type, deleted_at, updated_at),
    KEY idx_learning_note_source_conversation (source_conversation_uid)
);
