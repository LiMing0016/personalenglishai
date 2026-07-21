CREATE TABLE IF NOT EXISTS vocabulary_product_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_uid VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128) NULL,
    session_id VARCHAR(128) NOT NULL,
    card_uid VARCHAR(64) NULL,
    properties_json JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_vocabulary_product_event_user_uid (user_id, event_uid),
    KEY idx_vocabulary_product_event_name_time (event_name, occurred_at),
    KEY idx_vocabulary_product_event_trace_time (trace_id, occurred_at),
    KEY idx_vocabulary_product_event_card_time (card_uid, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
