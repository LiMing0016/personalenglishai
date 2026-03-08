-- Add exam_year column to essay_prompt table
ALTER TABLE essay_prompt
    ADD COLUMN exam_year INT NULL COMMENT 'exam year, e.g. 2025' AFTER prompt_text;

-- Backfill exam_year from paper column (extract first 4 digits)
UPDATE essay_prompt SET exam_year = CAST(LEFT(paper, 4) AS UNSIGNED) WHERE exam_year IS NULL;

-- Add index for year-based filtering
CREATE INDEX idx_stage_year ON essay_prompt (stage_id, exam_year);
