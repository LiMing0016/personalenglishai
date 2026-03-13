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
    study_stage VARCHAR(50) NULL COMMENT 'effective study stage used for scoring',
    rubric_key VARCHAR(64) NULL COMMENT 'rubric version key',
    exam_policy_key VARCHAR(64) NULL COMMENT 'exam policy version key',
    model_version VARCHAR(64) NULL COMMENT 'scoring model version',
    evaluated_revision INT NULL COMMENT 'document revision evaluated by this score',
    exam_band_label VARCHAR(20) NULL COMMENT '100-point band label',
    exam_band_min INT NULL COMMENT '100-point band minimum score',
    exam_band_max INT NULL COMMENT '100-point band maximum score',
    direction_relevance VARCHAR(32) NULL COMMENT 'direction assessment: relevance',
    direction_task_completion VARCHAR(32) NULL COMMENT 'direction assessment: task completion',
    direction_coverage VARCHAR(32) NULL COMMENT 'direction assessment: coverage',
    direction_max_band VARCHAR(20) NULL COMMENT 'direction assessment max allowed band',
    cap_score INT NULL COMMENT 'hard constraint cap score',
    deduction_total INT NULL COMMENT 'hard constraint deduction total',
    penalty_flags_json JSON NULL COMMENT 'machine-readable penalty flags',
    direction_reasons_json JSON NULL COMMENT 'direction assessment reasons',
    adjustment_reasons_json JSON NULL COMMENT 'hard constraint adjustment reasons',
    word_count INT NULL COMMENT 'essay word count',
    sentence_count INT NULL COMMENT 'essay sentence count',
    paragraph_count INT NULL COMMENT 'essay paragraph count',
    total_error_count INT NULL COMMENT 'total error count',
    major_error_count INT NULL COMMENT 'major error count',
    minor_error_count INT NULL COMMENT 'minor error count',
    content_quality INT NULL COMMENT 'dimension: content quality',
    task_achievement INT NULL COMMENT 'dimension: task achievement',
    structure_score INT NULL COMMENT 'dimension: structure',
    vocabulary_score INT NULL COMMENT 'dimension: vocabulary',
    grammar_score INT NULL COMMENT 'dimension: grammar',
    expression_score INT NULL COMMENT 'dimension: expression',
    grammar_error_count INT NULL COMMENT 'grammar error count',
    spelling_error_count INT NULL COMMENT 'spelling error count',
    vocabulary_error_count INT NULL COMMENT 'vocabulary error count',
    lexical_error_count INT NULL COMMENT 'lexical error count',
    punctuation_error_count INT NULL COMMENT 'punctuation error count',
    syntax_error_count INT NULL COMMENT 'syntax error count',
    result_json LONGTEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at DESC),
    INDEX idx_document_created (document_id, created_at DESC),
    INDEX idx_stage_mode_created (study_stage, mode, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay evaluation history';

-- essay evaluation dimensions (one row per dimension per evaluation)
CREATE TABLE IF NOT EXISTS essay_evaluation_dimension (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    evaluation_id BIGINT NOT NULL COMMENT 'essay_evaluation.id',
    dimension_key VARCHAR(64) NOT NULL COMMENT 'stable dimension key',
    dimension_label_snapshot VARCHAR(64) NOT NULL COMMENT 'dimension label at evaluation time',
    sort_order INT NOT NULL DEFAULT 0 COMMENT 'display order snapshot',
    score INT NULL COMMENT 'dimension score',
    grade VARCHAR(16) NULL COMMENT 'dimension grade / level',
    strength TEXT NULL COMMENT 'dimension strength feedback',
    weakness TEXT NULL COMMENT 'dimension weakness feedback',
    suggestion TEXT NULL COMMENT 'dimension improvement suggestion',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_dimension_order (evaluation_id, sort_order),
    INDEX idx_dimension_key_created (dimension_key, created_at),
    CONSTRAINT fk_essay_eval_dimension_eval
        FOREIGN KEY (evaluation_id) REFERENCES essay_evaluation(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay evaluation dimension details';

-- per-document score summary for fast reads
CREATE TABLE IF NOT EXISTS document_score_summary (
    document_id BIGINT PRIMARY KEY COMMENT 'documents.id',
    user_id BIGINT NOT NULL COMMENT 'users.id',
    first_evaluation_id BIGINT NULL COMMENT 'first essay_evaluation.id',
    latest_evaluation_id BIGINT NULL COMMENT 'latest essay_evaluation.id',
    best_evaluation_id BIGINT NULL COMMENT 'best essay_evaluation.id',
    first_overall_score INT NULL COMMENT 'first overall score',
    latest_overall_score INT NULL COMMENT 'latest overall score',
    best_overall_score INT NULL COMMENT 'best overall score',
    latest_band_label VARCHAR(20) NULL COMMENT 'latest 100-point band label',
    latest_word_count INT NULL COMMENT 'latest essay word count',
    latest_total_error_count INT NULL COMMENT 'latest total error count',
    latest_major_error_count INT NULL COMMENT 'latest major error count',
    latest_minor_error_count INT NULL COMMENT 'latest minor error count',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_score_summary_user (user_id, updated_at),
    CONSTRAINT fk_doc_score_summary_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_doc_score_summary_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='document score summary';

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
    min_word_count INT NOT NULL DEFAULT 60 COMMENT 'minimum word count for submission',
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
    image_description TEXT NULL COMMENT 'description of the image for AI context',
    material_text TEXT NULL COMMENT 'supplementary material text for material-based prompts',
    task VARCHAR(16) NULL COMMENT 'task identifier, e.g. task1, task2',
    word_count_min INT NULL COMMENT 'minimum word count requirement',
    word_count_max INT NULL COMMENT 'maximum word count requirement',
    max_score INT NULL COMMENT 'maximum score for this prompt',
    source VARCHAR(255) NULL COMMENT 'source file or origin',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stage_paper_task (stage_id, paper, task),
    INDEX idx_stage_active (stage_id, is_active),
    INDEX idx_stage_year (stage_id, exam_year),
    INDEX idx_stage_title (stage_id, title),
    CONSTRAINT fk_essay_prompt_stage
        FOREIGN KEY (stage_id) REFERENCES writing_stage(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='essay prompt library';

-- writing metadata (shared context for free/exam documents)
CREATE TABLE IF NOT EXISTS writing_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL COMMENT 'documents.id',
    user_id BIGINT NOT NULL COMMENT 'users.id',
    mode VARCHAR(10) NOT NULL DEFAULT 'free' COMMENT 'free | exam',
    study_stage VARCHAR(50) NULL COMMENT 'study stage code, e.g. highschool/postgrad',
    title_snapshot VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'title snapshot when metadata is created',
    topic_title VARCHAR(255) NULL COMMENT 'short topic title for display/search',
    prompt_text TEXT NULL COMMENT 'structured prompt text snapshot',
    genre VARCHAR(64) NULL COMMENT 'genre, e.g. 议论文/书信/task2',
    source_type VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual | past_prompt | ai_generated | free_input',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_metadata_document (document_id),
    INDEX idx_writing_metadata_user_mode (user_id, mode),
    INDEX idx_writing_metadata_stage (study_stage),
    CONSTRAINT fk_writing_metadata_document
        FOREIGN KEY (document_id) REFERENCES documents(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT,
    CONSTRAINT fk_writing_metadata_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='shared writing metadata for document context';

-- writing exam metadata (exam-only scoring constraints)
CREATE TABLE IF NOT EXISTS writing_exam_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metadata_id BIGINT NOT NULL COMMENT 'writing_metadata.id',
    exam_type VARCHAR(32) NULL COMMENT 'exam type, e.g. gaokao/postgrad/cet4',
    task_type VARCHAR(32) NULL COMMENT 'task type, e.g. task1/task2/application',
    min_words INT NULL COMMENT 'minimum required words for exam scoring',
    recommended_max_words INT NULL COMMENT 'recommended max words before overlength penalty',
    max_score INT NULL COMMENT 'max raw score for the exam task',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_exam_metadata_metadata (metadata_id),
    INDEX idx_writing_exam_metadata_exam_type (exam_type),
    INDEX idx_writing_exam_metadata_task_type (task_type),
    CONSTRAINT fk_writing_exam_metadata_metadata
        FOREIGN KEY (metadata_id) REFERENCES writing_metadata(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='exam-only writing metadata';


