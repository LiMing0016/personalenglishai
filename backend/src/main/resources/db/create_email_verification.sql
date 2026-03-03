-- 邮箱验证 token 表
CREATE TABLE IF NOT EXISTS email_verification_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL COMMENT '关联 users.id',
    token      VARCHAR(128) NOT NULL UNIQUE COMMENT '验证 token（UUID）',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    used       TINYINT NOT NULL DEFAULT 0 COMMENT '0=未使用 1=已使用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='邮箱验证 token';

-- users 表增加 email_verified 字段（如果尚未添加）
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证 0=未验证 1=已验证' AFTER email;
