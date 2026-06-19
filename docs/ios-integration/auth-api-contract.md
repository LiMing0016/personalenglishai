---
title: 认证 API 契约
status: draft
owner: backend
last_updated: 2026-06-18
review_cycle: on-change
related_code:
  - backend/src/main/java/com/personalenglishai/backend/controller/auth/v1/AuthControllerV1.java
  - backend/src/main/java/com/personalenglishai/backend/controller/UserController.java
  - backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java
related_docs:
  - docs/ios-integration/README.md
  - docs/architecture/auth.md
---

# 认证 API 契约

## 当前结论

iPad 端使用 `/api/v1/auth/**` 完成注册、登录、刷新、退出、验证码、密码重置、短信登录注册；已登录用户改密使用 `/api/users/me/password`。登录成功后，access token 通过 JSON 返回，refresh token 通过 httpOnly Cookie 返回。

## 通用约定

### 统一响应体

```json
{
  "code": "0",
  "message": "OK",
  "data": {},
  "traceId": "trace-id"
}
```

### Token

登录和刷新成功响应：

```json
{
  "token": "jwt",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

iPad 端保存 `data.token` 作为 access token。refresh token 不在 JSON 中返回，由后端设置：

```http
Set-Cookie: refresh_token=...; HttpOnly; Path=/api/v1/auth/; SameSite=Lax
```

本地 `COOKIE_SECURE=false`，生产环境可启用 Secure Cookie。

### 通用错误码

| HTTP 状态 | 错误码 | 场景 | iPad 端处理 |
| --- | --- | --- | --- |
| 400 | `400001` | 参数验证失败 | 表单字段级提示 |
| 400 | `400020` | 重置链接无效或已使用 | 让用户重新申请 |
| 400 | `400021` | 重置链接过期 | 让用户重新申请 |
| 400 | `400030` | 短信验证码无效或过期 | 允许重新输入或重新发送 |
| 400 | `400040` | 滑动验证码无效或过期 | 重新拉取验证码 |
| 401 | `401001` | 用户名或密码错误 | 登录失败提示 |
| 401 | `401002` | refresh token 无效 | 清理登录态 |
| 401 | `401003` | 手机号未注册 | 引导注册 |
| 401 | `401004` | 手机账号未设置密码 | 引导验证码登录 |
| 401 | `401005` | 当前密码错误 | 改密页字段提示 |
| 403 | `403020` | 邮箱未验证 | 展示验证邮箱提示 |
| 409 | `409001` | 邮箱已注册 | 引导登录 |
| 409 | `409003` | 手机号已注册 | 引导登录 |
| 429 | `429001` | 登录尝试过多 | 倒计时后再试 |
| 429 | `429002` | 短信发送过频 | 倒计时后再发 |
| 429 | `429003` | 验证邮件发送过频 | 稍后重试 |

## 接口

### 邮箱注册

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Request：

```json
{
  "email": "user@example.com",
  "password": "Passw0rd123",
  "nickname": "Carter"
}
```

约束：`email` 必须是邮箱；`password` 至少 8 位且包含大小写字母和数字；`nickname` 长度 1-50。

Response：HTTP `201`。

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "userId": 1
  }
}
```

错误码：`400001`、`409001`、`429003`、`500000`。

iPad 验收：注册成功后展示邮箱验证提示；弱密码和重复邮箱展示字段错误。

### 获取滑动验证码

```http
GET /api/v1/auth/captcha
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "captchaId": "captcha-id",
    "bgImage": "data:image/png;base64,...",
    "pieceImage": "data:image/png;base64,..."
  }
}
```

错误码：`500000`。

iPad 验收：登录页能展示背景图和滑块图；图片字段是 data URL。

### 验证滑动验证码

```http
POST /api/v1/auth/captcha/verify
```

Request：

```json
{
  "captchaId": "captcha-id",
  "x": 123
}
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "verified": true,
    "captchaToken": "captcha-token"
  }
}
```

验证失败时仍可能返回 `code = "0"`，但 `verified = false`。

错误码：`400001`。

iPad 验收：`verified=false` 时不调用登录接口，提示重新拖动。

### 邮箱登录

```http
POST /api/v1/auth/login
```

Request：

```json
{
  "email": "user@example.com",
  "password": "Passw0rd123",
  "captchaToken": "captcha-token"
}
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "token": "jwt",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

错误码：`400001`、`400040`、`401001`、`403020`、`429001`。

iPad 验收：登录成功后后续 `/api/assistant/projects` 带 Authorization 可以成功；登录失败不清空用户输入的邮箱。

### 本地 dev-login

```http
POST /api/v1/auth/dev-login
```

状态：仅本地显式开启 `app.dev-admin-login.enabled=true` 且请求来自 loopback 时可用。

Request：

```json
{
  "email": "admin@example.com",
  "password": "Passw0rd123"
}
```

Response：同邮箱登录。

错误码：未开启时返回 `404`；参数错误返回 `400001`。

iPad 验收：只用于本地自动化或人工验收，不允许打包到生产配置。

### 刷新 token

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=...
```

Request：无。

Response：同邮箱登录，并重新设置 refresh cookie。

错误码：`401002`。

iPad 验收：access token 过期后先调用 refresh；refresh 成功后重放原请求；refresh 失败清理登录态。

### 退出登录

```http
POST /api/v1/auth/logout
Cookie: refresh_token=...
```

Response：

```json
{
  "code": "0",
  "message": "OK"
}
```

iPad 验收：退出后本地删除 access token，并清空相关 Cookie。

### 验证邮箱

```http
GET /api/v1/auth/verify-email?token=xxx
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "status": "verified"
  }
}
```

错误码：`400001`。

iPad 验收：从邮件 deep link 打开时可展示验证结果。

### 重发验证邮件

```http
POST /api/v1/auth/resend-verification
```

Request：

```json
{
  "email": "user@example.com"
}
```

Response：成功空响应。为防枚举，无论邮箱是否存在都返回成功。

错误码：`429003`。

iPad 验收：按钮点击后进入冷却倒计时。

### 请求密码重置

```http
POST /api/v1/auth/forgot-password
```

Request：

```json
{
  "email": "user@example.com"
}
```

Response：成功空响应。为防枚举，无论邮箱是否存在都返回成功。

iPad 验收：展示“如果邮箱存在，将收到重置邮件”。

### 验证重置 token

```http
GET /api/v1/auth/reset-password/validate?token=xxx
```

Response：

```json
{
  "code": "0",
  "message": "OK",
  "data": {
    "status": "valid"
  }
}
```

错误码：`400020`、`400021`。

iPad 验收：deep link 进入重置页前先校验 token。

### 执行密码重置

```http
POST /api/v1/auth/reset-password
```

Request：

```json
{
  "token": "reset-token",
  "password": "NewPassw0rd123"
}
```

Response：成功空响应。

错误码：`400001`、`400020`、`400021`。

iPad 验收：成功后返回登录页；弱密码展示字段提示。

### 发送短信验证码

```http
POST /api/v1/auth/sms/send
```

Request：

```json
{
  "phone": "13800138000",
  "purpose": "login"
}
```

`purpose` 只能是 `login` 或 `register`。

Response：成功空响应。

错误码：`400001`、`429002`。

iPad 验收：发送后倒计时；手机号格式错误时不触发网络重试。

### 手机登录

```http
POST /api/v1/auth/phone/login
```

Request，验证码模式：

```json
{
  "phone": "13800138000",
  "mode": "otp",
  "code": "123456"
}
```

Request，密码模式：

```json
{
  "phone": "13800138000",
  "mode": "password",
  "password": "Passw0rd123"
}
```

Response：同邮箱登录。

错误码：`400001`、`400030`、`401003`、`401004`。

iPad 验收：两种登录模式都能拿到 token；`401004` 时切换到验证码模式。

### 手机注册

```http
POST /api/v1/auth/phone/register
```

Request：

```json
{
  "phone": "13800138000",
  "code": "123456",
  "nickname": "Carter"
}
```

Response：HTTP `201`，同邮箱登录。

错误码：`400001`、`400030`、`409003`。

iPad 验收：注册成功后直接进入已登录状态。

### 修改密码

```http
POST /api/users/me/password
Authorization: Bearer <access_token>
```

Request：

```json
{
  "currentPassword": "OldPassw0rd123",
  "newPassword": "NewPassw0rd123"
}
```

Response：成功空响应。

错误码：`400001`、`401002`、`401005`。

iPad 验收：当前密码错误时保持页面；成功后提示重新登录或继续使用当前 token，以产品策略为准。
