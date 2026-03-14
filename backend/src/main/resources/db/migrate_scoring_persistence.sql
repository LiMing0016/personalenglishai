-- Migration: scoring-persistence
-- Extends essay_evaluation and adds dimension/detail summary tables.

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

ALTER TABLE essay_evaluation
    ADD COLUMN study_stage VARCHAR(50) NULL COMMENT 'effective study stage used for scoring' AFTER overall_score,
    ADD COLUMN rubric_key VARCHAR(64) NULL COMMENT 'rubric version key' AFTER study_stage,
    ADD COLUMN exam_policy_key VARCHAR(64) NULL COMMENT 'exam policy version key' AFTER rubric_key,
    ADD COLUMN model_version VARCHAR(64) NULL COMMENT 'scoring model version' AFTER exam_policy_key,
    ADD COLUMN evaluated_revision INT NULL COMMENT 'document revision evaluated by this score' AFTER model_version,
    ADD COLUMN exam_band_label VARCHAR(20) NULL COMMENT '100-point band label' AFTER evaluated_revision,
    ADD COLUMN exam_band_min INT NULL COMMENT '100-point band minimum score' AFTER exam_band_label,
    ADD COLUMN exam_band_max INT NULL COMMENT '100-point band maximum score' AFTER exam_band_min,
    ADD COLUMN direction_relevance VARCHAR(32) NULL COMMENT 'direction assessment: relevance' AFTER exam_band_max,
    ADD COLUMN direction_task_completion VARCHAR(32) NULL COMMENT 'direction assessment: task completion' AFTER direction_relevance,
    ADD COLUMN direction_coverage VARCHAR(32) NULL COMMENT 'direction assessment: coverage' AFTER direction_task_completion,
    ADD COLUMN direction_max_band VARCHAR(20) NULL COMMENT 'direction assessment max allowed band' AFTER direction_coverage,
    ADD COLUMN cap_score INT NULL COMMENT 'hard constraint cap score' AFTER direction_max_band,
    ADD COLUMN deduction_total INT NULL COMMENT 'hard constraint deduction total' AFTER cap_score,
    ADD COLUMN penalty_flags_json JSON NULL COMMENT 'machine-readable penalty flags' AFTER deduction_total,
    ADD COLUMN direction_reasons_json JSON NULL COMMENT 'direction assessment reasons' AFTER penalty_flags_json,
    ADD COLUMN adjustment_reasons_json JSON NULL COMMENT 'hard constraint adjustment reasons' AFTER direction_reasons_json,
    ADD COLUMN word_count INT NULL COMMENT 'essay word count' AFTER adjustment_reasons_json,
    ADD COLUMN sentence_count INT NULL COMMENT 'essay sentence count' AFTER word_count,
    ADD COLUMN paragraph_count INT NULL COMMENT 'essay paragraph count' AFTER sentence_count,
    ADD COLUMN total_error_count INT NULL COMMENT 'total error count' AFTER paragraph_count,
    ADD COLUMN major_error_count INT NULL COMMENT 'major error count' AFTER total_error_count,
    ADD COLUMN minor_error_count INT NULL COMMENT 'minor error count' AFTER major_error_count,
    ADD COLUMN lexical_error_count INT NULL COMMENT 'lexical error count' AFTER vocabulary_error_count,
    ADD COLUMN punctuation_error_count INT NULL COMMENT 'punctuation error count' AFTER lexical_error_count,
    ADD COLUMN syntax_error_count INT NULL COMMENT 'syntax error count' AFTER punctuation_error_count,
    ADD INDEX idx_stage_mode_created (study_stage, mode, created_at DESC);

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
