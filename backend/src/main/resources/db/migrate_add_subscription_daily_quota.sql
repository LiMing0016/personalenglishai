-- Migration: Free daily token quota and daily AI token usage aggregate

ALTER TABLE subscription_plan
    ADD COLUMN daily_token_limit BIGINT NULL COMMENT 'Free daily token quota; paid plans use monthly_token_limit' AFTER monthly_token_limit;

UPDATE subscription_plan
SET daily_token_limit = 10000
WHERE plan_code = 'free'
  AND daily_token_limit IS NULL;

CREATE TABLE IF NOT EXISTS user_ai_token_usage_daily (
    user_id BIGINT NOT NULL,
    usage_date DATE NOT NULL COMMENT 'natural date by server timezone',
    token_used BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_user_ai_token_usage_daily_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='daily AI token usage aggregate';

