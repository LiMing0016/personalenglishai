START TRANSACTION;

SET @pwd := '$2a$10$cLrfJq7SC/f2IwRSFDhZ1e.k5uBXWIvTIjl.LkkKLphlJI2vywE3q';

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
);

SET @super_id := LAST_INSERT_ID();

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES (@super_id, 'super_admin', NOW(), NOW());

INSERT INTO user_profile (user_id, study_stage, ai_mode )
VALUES (@super_id, 'ielts', 0);

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
);

SET @support_id := LAST_INSERT_ID();

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES (@support_id, 'support_admin', NOW(), NOW());

INSERT INTO user_profile (user_id, study_stage, ai_mode )
VALUES (@support_id, 'ielts', 0);

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
);

SET @content_id := LAST_INSERT_ID();

INSERT INTO admin_user_role (user_id, role, created_at, updated_at)
VALUES (@content_id, 'content_admin', NOW(), NOW());

INSERT INTO user_profile (user_id, study_stage, ai_mode )
VALUES (@content_id, 'ielts', 0);

COMMIT;


