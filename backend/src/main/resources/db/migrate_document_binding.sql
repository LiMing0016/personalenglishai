-- Migration: document-binding
-- Adds document-essay binding support for user profiling

-- 1. documents 表新增字段
ALTER TABLE documents
    ADD COLUMN task_prompt TEXT NULL COMMENT 'essay topic / writing prompt' AFTER title,
    ADD COLUMN task_prompt_hash VARCHAR(64) NULL COMMENT 'SHA-256 of task_prompt for dedup' AFTER task_prompt,
    ADD COLUMN initial_score INT NULL COMMENT 'first evaluation score' AFTER task_prompt_hash,
    ADD COLUMN latest_score INT NULL COMMENT 'most recent evaluation score' AFTER initial_score,
    ADD COLUMN submit_count INT NOT NULL DEFAULT 0 COMMENT 'number of evaluation submissions' AFTER latest_score,
    ADD INDEX idx_owner_prompt_hash (owner_user_id, task_prompt_hash),
    ADD UNIQUE KEY uk_owner_prompt_hash (owner_user_id, task_prompt_hash, tenant_id, workspace_id);

-- 2. essay_evaluation 表新增字段
ALTER TABLE essay_evaluation
    ADD COLUMN document_id BIGINT NULL COMMENT 'documents.id' AFTER user_id,
    ADD COLUMN task_prompt TEXT NULL COMMENT 'essay topic (denormalized)' AFTER mode,
    ADD COLUMN content_quality INT NULL COMMENT 'dimension: content quality' AFTER overall_score,
    ADD COLUMN task_achievement INT NULL COMMENT 'dimension: task achievement' AFTER content_quality,
    ADD COLUMN structure_score INT NULL COMMENT 'dimension: structure' AFTER task_achievement,
    ADD COLUMN vocabulary_score INT NULL COMMENT 'dimension: vocabulary' AFTER structure_score,
    ADD COLUMN grammar_score INT NULL COMMENT 'dimension: grammar' AFTER vocabulary_score,
    ADD COLUMN expression_score INT NULL COMMENT 'dimension: expression' AFTER grammar_score,
    ADD COLUMN grammar_error_count INT NULL COMMENT 'grammar error count' AFTER expression_score,
    ADD COLUMN spelling_error_count INT NULL COMMENT 'spelling error count' AFTER grammar_error_count,
    ADD COLUMN vocabulary_error_count INT NULL COMMENT 'vocabulary error count' AFTER spelling_error_count,
    ADD INDEX idx_document_created (document_id, created_at DESC);
