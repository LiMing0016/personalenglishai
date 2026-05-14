-- Migration: subscription plans and monthly AI token quota usage

CREATE TABLE IF NOT EXISTS subscription_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL COMMENT 'free | basic | pro | premium',
    name VARCHAR(64) NOT NULL,
    monthly_token_limit BIGINT NOT NULL,
    daily_token_limit BIGINT NULL COMMENT 'Free daily token quota; paid plans use monthly_token_limit',
    sort_order INT NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_subscription_plan_code (plan_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='subscription plan definitions';

INSERT INTO subscription_plan (plan_code, name, monthly_token_limit, daily_token_limit, sort_order, active)
VALUES
    ('free', 'Free', 100000, 10000, 0, 1),
    ('basic', 'Basic', 1000000, NULL, 1, 1),
    ('pro', 'Pro', 5000000, NULL, 2, 1),
    ('premium', 'Premium', 20000000, NULL, 3, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    monthly_token_limit = VALUES(monthly_token_limit),
    daily_token_limit = VALUES(daily_token_limit),
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

CREATE TABLE IF NOT EXISTS subscription_redeem_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_hash CHAR(64) NOT NULL,
    plan_code VARCHAR(32) NOT NULL,
    duration_days INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'unused',
    expires_at DATETIME NULL,
    batch_name VARCHAR(128) NULL,
    created_by_user_id BIGINT NULL,
    redeemed_by_user_id BIGINT NULL,
    redeemed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_subscription_redeem_code_hash (code_hash),
    INDEX idx_subscription_redeem_code_status (status),
    INDEX idx_subscription_redeem_code_batch (batch_name),
    INDEX idx_subscription_redeem_code_redeemed_by (redeemed_by_user_id),
    CONSTRAINT fk_subscription_redeem_code_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users(id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT,
    CONSTRAINT fk_subscription_redeem_code_redeemed_by
        FOREIGN KEY (redeemed_by_user_id) REFERENCES users(id)
        ON DELETE SET NULL
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='subscription redeem codes';

CREATE TABLE IF NOT EXISTS subscription_redeem_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    redeem_code_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    plan_code VARCHAR(32) NOT NULL,
    duration_days INT NOT NULL,
    before_plan_code VARCHAR(32) NULL,
    before_period_end DATETIME NULL,
    after_plan_code VARCHAR(32) NOT NULL,
    after_period_end DATETIME NULL,
    redeem_ip VARCHAR(64) NULL,
    redeemed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subscription_redeem_event_user_time (user_id, redeemed_at),
    INDEX idx_subscription_redeem_event_code (redeem_code_id),
    CONSTRAINT fk_subscription_redeem_event_code
        FOREIGN KEY (redeem_code_id) REFERENCES subscription_redeem_code(id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,
    CONSTRAINT fk_subscription_redeem_event_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='subscription redeem audit events';
