-- Migration: expand attachment image snapshot column to support base64 data URLs

ALTER TABLE writing_metadata
    MODIFY COLUMN attachment_image_url LONGTEXT NULL COMMENT 'generated or uploaded visual attachment url snapshot';
