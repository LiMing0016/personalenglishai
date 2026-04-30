-- assistant projects
CREATE TABLE IF NOT EXISTS assistant_project (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id',
    name VARCHAR(120) NOT NULL COMMENT 'project display name',
    description VARCHAR(500) NULL COMMENT 'project description',
    archived_at DATETIME NULL COMMENT 'archive time',
    deleted_at DATETIME NULL COMMENT 'soft delete time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
    INDEX idx_assistant_project_user_active (user_id, deleted_at, updated_at),
    CONSTRAINT fk_assistant_project_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='assistant projects';

-- assistant conversations
CREATE TABLE IF NOT EXISTS assistant_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_uid VARCHAR(64) NOT NULL COMMENT 'stable public conversation id',
    user_id BIGINT NOT NULL COMMENT 'users.id',
    project_id BIGINT NULL COMMENT 'assistant_project.id',
    title VARCHAR(160) NOT NULL DEFAULT '新对话' COMMENT 'conversation title',
    summary VARCHAR(500) NULL COMMENT 'conversation summary',
    pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=normal,1=pinned',
    archived_at DATETIME NULL COMMENT 'archive time',
    deleted_at DATETIME NULL COMMENT 'soft delete time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
    UNIQUE KEY uk_assistant_conversation_uid (conversation_uid),
    INDEX idx_assistant_conversation_user_visible (user_id, deleted_at, archived_at, pinned, updated_at),
    INDEX idx_assistant_conversation_project (project_id),
    CONSTRAINT fk_assistant_conversation_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_assistant_conversation_project
        FOREIGN KEY (project_id) REFERENCES assistant_project(id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='assistant conversations';

-- assistant messages
CREATE TABLE IF NOT EXISTS assistant_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_uid VARCHAR(64) NOT NULL COMMENT 'stable public message id',
    conversation_uid VARCHAR(64) NOT NULL COMMENT 'assistant_conversation.conversation_uid',
    user_id BIGINT NOT NULL COMMENT 'users.id',
    role VARCHAR(16) NOT NULL COMMENT 'user | assistant',
    content MEDIUMTEXT NOT NULL COMMENT 'message content',
    status VARCHAR(16) NOT NULL DEFAULT 'done' COMMENT 'done | failed',
    sort_order INT NOT NULL COMMENT 'message order in conversation',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    UNIQUE KEY uk_assistant_message_uid (message_uid),
    INDEX idx_assistant_message_conversation_order (conversation_uid, sort_order),
    CONSTRAINT fk_assistant_message_conversation
        FOREIGN KEY (conversation_uid) REFERENCES assistant_conversation(conversation_uid)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_assistant_message_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='assistant messages';

-- assistant public share snapshots
CREATE TABLE IF NOT EXISTS assistant_share (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_token VARCHAR(96) NOT NULL COMMENT 'unguessable share token',
    conversation_uid VARCHAR(64) NOT NULL COMMENT 'assistant_conversation.conversation_uid',
    owner_user_id BIGINT NOT NULL COMMENT 'users.id',
    title_snapshot VARCHAR(160) NOT NULL COMMENT 'title at share creation',
    messages_snapshot JSON NOT NULL COMMENT 'messages at share creation',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    revoked_at DATETIME NULL COMMENT 'revoke time',
    UNIQUE KEY uk_assistant_share_token (share_token),
    INDEX idx_assistant_share_conversation_active (conversation_uid, revoked_at),
    CONSTRAINT fk_assistant_share_conversation
        FOREIGN KEY (conversation_uid) REFERENCES assistant_conversation(conversation_uid)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_assistant_share_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='assistant public share snapshots';
