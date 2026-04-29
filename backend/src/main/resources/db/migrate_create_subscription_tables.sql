-- Migration: subscription plans and monthly AI token quota usage

CREATE TABLE IF NOT EXISTS subscription_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL COMMENT 'free | basic | pro | premium',
    name VARCHAR(64) NOT NULL,
    monthly_token_limit BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_subscription_plan_code (plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='subscription plan definitions';

INSERT INTO subscription_plan (plan_code, name, monthly_token_limit, sort_order, active)
VALUES
    ('free', 'Free', 100000, 0, 1),
    ('basic', 'Basic', 1000000, 1, 1),
    ('pro', 'Pro', 5000000, 2, 1),
    ('premium', 'Premium', 20000000, 3, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    monthly_token_limit = VALUES(monthly_token_limit),
    sort_order = VALUES(sort_order),
    active = VALUES(active),
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS user_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan_code VARCHAR(32) NOT NULL DEFAULT 'free',
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    current_period_start DATETIME NULL,
    current_period_end DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_subscription_user (user_id),
    INDEX idx_user_subscription_plan (plan_code),
    INDEX idx_user_subscription_period (current_period_end),
    CONSTRAINT fk_user_subscription_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='current user subscription';

CREATE TABLE IF NOT EXISTS ai_token_usage_event (
    usage_event_id VARCHAR(96) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    feature_key VARCHAR(96) NOT NULL,
    provider VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    cached_input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    reasoning_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    trace_id VARCHAR(96) NULL,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_token_usage_event_user_time (user_id, occurred_at),
    INDEX idx_ai_token_usage_event_feature (feature_key),
    INDEX idx_ai_token_usage_event_trace (trace_id),
    CONSTRAINT fk_ai_token_usage_event_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI token usage event ledger';

CREATE TABLE IF NOT EXISTS user_ai_token_usage_monthly (
    user_id BIGINT NOT NULL,
    usage_month CHAR(7) NOT NULL COMMENT 'YYYY-MM natural month',
    token_used BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, usage_month),
    CONSTRAINT fk_user_ai_token_usage_monthly_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='monthly AI token usage aggregate';
