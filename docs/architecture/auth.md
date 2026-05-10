# Auth 模块验收说明

## 数据库要求

`users` 表需包含至少：`id`（自增主键）、`email`（唯一）、`password_hash`、`nickname`、`created_at`、`updated_at`。  
若仍为 `username`/`password` 结构，请先迁移为 `nickname`/`password_hash` 以与 Mapper 一致。

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
- 邮箱已存在：HTTP **409**，`AUTH_EMAIL_EXISTS`
- 参数校验失败：HTTP **400**，`COMMON_VALIDATION_ERROR`

### 登录

- **POST** `/api/v1/auth/login`
- Request JSON：**仅允许** `{"email":"xxx","password":"xxx"}`；多余字段（如 `nickname`）→ HTTP **400**，`400001`
- 成功：HTTP **200**，`data` 含 `token`、`tokenType`（Bearer）、`expiresIn`
- 用户不存在或密码错误：HTTP **401**，`401001`，`"用户名或密码错误"`
- 参数校验失败：HTTP **400**，`400001`

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
