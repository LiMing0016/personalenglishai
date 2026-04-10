-- Migration: add attachment image snapshot to writing metadata

ALTER TABLE writing_metadata
    ADD COLUMN attachment_image_url LONGTEXT NULL COMMENT 'generated or uploaded visual attachment url snapshot'
    AFTER prompt_text;
