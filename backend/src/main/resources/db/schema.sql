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
    task_prompt TEXT NULL COMMENT 'essay topic / writing prompt',
    task_prompt_hash VARCHAR(64) NULL COMMENT 'SHA-256 of task_prompt for dedup',
    initial_score INT NULL COMMENT 'first evaluation score',
    latest_score INT NULL COMMENT 'most recent evaluation score',
    submit_count INT NOT NULL DEFAULT 0 COMMENT 'number of evaluation submissions',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=draft,1=active,2=archived',
    latest_revision INT NOT NULL DEFAULT 1,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_tenant_workspace_public (tenant_id, workspace_id, public_id),
    INDEX idx_tenant_workspace_owner (tenant_id, workspace_id, owner_user_id),
    INDEX idx_tenant_workspace_updated (tenant_id, workspace_id, updated_at),
    INDEX idx_owner_prompt_hash (owner_user_id, task_prompt_hash),
    INDEX idx_deleted_at (deleted_at),
    UNIQUE KEY uk_owner_prompt_hash (owner_user_id, task_prompt_hash, tenant_id, workspace_id)
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
    document_id BIGINT NULL COMMENT 'documents.id',
    mode VARCHAR(10) NOT NULL DEFAULT 'free' COMMENT 'free | exam',
    task_prompt TEXT NULL COMMENT 'essay topic (denormalized)',
    essay_text MEDIUMTEXT NOT NULL,
    gaokao_score INT NULL,
    max_score INT NULL,
    band VARCHAR(20) NULL,
    overall_score INT NULL,
    content_quality INT NULL COMMENT 'dimension: content quality',
    task_achievement INT NULL COMMENT 'dimension: task achievement',
    structure_score INT NULL COMMENT 'dimension: structure',
    vocabulary_score INT NULL COMMENT 'dimension: vocabulary',
    grammar_score INT NULL COMMENT 'dimension: grammar',
    expression_score INT NULL COMMENT 'dimension: expression',
    grammar_error_count INT NULL COMMENT 'grammar error count',
    spelling_error_count INT NULL COMMENT 'spelling error count',
    vocabulary_error_count INT NULL COMMENT 'vocabulary error count',
    result_json LONGTEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_document_created (document_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay evaluation history';

-- essay favorites
CREATE TABLE IF NOT EXISTS essay_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'users.id',
    evaluation_id BIGINT NOT NULL COMMENT 'essay_evaluation.id',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_eval (user_id, evaluation_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay favorites';

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
-- writing stages
CREATE TABLE IF NOT EXISTS writing_stage (
    id INT PRIMARY KEY COMMENT 'align with app stage ids when possible',
    code VARCHAR(32) NOT NULL COMMENT 'stable business code',
    name VARCHAR(64) NOT NULL COMMENT 'display name',
    sort_order INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_stage_code (code),
    INDEX idx_writing_stage_active_sort (is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='writing stages';

-- essay prompt library
CREATE TABLE IF NOT EXISTS essay_prompt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stage_id INT NOT NULL COMMENT 'writing_stage.id',
    paper VARCHAR(64) NOT NULL COMMENT 'stable paper code, e.g. 2025-06-set-1',
    title VARCHAR(255) NOT NULL COMMENT 'display title',
    prompt_text TEXT NOT NULL COMMENT 'essay prompt content',
    exam_year INT NULL COMMENT 'exam year, e.g. 2025',
    image_url VARCHAR(500) NULL COMMENT 'image URL for picture-based prompts',
    material_text TEXT NULL COMMENT 'supplementary material text for material-based prompts',
    source VARCHAR(255) NULL COMMENT 'source file or origin',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stage_paper (stage_id, paper),
    INDEX idx_stage_active (stage_id, is_active),
    INDEX idx_stage_year (stage_id, exam_year),
    INDEX idx_stage_title (stage_id, title),
    CONSTRAINT fk_essay_prompt_stage
        FOREIGN KEY (stage_id) REFERENCES writing_stage(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay prompt library';
