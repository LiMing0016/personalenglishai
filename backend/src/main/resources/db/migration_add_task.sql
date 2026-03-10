-- Add task column and image_description column to essay_prompt table
ALTER TABLE essay_prompt ADD COLUMN image_description TEXT NULL COMMENT 'description of the image for AI context' AFTER image_url;
ALTER TABLE essay_prompt ADD COLUMN task VARCHAR(16) NULL COMMENT 'task identifier, e.g. task1, task2' AFTER material_text;

-- Drop old unique index and create new one
ALTER TABLE essay_prompt DROP INDEX uk_stage_paper;
ALTER TABLE essay_prompt ADD UNIQUE KEY uk_stage_paper_task (stage_id, paper, task);

-- Add word count range and max score columns
ALTER TABLE essay_prompt ADD COLUMN word_count_min INT NULL COMMENT 'minimum word count requirement' AFTER task;
ALTER TABLE essay_prompt ADD COLUMN word_count_max INT NULL COMMENT 'maximum word count requirement' AFTER word_count_min;
ALTER TABLE essay_prompt ADD COLUMN max_score INT NULL COMMENT 'maximum score for this prompt' AFTER word_count_max;
