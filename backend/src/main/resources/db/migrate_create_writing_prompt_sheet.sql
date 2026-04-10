-- Migration: create ai-generated writing prompt sheet library

CREATE TABLE IF NOT EXISTS writing_prompt_sheet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper VARCHAR(64) NOT NULL COMMENT 'stable internal identifier, e.g. ai-20260410-xxxxx',
    document_id BIGINT NULL COMMENT 'optional linked documents.id',
    study_stage VARCHAR(32) NULL COMMENT 'study stage code, e.g. highschool/postgrad',
    source_type VARCHAR(32) NOT NULL DEFAULT 'ai_generated' COMMENT 'manual | past_prompt | ai_generated | free_input',
    task_type VARCHAR(32) NULL COMMENT 'task identifier, e.g. task1/task2',
    prompt_type VARCHAR(32) NULL COMMENT 'prompt type, e.g. general/material/chart/comic',
    topic_title VARCHAR(255) NOT NULL COMMENT 'display topic title',
    directions TEXT NULL COMMENT 'directions header / instruction lead',
    prompt_text TEXT NOT NULL COMMENT 'main prompt text',
    requirements_text TEXT NULL COMMENT 'flattened writing requirements text',
    genre VARCHAR(64) NULL COMMENT 'genre, e.g. exposition/argumentation',
    word_count_min INT NULL COMMENT 'minimum word count',
    word_count_max INT NULL COMMENT 'recommended or maximum word count',
    attachment_type VARCHAR(16) NOT NULL DEFAULT 'none' COMMENT 'none | material | visual',
    attachment_payload_json JSON NULL COMMENT 'attachment payload: title/content/materialText/imageUrl',
    structured_payload_json JSON NULL COMMENT 'structured prompt payload: chartSpec/comicScenes/etc',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'draft | active | archived',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_writing_prompt_sheet_paper (paper),
    INDEX idx_writing_prompt_sheet_stage_status (study_stage, status),
    INDEX idx_writing_prompt_sheet_task_type (task_type),
    INDEX idx_writing_prompt_sheet_prompt_type (prompt_type),
    INDEX idx_writing_prompt_sheet_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI-generated writing prompt sheet library';

ALTER TABLE documents
    ADD COLUMN prompt_sheet_id BIGINT NULL COMMENT 'writing_prompt_sheet.id' AFTER task_prompt_hash,
    ADD INDEX idx_prompt_sheet_id (prompt_sheet_id);
