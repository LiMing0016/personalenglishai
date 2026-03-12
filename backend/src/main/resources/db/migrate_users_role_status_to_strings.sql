START TRANSACTION;

ALTER TABLE users
  MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT 'user/admin',
  MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled';

UPDATE users
SET role = CASE role
  WHEN '1' THEN 'admin'
  WHEN '0' THEN 'user'
  ELSE role
END;

UPDATE users
SET status = CASE status
  WHEN '1' THEN 'active'
  WHEN '0' THEN 'disabled'
  ELSE status
END;

UPDATE users
SET role = 'user'
WHERE role IS NULL OR role NOT IN ('user', 'admin');

UPDATE users
SET status = 'active'
WHERE status IS NULL OR status NOT IN ('active', 'disabled');

COMMIT;
