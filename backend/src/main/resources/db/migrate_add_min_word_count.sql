-- Add min_word_count column to writing_stage table
ALTER TABLE writing_stage
    ADD COLUMN min_word_count INT NOT NULL DEFAULT 60 COMMENT 'minimum word count for submission'
    AFTER name;

-- Initialize all stages to 60 (adjust per stage later)
UPDATE writing_stage SET min_word_count = 60;
