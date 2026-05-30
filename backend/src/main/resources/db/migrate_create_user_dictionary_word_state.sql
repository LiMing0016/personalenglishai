CREATE TABLE IF NOT EXISTS user_dictionary_word_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT 'users.id',
    word VARCHAR(255) NOT NULL,
    normalized_word VARCHAR(255) NOT NULL,
    language VARCHAR(32) NULL,
    source VARCHAR(32) NULL COMMENT 'local | oxford | manual',
    favorite TINYINT(1) NOT NULL DEFAULT 0,
    lookup_count INT NOT NULL DEFAULT 0,
    first_lookup_at DATETIME NULL,
    last_lookup_at DATETIME NULL,
    favorited_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_dictionary_word (user_id, normalized_word),
    KEY idx_user_dictionary_favorite (user_id, favorite, favorited_at),
    KEY idx_user_dictionary_lookup (user_id, last_lookup_at),
    CONSTRAINT fk_user_dictionary_word_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='per-user dictionary lookup and favorite state';
