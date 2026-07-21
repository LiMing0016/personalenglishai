ALTER TABLE assistant_message
    ADD COLUMN parts_json JSON NULL COMMENT 'versioned assistant learning blocks' AFTER content;
