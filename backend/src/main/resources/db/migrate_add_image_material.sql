-- Add image_url and material_text columns to essay_prompt table
ALTER TABLE essay_prompt
    ADD COLUMN image_url VARCHAR(500) NULL COMMENT 'image URL for picture-based prompts' AFTER exam_year,
    ADD COLUMN material_text TEXT NULL COMMENT 'supplementary material text for material-based prompts' AFTER image_url;
