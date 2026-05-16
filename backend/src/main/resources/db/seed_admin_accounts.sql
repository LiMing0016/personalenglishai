START TRANSACTION;

SET @pwd := '$2a$10$LQPpQKhFjT5sQ4/DELEyeuRqVM6gay3VQW1Uz0LjMxQdj7/AH3vWG';
SET @extra_admin_pwd := '$2a$10$OX/kcvP8zJzCpah7TLbTfuV7C.BccqCQ95eZQ5ZeuohoOgpBD01ei';

-- super_admin
INSERT INTO users (
  email,
  email_verified,
  phone,
  phone_verified,
  password_hash,
  nickname,
  role,
  status,
  register_source,
  token_version,
  last_active_at,
  created_at,
  updated_at
) VALUES (
  'superadmin@peai.local',
  1,
  NULL,
  0,
  @pwd,
  'Super Admin',
  'admin',
  'active',
  'email',
  0,
  NOW(),
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  email_verified = VALUES(email_verified),
  password_hash = VALUES(password_hash),
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status),
  register_source = VALUES(register_source),
  updated_at = NOW();

SELECT id INTO @super_id FROM users WHERE email = 'superadmin@peai.local';

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES (@super_id, 'super_admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO user_profile (user_id, study_stage, ai_mode )
VALUES (@super_id, 'ielts', 0)
ON DUPLICATE KEY UPDATE
  study_stage = VALUES(study_stage),
  ai_mode = VALUES(ai_mode),
  updated_at = NOW();

-- support_admin
INSERT INTO users (
  email,
  email_verified,
  phone,
  phone_verified,
  password_hash,
  nickname,
  role,
  status,
  register_source,
  token_version,
  last_active_at,
  created_at,
  updated_at
) VALUES (
  'supportadmin@peai.local',
  1,
  NULL,
  0,
  @pwd,
  'Support Admin',
  'admin',
  'active',
  'email',
  0,
  NOW(),
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  email_verified = VALUES(email_verified),
  password_hash = VALUES(password_hash),
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status),
  register_source = VALUES(register_source),
  updated_at = NOW();

SELECT id INTO @support_id FROM users WHERE email = 'supportadmin@peai.local';

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES (@support_id, 'support_admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO user_profile (user_id, study_stage, ai_mode )
VALUES (@support_id, 'ielts', 0)
ON DUPLICATE KEY UPDATE
  study_stage = VALUES(study_stage),
  ai_mode = VALUES(ai_mode),
  updated_at = NOW();

-- content_admin
INSERT INTO users (
  email,
  email_verified,
  phone,
  phone_verified,
  password_hash,
  nickname,
  role,
  status,
  register_source,
  token_version,
  last_active_at,
  created_at,
  updated_at
) VALUES (
  'contentadmin@peai.local',
  1,
  NULL,
  0,
  @pwd,
  'Content Admin',
  'admin',
  'active',
  'email',
  0,
  NOW(),
  NOW(),
  NOW()
)
ON DUPLICATE KEY UPDATE
  email_verified = VALUES(email_verified),
  password_hash = VALUES(password_hash),
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status),
  register_source = VALUES(register_source),
  updated_at = NOW();

SELECT id INTO @content_id FROM users WHERE email = 'contentadmin@peai.local';

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES (@content_id, 'content_admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO user_profile (user_id, study_stage, ai_mode )
VALUES (@content_id, 'ielts', 0)
ON DUPLICATE KEY UPDATE
  study_stage = VALUES(study_stage),
  ai_mode = VALUES(ai_mode),
  updated_at = NOW();

-- local extra super admins
INSERT INTO users (
  email,
  email_verified,
  phone,
  phone_verified,
  password_hash,
  nickname,
  role,
  status,
  register_source,
  token_version,
  last_active_at,
  created_at,
  updated_at
) VALUES
  ('admin01@admin.com', 1, NULL, 0, @extra_admin_pwd, 'Admin 01', 'admin', 'active', 'email', 0, NOW(), NOW(), NOW()),
  ('admin02@admin.com', 1, NULL, 0, @extra_admin_pwd, 'Admin 02', 'admin', 'active', 'email', 0, NOW(), NOW(), NOW()),
  ('admin03@admin.com', 1, NULL, 0, @extra_admin_pwd, 'Admin 03', 'admin', 'active', 'email', 0, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  email_verified = VALUES(email_verified),
  password_hash = VALUES(password_hash),
  nickname = VALUES(nickname),
  role = VALUES(role),
  status = VALUES(status),
  register_source = VALUES(register_source),
  updated_at = NOW();

SELECT id INTO @admin01_id FROM users WHERE email = 'admin01@admin.com';
SELECT id INTO @admin02_id FROM users WHERE email = 'admin02@admin.com';
SELECT id INTO @admin03_id FROM users WHERE email = 'admin03@admin.com';

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES
  (@admin01_id, 'super_admin', NOW(), NOW()),
  (@admin02_id, 'super_admin', NOW(), NOW()),
  (@admin03_id, 'super_admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO user_profile (user_id, study_stage, ai_mode)
VALUES
  (@admin01_id, 'ielts', 0),
  (@admin02_id, 'ielts', 0),
  (@admin03_id, 'ielts', 0)
ON DUPLICATE KEY UPDATE
  study_stage = VALUES(study_stage),
  ai_mode = VALUES(ai_mode),
  updated_at = NOW();

COMMIT;


