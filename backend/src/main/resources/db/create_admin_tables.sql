-- admin panel bootstrap tables
CREATE TABLE IF NOT EXISTS admin_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id',
    role VARCHAR(32) NOT NULL COMMENT 'super_admin/support_admin/content_admin',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_user_role (user_id, role),
    INDEX idx_admin_role (role),
    CONSTRAINT fk_admin_user_role_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='admin roles';

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT NOT NULL COMMENT 'admin users.id',
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NULL,
    target_user_id BIGINT NULL,
    before_json LONGTEXT NULL,
    after_json LONGTEXT NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_audit_admin_created (admin_user_id, created_at DESC),
    INDEX idx_admin_audit_action_created (action, created_at DESC),
    INDEX idx_admin_audit_target_created (target_user_id, created_at DESC),
    CONSTRAINT fk_admin_audit_admin_user FOREIGN KEY (admin_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='admin audit log';
