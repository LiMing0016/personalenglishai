-- users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NULL COMMENT 'email',
    email_verified TINYINT NOT NULL DEFAULT 0 COMMENT '0=unverified,1=verified',
    phone VARCHAR(20) NULL COMMENT 'phone',
    phone_verified TINYINT NOT NULL DEFAULT 0 COMMENT '0=unverified,1=verified',
    password_hash VARCHAR(255) NULL COMMENT 'bcrypt hash',
    nickname VARCHAR(50) NOT NULL DEFAULT '' COMMENT 'display name',
    avatar_url VARCHAR(255) NULL COMMENT 'avatar',
    role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'user/admin',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    register_source VARCHAR(20) NOT NULL DEFAULT 'email' COMMENT 'email/phone/oauth',
    token_version INT NOT NULL DEFAULT 0 COMMENT 'jwt token version',
    last_active_at DATETIME NULL COMMENT 'last active time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created at',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated at',
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_phone (phone),
    INDEX idx_email_verified (email_verified),
    INDEX idx_phone_verified (phone_verified)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='users';

-- user profile (1:1)
CREATE TABLE IF NOT EXISTS user_profile (
    user_id BIGINT PRIMARY KEY COMMENT 'users.id',
    study_stage VARCHAR(50) NULL,
    ai_mode INT NOT NULL DEFAULT 0 COMMENT '0=normal,1=stage-mode',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='user profile';

-- documents main table
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT '',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=draft,1=active,2=archived',
    latest_revision INT NOT NULL DEFAULT 1,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_tenant_workspace_public (tenant_id, workspace_id, public_id),
    INDEX idx_tenant_workspace_owner (tenant_id, workspace_id, owner_user_id),
    INDEX idx_tenant_workspace_updated (tenant_id, workspace_id, updated_at),
    INDEX idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='documents';

-- document revisions
CREATE TABLE IF NOT EXISTS document_revisions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    revision INT NOT NULL,
    content LONGTEXT NOT NULL,
    content_hash CHAR(64) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_doc_revision (document_id, revision),
    INDEX idx_document_created (document_id, created_at),
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='document revisions';

-- document pins
CREATE TABLE IF NOT EXISTS document_pins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    pin_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    payload JSON NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_doc_pin (document_id, pin_id),
    INDEX idx_document_id (document_id),
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='document pins';

-- user writing ability profile
CREATE TABLE IF NOT EXISTS user_ability_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT 'users.id',
    stage INT NOT NULL DEFAULT 1 COMMENT '1=highschool,2=CET4,3=CET6,4=postgrad',
    task_score DECIMAL(5,2) NULL,
    coherence_score DECIMAL(5,2) NULL,
    grammar_score DECIMAL(5,2) NULL,
    vocabulary_score DECIMAL(5,2) NULL,
    structure_score DECIMAL(5,2) NULL,
    variety_score DECIMAL(5,2) NULL,
    assessed_score DECIMAL(5,2) NULL,
    confidence DECIMAL(4,3) NULL,
    sample_count INT NOT NULL DEFAULT 0,
    model_version VARCHAR(32) NULL,
    rubric_version VARCHAR(64) NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='user ability profile';

-- essay evaluation history
CREATE TABLE IF NOT EXISTS essay_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id',
    mode VARCHAR(10) NOT NULL DEFAULT 'free' COMMENT 'free | exam',
    essay_text MEDIUMTEXT NOT NULL,
    gaokao_score INT NULL,
    max_score INT NULL,
    band VARCHAR(20) NULL,
    overall_score INT NULL,
    result_json LONGTEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay evaluation history';

-- async evaluation tasks
CREATE TABLE IF NOT EXISTS evaluate_task (
    request_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NULL COMMENT 'users.id',
    status VARCHAR(20) NOT NULL DEFAULT 'processing' COMMENT 'processing | succeeded | failed',
    error VARCHAR(500) NULL,
    result_json LONGTEXT NULL,
    submitted_at BIGINT NOT NULL COMMENT 'epoch ms',
    completed_at BIGINT NULL COMMENT 'epoch ms',
    INDEX idx_status (status),
    INDEX idx_submitted (submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='evaluate task';