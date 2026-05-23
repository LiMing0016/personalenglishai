-- assistant archive settings
CREATE TABLE IF NOT EXISTS assistant_archive_setting (
    user_id BIGINT PRIMARY KEY COMMENT 'users.id',
    archive_dir VARCHAR(1000) NOT NULL COMMENT 'server-local archive directory',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
    CONSTRAINT fk_assistant_archive_setting_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='assistant archive directory settings';

-- assistant conversation archive records
CREATE TABLE IF NOT EXISTS assistant_conversation_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_uid VARCHAR(64) NOT NULL COMMENT 'stable public archive id',
    conversation_uid VARCHAR(64) NOT NULL COMMENT 'assistant_conversation.conversation_uid',
    user_id BIGINT NOT NULL COMMENT 'users.id',
    title VARCHAR(160) NOT NULL COMMENT 'conversation title at archive time',
    summary VARCHAR(500) NULL COMMENT 'conversation summary at archive time',
    message_count INT NOT NULL DEFAULT 0 COMMENT 'message count at archive time',
    archive_dir VARCHAR(1000) NOT NULL COMMENT 'archive folder path',
    markdown_path VARCHAR(1000) NOT NULL COMMENT 'conversation.md path',
    json_path VARCHAR(1000) NOT NULL COMMENT 'conversation.json path',
    metadata_path VARCHAR(1000) NOT NULL COMMENT 'metadata.json path',
    checksum VARCHAR(64) NOT NULL COMMENT 'sha256 of json snapshot',
    status VARCHAR(16) NOT NULL DEFAULT 'archived' COMMENT 'archived | restored | failed',
    error_message VARCHAR(1000) NULL COMMENT 'archive failure detail',
    archived_at DATETIME NOT NULL COMMENT 'archive time',
    restored_at DATETIME NULL COMMENT 'restore time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
    UNIQUE KEY uk_assistant_archive_uid (archive_uid),
    INDEX idx_assistant_archive_conversation (conversation_uid, status, archived_at),
    INDEX idx_assistant_archive_user (user_id, archived_at),
    CONSTRAINT fk_assistant_archive_conversation
        FOREIGN KEY (conversation_uid) REFERENCES assistant_conversation(conversation_uid)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_assistant_archive_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='assistant conversation archive records';
