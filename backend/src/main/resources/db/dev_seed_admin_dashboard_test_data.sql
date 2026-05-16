-- Development-only seed data for admin subscription/user dashboard.
-- Safe to run repeatedly. It only touches fixed peai.test+...@local accounts.

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

INSERT INTO users (
    email,
    email_verified,
    phone,
    phone_verified,
    password_hash,
    nickname,
    avatar_url,
    role,
    status,
    register_source,
    token_version,
    last_active_at,
    created_at,
    updated_at
)
VALUES
    ('peai.test+admin@local', 1, NULL, 0, NULL, 'Dev Ops Admin', NULL, 'admin', 'active', 'email', 0, NOW(), DATE_SUB(NOW(), INTERVAL 15 DAY), NOW()),
    ('peai.test+free01@local', 1, NULL, 0, NULL, 'Free Today', NULL, 'user', 'active', 'email', 0, NOW(), NOW(), NOW()),
    ('peai.test+free02@local', 1, NULL, 0, NULL, 'Free Yesterday', NULL, 'user', 'active', 'email', 0, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY), NOW()),
    ('peai.test+free-overlimit@local', 1, NULL, 0, NULL, 'Free Over Limit', NULL, 'user', 'active', 'email', 0, NOW(), DATE_SUB(NOW(), INTERVAL 3 DAY), NOW()),
    ('peai.test+basic01@local', 1, NULL, 0, NULL, 'Basic Active Today', NULL, 'user', 'active', 'email', 0, NOW(), NOW(), NOW()),
    ('peai.test+basic02@local', 1, NULL, 0, NULL, 'Basic Active', NULL, 'user', 'active', 'email', 0, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 6 DAY), NOW()),
    ('peai.test+pro01@local', 1, NULL, 0, NULL, 'Pro Active', NULL, 'user', 'active', 'email', 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 10 DAY), NOW()),
    ('peai.test+premium01@local', 1, NULL, 0, NULL, 'Premium Active', NULL, 'user', 'active', 'email', 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 20 DAY), NOW()),
    ('peai.test+expired-pro@local', 1, NULL, 0, NULL, 'Expired Pro', NULL, 'user', 'active', 'email', 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY), NOW()),
    ('peai.test+canceled-basic@local', 1, NULL, 0, NULL, 'Canceled Basic', NULL, 'user', 'active', 'email', 0, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY), NOW()),
    ('peai.test+disabled@local', 1, NULL, 0, NULL, 'Disabled Learner', NULL, 'user', 'disabled', 'email', 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 45 DAY), NOW()),
    ('peai.test+new-postgrad@local', 1, NULL, 0, NULL, 'New Postgrad Learner', NULL, 'user', 'active', 'email', 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    email_verified = VALUES(email_verified),
    nickname = VALUES(nickname),
    role = VALUES(role),
    status = VALUES(status),
    register_source = VALUES(register_source),
    last_active_at = VALUES(last_active_at),
    created_at = VALUES(created_at),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
SELECT id, 'super_admin', NOW(), NOW()
FROM users
WHERE email = 'peai.test+admin@local'
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_profile (user_id, study_stage, ai_mode, updated_at)
SELECT u.id,
       CASE u.email
           WHEN 'peai.test+free01@local' THEN 'gaokao'
           WHEN 'peai.test+free02@local' THEN 'cet4'
           WHEN 'peai.test+free-overlimit@local' THEN 'cet6'
           WHEN 'peai.test+basic01@local' THEN 'postgrad'
           WHEN 'peai.test+basic02@local' THEN 'ielts'
           WHEN 'peai.test+pro01@local' THEN 'toefl'
           WHEN 'peai.test+premium01@local' THEN 'postgrad'
           WHEN 'peai.test+new-postgrad@local' THEN 'postgrad'
           ELSE 'general'
       END AS study_stage,
       0,
       NOW()
FROM users u
WHERE u.email LIKE 'peai.test+%@local'
ON DUPLICATE KEY UPDATE
    study_stage = VALUES(study_stage),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_subscription (
    user_id,
    plan_code,
    status,
    current_period_start,
    current_period_end,
    created_at,
    updated_at
)
SELECT u.id,
       seed.plan_code,
       seed.status,
       DATE_SUB(NOW(), INTERVAL seed.start_days_ago DAY),
       DATE_ADD(NOW(), INTERVAL seed.end_days_later DAY),
       DATE_SUB(NOW(), INTERVAL seed.start_days_ago DAY),
       NOW()
FROM users u
JOIN (
    SELECT 'peai.test+free01@local' AS email, 'free' AS plan_code, 'active' AS status, 0 AS start_days_ago, 30 AS end_days_later
    UNION ALL SELECT 'peai.test+free02@local', 'free', 'active', 1, 29
    UNION ALL SELECT 'peai.test+free-overlimit@local', 'free', 'active', 3, 27
    UNION ALL SELECT 'peai.test+basic01@local', 'basic', 'active', 0, 30
    UNION ALL SELECT 'peai.test+basic02@local', 'basic', 'active', 2, 28
    UNION ALL SELECT 'peai.test+pro01@local', 'pro', 'active', 5, 25
    UNION ALL SELECT 'peai.test+premium01@local', 'premium', 'active', 10, 20
    UNION ALL SELECT 'peai.test+expired-pro@local', 'pro', 'active', 45, -1
    UNION ALL SELECT 'peai.test+canceled-basic@local', 'basic', 'canceled', 20, 10
    UNION ALL SELECT 'peai.test+disabled@local', 'free', 'active', 20, 10
    UNION ALL SELECT 'peai.test+new-postgrad@local', 'free', 'active', 0, 30
) seed ON seed.email = u.email
ON DUPLICATE KEY UPDATE
    plan_code = VALUES(plan_code),
    status = VALUES(status),
    current_period_start = VALUES(current_period_start),
    current_period_end = VALUES(current_period_end),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_ai_token_usage_daily (user_id, usage_date, token_used, updated_at)
SELECT u.id, CURDATE(), seed.today_tokens, NOW()
FROM users u
JOIN (
    SELECT 'peai.test+free01@local' AS email, 2400 AS today_tokens
    UNION ALL SELECT 'peai.test+free02@local', 1200
    UNION ALL SELECT 'peai.test+free-overlimit@local', 12800
    UNION ALL SELECT 'peai.test+new-postgrad@local', 900
) seed ON seed.email = u.email
ON DUPLICATE KEY UPDATE
    token_used = VALUES(token_used),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_ai_token_usage_monthly (user_id, usage_month, token_used, updated_at)
SELECT u.id, DATE_FORMAT(CURDATE(), '%Y-%m'), seed.month_tokens, NOW()
FROM users u
JOIN (
    SELECT 'peai.test+basic01@local' AS email, 120000 AS month_tokens
    UNION ALL SELECT 'peai.test+basic02@local', 380000
    UNION ALL SELECT 'peai.test+pro01@local', 980000
    UNION ALL SELECT 'peai.test+premium01@local', 2600000
) seed ON seed.email = u.email
ON DUPLICATE KEY UPDATE
    token_used = VALUES(token_used),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO ai_token_usage_event (
    usage_event_id,
    user_id,
    feature_key,
    provider,
    model,
    input_tokens,
    cached_input_tokens,
    output_tokens,
    reasoning_tokens,
    total_tokens,
    trace_id,
    occurred_at
)
SELECT CONCAT('dev-admin-dashboard-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-', seed.event_key),
       u.id,
       seed.feature_key,
       'openai',
       seed.model,
       seed.input_tokens,
       seed.cached_input_tokens,
       seed.output_tokens,
       0,
       seed.input_tokens + seed.output_tokens,
       CONCAT('trace_dev_', seed.event_key),
       DATE_SUB(NOW(), INTERVAL seed.minutes_ago MINUTE)
FROM users u
JOIN (
    SELECT 'basic01' AS event_key, 'peai.test+basic01@local' AS email, 'assistant_chat' AS feature_key, 'gpt-4.1-mini' AS model, 1800 AS input_tokens, 500 AS cached_input_tokens, 620 AS output_tokens, 15 AS minutes_ago
    UNION ALL SELECT 'basic02', 'peai.test+basic02@local', 'writing_score', 'gpt-4.1-mini', 2600, 800, 780, 50
    UNION ALL SELECT 'pro01', 'peai.test+pro01@local', 'essay_polish', 'gpt-4.1', 3600, 1200, 1200, 80
    UNION ALL SELECT 'premium01', 'peai.test+premium01@local', 'agent_workflow', 'gpt-4.1', 5200, 1800, 2100, 120
) seed ON seed.email = u.email
ON DUPLICATE KEY UPDATE
    feature_key = VALUES(feature_key),
    provider = VALUES(provider),
    model = VALUES(model),
    input_tokens = VALUES(input_tokens),
    cached_input_tokens = VALUES(cached_input_tokens),
    output_tokens = VALUES(output_tokens),
    total_tokens = VALUES(total_tokens),
    trace_id = VALUES(trace_id),
    occurred_at = VALUES(occurred_at);
