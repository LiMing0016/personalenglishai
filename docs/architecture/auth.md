# Auth 模块验收说明

## 数据库要求

`users` 表需包含至少：`id`（自增主键）、`email`（唯一）、`email_verified`、`password_hash`、`nickname`、`created_at`、`updated_at`。  
若仍为 `username`/`password` 结构，请先迁移为 `nickname`/`password_hash` 以与 Mapper 一致。

邮箱注册还需要 `email_verification_token` 表。全量初始化应执行 `backend/src/main/resources/db/schema.sql`；旧库升级可执行 `backend/src/main/resources/db/create_email_verification.sql`。

## 邮件配置

本地开发可保持 `MAIL_ENABLED=false`，验证邮件会输出到后端日志。

生产环境启用真实 SMTP 时至少配置：

- `APP_BASE_URL`：前端公开访问地址，用于生成 `/verify-email?token=...` 链接
- `MAIL_ENABLED=true`
- `MAIL_HOST` / `MAIL_PORT`
- `MAIL_USERNAME` / `MAIL_PASSWORD`
- `MAIL_FROM`
- `MAIL_SSL_ENABLE` 或 `MAIL_STARTTLS_ENABLE`
- `COOKIE_SECURE=true`：生产 HTTPS 环境保护 refresh cookie

阿里云邮件发送有两种常见配置，不要混用账号体系：

- 阿里云邮件推送 DirectMail：`MAIL_HOST=smtpdm.aliyun.com`，`MAIL_USERNAME` / `MAIL_PASSWORD` 使用 DirectMail 生成的 SMTP 账号和密码，`MAIL_FROM` 使用已验证发信地址。
- 阿里邮箱/企业邮箱：`MAIL_HOST=smtp.qiye.aliyun.com`，`MAIL_USERNAME` 使用邮箱账号，`MAIL_PASSWORD` 使用三方客户端安全密码或授权码。

如果后端部署在阿里云 ECS，优先使用 465 SSL 端口，不要依赖 25 端口。

## 接口契约

### 注册

- **POST** `/api/v1/auth/register`
- Request JSON 示例：
  ```json
  {
    "email": "local_001@test.com",
    "password": "Abcd1234!",
    "nickname": "测试"
  }
  ```
- 成功：HTTP **201**，`ApiResponse` 的 `data` 包含 `userId`
- 成功后后端会创建邮箱验证 token，并发送验证邮件；邮件发送失败不会阻塞注册主流程。
- 注册首次发送验证邮件前会执行 Redis IP 限流，同 IP 1 小时最多触发 20 次；超限返回 HTTP **429**，`429003`。
- 邮箱已存在：HTTP **409**，`AUTH_EMAIL_EXISTS`
- 参数校验失败：HTTP **400**，`COMMON_VALIDATION_ERROR`

### 邮箱验证

- **GET** `/api/v1/auth/verify-email?token=xxx`
- 成功：HTTP **200**，`data.status=VERIFIED`
- 链接过期：HTTP **200**，`data.status=EXPIRED`
- 无效或已使用：HTTP **200**，`data.status=INVALID`

### 重新发送验证邮件

- **POST** `/api/v1/auth/resend-verification`
- Request JSON 示例：`{"email":"local_001@test.com"}`
- 成功：HTTP **200**。为避免邮箱枚举，邮箱不存在或已验证时也返回成功。
- 限流：同邮箱 60 秒冷却、同邮箱 1 小时最多 5 次、同 IP 1 小时最多 20 次；超限返回 HTTP **429**，`429003`。

### 登录

- **POST** `/api/v1/auth/login`
- Request JSON：**仅允许** `{"email":"xxx","password":"xxx"}`；多余字段（如 `nickname`）→ HTTP **400**，`400001`
- 成功：HTTP **200**，`data` 含 `token`、`tokenType`（Bearer）、`expiresIn`
- 用户不存在或密码错误：HTTP **401**，`401001`，`"用户名或密码错误"`
- 邮箱未验证：仅在密码正确后返回 HTTP **403**，`403020`，`"邮箱尚未验证，请先完成邮箱验证"`；不会签发 token，也不会设置 refresh cookie。
- 参数校验失败：HTTP **400**，`400001`

### 错误码

| code | 含义 |
| --- | --- |
| `401001` | 用户不存在或密码错误；密码错误时不会暴露邮箱是否已验证。 |
| `403020` | 邮箱尚未验证，请先完成邮箱验证。 |
| `429003` | 验证邮件发送过于频繁，请稍后再试。 |

### 当前用户档案（需 JWT）

- **GET** `/api/users/me/profile`
- 无 `Authorization: Bearer <token>` → HTTP **401**，`ApiResponse` `code=401001`，`message=未登录或登录已过期`
- 有效 token → HTTP **200**，`data` 含 `userId`、`email`、`nickname`

---

## 验收命令

### 本地

```bash
# 注册（应 201 / 409 / 400，不能 404 / 401）
curl -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"local_001@test.com","password":"Abcd1234!","nickname":"测试"}'

# 登录（先注册同 email 用户后）
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"local_001@test.com","password":"Abcd1234!"}'

# 未验证邮箱登录 -> 403 + 403020；点击邮件验证后同一账号应能登录成功

# 登录传 nickname 等多余字段 -> 必须 400（400001），不得 200
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"local_001@test.com","password":"Abcd1234!","nickname":"测试"}'

# 健康检查（应 200）
curl -i http://localhost:8080/health

# Ping（应 200）
curl -i http://localhost:8080/api/ping

# 受保护接口：无 token -> 401 + 401001
curl -i http://localhost:8080/api/users/me/profile

# 受保护接口：带 token -> 200 + data.userId
# 先登录获取 token，再：
curl -i -H "Authorization: Bearer <TOKEN>" http://localhost:8080/api/users/me/profile
```

### 线上（部署后使用）

```bash
curl -i -X POST http://8.130.80.179:18080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"local_001@test.com","password":"Abcd1234!","nickname":"测试"}'

curl -i -X POST http://8.130.80.179:18080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"local_001@test.com","password":"Abcd1234!"}'
```

---

## 白名单

以下路径允许匿名访问（无 token 不 401）：

- `/api/v1/auth/**`
- `/health`
- `/api/ping`
