-- 短信验证码表
CREATE TABLE IF NOT EXISTS sms_verification_code (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone      VARCHAR(20)  NOT NULL,
    code       VARCHAR(6)   NOT NULL,
    purpose    VARCHAR(20)  NOT NULL DEFAULT 'login',  -- login | register
    expires_at DATETIME     NOT NULL,
    used       TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone_purpose (phone, purpose)
);

-- users 表适配手机注册（邮箱/密码可为空）
ALTER TABLE users MODIFY email VARCHAR(100) NULL;
ALTER TABLE users MODIFY password_hash VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN phone_verified TINYINT NOT NULL DEFAULT 0 AFTER phone;
-- phone 列已存在，加唯一索引（NULL 不冲突）
ALTER TABLE users ADD UNIQUE INDEX uk_phone (phone);
