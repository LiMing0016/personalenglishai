-- Migration: add handwriting import fields to writing_metadata

ALTER TABLE writing_metadata
    ADD COLUMN handwritten_source_type VARCHAR(16) NULL COMMENT 'uploaded handwriting source type' AFTER source_type,
    ADD COLUMN handwritten_source_image_url LONGTEXT NULL COMMENT 'uploaded handwriting image url snapshot' AFTER handwritten_source_type,
    ADD COLUMN handwritten_recognized_text LONGTEXT NULL COMMENT 'recognized handwriting text snapshot' AFTER handwritten_source_image_url,
    ADD COLUMN handwritten_imported_at DATETIME NULL COMMENT 'when handwriting import was bound' AFTER handwritten_recognized_text;
