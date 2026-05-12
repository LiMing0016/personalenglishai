# 邮箱注册验证上线加固 Trae 题单

## 目标

本题单用于把邮箱注册验证链路补齐到可上线状态。

核心目标：

- 邮箱注册后必须完成验证，未验证账号不能登录使用系统。
- 密码错误时不暴露账号是否存在或邮箱是否未验证。
- 验证邮件重发和注册首次发信使用 Redis Lua 原子限流。
- 前端形成完整用户闭环：注册后检查邮箱、未验证登录提示、可重新发送验证邮件。
- 手机号和微信登录暂不上线，前端先隐藏手机入口。

## 统一接口契约

### 未验证邮箱禁止登录

```text
HTTP 403
code: 403020
message: 邮箱尚未验证，请先完成邮箱验证
```

### 验证邮件发送频率过高

```text
HTTP 429
code: 429003
message: 验证邮件发送过于频繁，请稍后再试
```

## 拆分原则

- 每题尽量独立，可单独提交。
- 后端题目不修改 `web` 目录。
- 前端题目不修改 `backend` 目录。
- 题目之间通过固定错误码和 API 契约对齐。
- 不做手机号注册、微信登录、人机风控、运营后台等扩展能力。

## 题目 1：后端新增邮箱验证错误码

### Prompt

请在 `backend` 中新增邮箱验证上线所需错误码，不修改业务逻辑。

目标：

1. 新增未验证邮箱禁止登录错误码：
   - enum: `AUTH_EMAIL_NOT_VERIFIED`
   - code: `403020`
   - message: `邮箱尚未验证，请先完成邮箱验证`
2. 新增验证邮件发送频率过高错误码：
   - enum: `AUTH_EMAIL_RESEND_RATE_LIMITED`
   - code: `429003`
   - message: `验证邮件发送过于频繁，请稍后再试`
3. 确认 `GlobalExceptionHandler` 会根据 code 前缀返回正确 HTTP 状态：
   - `403020` -> HTTP 403
   - `429003` -> HTTP 429

建议改动范围：

- `backend/src/main/java/com/personalenglishai/backend/common/error/ErrorCode.java`
- 如有必要，补充 `GlobalExceptionHandler` 测试

不要修改：

- 登录逻辑
- 邮件发送逻辑
- 前端代码

### 验收

- `ErrorCode` 中存在 `AUTH_EMAIL_NOT_VERIFIED`，code 为 `403020`。
- `ErrorCode` 中存在 `AUTH_EMAIL_RESEND_RATE_LIMITED`，code 为 `429003`。
- `BizException(AUTH_EMAIL_NOT_VERIFIED)` 会返回 HTTP 403。
- `BizException(AUTH_EMAIL_RESEND_RATE_LIMITED)` 会返回 HTTP 429。
- 后端相关测试通过。

## 题目 2：后端实现未验证邮箱禁止登录

### Prompt

请在 `backend` 中实现“邮箱未验证禁止登录”，不修改前端代码。

目标：

1. 邮箱密码登录时，必须先校验邮箱和密码。
2. 如果用户不存在或密码错误，仍返回 `AUTH_LOGIN_FAILED`，不暴露邮箱是否存在或是否未验证。
3. 只有密码正确后，才检查 `user.emailVerified`。
4. 如果 `emailVerified=false`，返回 `AUTH_EMAIL_NOT_VERIFIED`。
5. 未验证邮箱登录失败时，不签发 access token，不设置 refresh cookie。
6. 已验证邮箱正常登录。

建议改动范围：

- `backend/src/main/java/com/personalenglishai/backend/service/auth/impl/AuthServiceImpl.java`
- `backend/src/test/java` 下对应 `AuthServiceImpl` 测试或认证测试

不要修改：

- 手机登录
- 注册逻辑
- 邮件发送逻辑
- 前端代码

### 验收

- 未验证邮箱 + 正确密码：登录失败，code 为 `403020`。
- 未验证邮箱 + 错误密码：登录失败，code 为 `401001`。
- 不存在邮箱 + 任意密码：登录失败，code 为 `401001`。
- 已验证邮箱 + 正确密码：登录成功。
- 已验证邮箱 + 错误密码：登录失败，code 为 `401001`。
- 登录失败计数逻辑仍正常工作。
- 后端测试通过。

## 题目 3：后端控制器保证未验证登录不写 refresh cookie

### Prompt

请检查并补充 `backend` 登录控制器测试，确保未验证邮箱登录失败时不会写 refresh cookie。

目标：

1. 当 `AuthService.login` 抛出 `AUTH_EMAIL_NOT_VERIFIED` 时，`AuthControllerV1` 不应调用 `setRefreshCookie`。
2. 响应中不能包含 `refresh_token` cookie。
3. 响应状态应为 HTTP 403。
4. 响应 code 应为 `403020`。

建议改动范围：

- `backend/src/test/java/com/personalenglishai/backend/controller/auth/v1/AuthControllerV1Test.java`
- 如现有控制器无需改动，只补测试即可

不要修改：

- 前端代码
- Redis 限流逻辑

### 验收

- 控制器测试覆盖未验证邮箱登录场景。
- 响应 HTTP 403。
- 响应 JSON `code=403020`。
- 响应没有 `refresh_token` cookie。
- 原有登录成功测试仍通过并能设置 refresh cookie。

## 题目 4：后端实现 Redis Lua 邮件验证限流服务

### Prompt

请在 `backend` 中新增 Redis Lua 邮件验证限流服务，不接入业务逻辑。

目标：

1. 使用项目已有 `StringRedisTemplate`。
2. 使用 Redis Lua 脚本保证限流检查和计数更新原子执行。
3. 支持以下维度：
   - 同邮箱 60 秒冷却。
   - 同邮箱 1 小时最多 5 次。
   - 同 IP 1 小时最多 20 次。
4. key 命名建议：
   - `auth:email:verify:resend:cooldown:{email}`
   - `auth:email:verify:resend:hour:{email}`
   - `auth:email:verify:resend:ip:{ip}`
5. 服务对外提供清晰方法，例如：
   - `checkAndConsumeResend(email, ip)`
   - `checkAndConsumeRegisterSend(ip)`
6. 命中限流时抛出 `BizException(AUTH_EMAIL_RESEND_RATE_LIMITED)`。
7. 不在此题中修改 `EmailVerificationService` 或 Controller。

建议改动范围：

- 新增 `backend/src/main/java/com/personalenglishai/backend/service/auth/EmailVerificationRateLimitService.java`
- 新增对应单元测试

不要修改：

- 登录逻辑
- 注册逻辑
- 重发验证邮件逻辑
- 前端代码

### 验收

- 服务使用 `StringRedisTemplate` 执行 Lua 脚本。
- 同邮箱 60 秒内重复消费会被拒绝。
- 同邮箱 1 小时超过 5 次会被拒绝。
- 同 IP 1 小时超过 20 次会被拒绝。
- 注册发信 IP 限流可单独调用。
- 限流命中抛出 `AUTH_EMAIL_RESEND_RATE_LIMITED`。
- 单元测试覆盖通过和超限场景。

## 题目 5：后端接入重发验证邮件 Redis 限流

### Prompt

请把 Redis 邮件验证限流服务接入 `POST /api/v1/auth/resend-verification`。

目标：

1. `resend-verification` 未登录仍可访问。
2. Controller 需要获取客户端 IP，并传给 `EmailVerificationService`。
3. `EmailVerificationService.resendVerification` 在查用户前先执行限流。
4. 限流通过后再查用户。
5. 邮箱不存在时仍返回成功。
6. 邮箱已验证时仍返回成功。
7. 邮箱未验证时生成新 token 并发送验证邮件。
8. 限流命中返回 HTTP 429，code 为 `429003`。

建议改动范围：

- `backend/src/main/java/com/personalenglishai/backend/controller/auth/v1/AuthControllerV1.java`
- `backend/src/main/java/com/personalenglishai/backend/service/auth/EmailVerificationService.java`
- `backend/src/test/java` 下相关测试

不要修改：

- 前端代码
- 登录限制逻辑
- 手机短信逻辑

### 验收

- `resend-verification` 会在查用户前调用限流服务。
- 不存在邮箱返回成功，不暴露账号状态。
- 已验证邮箱返回成功，不发送邮件。
- 未验证邮箱返回成功，并发送验证邮件。
- 限流命中返回 HTTP 429，code 为 `429003`。
- 该接口未登录仍可调用。
- 测试覆盖以上场景。

## 题目 6：后端接入注册首次发验证邮件 IP 限流

### Prompt

请把 Redis 邮件验证限流服务接入邮箱注册后的首次验证邮件发送。

目标：

1. 邮箱注册成功后发送验证邮件前，先做 IP 级限流。
2. 同 IP 1 小时最多触发 20 次注册验证邮件。
3. 限流命中返回 HTTP 429，code 为 `429003`。
4. 注意事务边界：不要因为发信限流造成数据库产生不一致状态。
5. 如果当前注册方法和发信方法分离，请选择最小改动方式保证行为清晰。

建议改动范围：

- `backend/src/main/java/com/personalenglishai/backend/controller/auth/v1/AuthControllerV1.java`
- `backend/src/main/java/com/personalenglishai/backend/service/auth/EmailVerificationService.java`
- `backend/src/test/java` 下相关测试

不要修改：

- 前端代码
- 登录逻辑
- 手机注册逻辑

### 验收

- 注册首次发送验证邮件前执行 IP 限流。
- 同 IP 超限时返回 HTTP 429，code 为 `429003`。
- 正常注册仍会创建用户并发送验证邮件。
- 限流测试覆盖正常和超限场景。
- 不影响邮箱已存在时的 `AUTH_EMAIL_EXISTS` 行为。

## 题目 7：前端登录页处理未验证邮箱状态

### Prompt

请在 `web` 中处理未验证邮箱登录失败体验，不修改 `backend` 代码。

目标：

1. 登录页识别后端返回 `code=403020`。
2. 显示“邮箱尚未验证，请先完成邮箱验证”。
3. 在提示附近提供“重新发送验证邮件”入口。
4. 点击重新发送时调用 `POST /api/v1/auth/resend-verification`。
5. 重发成功后显示成功提示，并进入 60 秒倒计时。
6. 如果返回 `code=429003`，显示“验证邮件发送过于频繁，请稍后再试”。
7. 只有邮箱登录表单需要该功能，手机登录不处理。

建议改动范围：

- `web/src/views/LoginFormView.vue`
- `web/src/api/authApi.ts` 或 `web/src/api/auth.ts`
- `web/src/types/api.ts` 如有必要

不要修改：

- `backend` 目录
- 注册页
- 写作页主链路

### 验收

- 登录返回 `403020` 时，页面不跳转 `/app`。
- 页面显示邮箱未验证提示。
- 页面出现重新发送验证邮件入口。
- 点击重发会调用 `/v1/auth/resend-verification`。
- 重发成功后按钮进入 60 秒倒计时。
- 返回 `429003` 时显示频率过高提示。
- `npm run build` 通过。

## 题目 8：前端调整全局 403 拦截与 Check Email 页面

### Prompt

请在 `web` 中优化邮箱验证相关页面和 HTTP 拦截器，不修改 `backend` 代码。

目标：

1. `http.ts` 中不要把 `code=403020` 的响应统一 toast 成“无权限访问”。
2. 其他 403 仍保持原有“无权限访问”提示。
3. `/check-email` 页面如果 URL 没有 `email` query 参数，应展示邮箱输入框。
4. 用户输入邮箱后，可以点击重新发送验证邮件。
5. `/check-email?email=xxx` 仍保持现有展示和重发能力。
6. 返回 `code=429003` 时，显示频率过高提示。

建议改动范围：

- `web/src/api/http.ts`
- `web/src/pages/CheckEmail.vue`
- `web/src/api/authApi.ts` 如有必要

不要修改：

- `LoginFormView.vue`
- `RegisterView.vue`
- `backend` 目录

### 验收

- `403020` 不触发全局“无权限访问”toast。
- 普通 403 仍触发“无权限访问”toast。
- 直接访问 `/check-email` 时可以输入邮箱并重发。
- 访问 `/check-email?email=test@example.com` 时行为不回退。
- `429003` 显示频率过高文案。
- `npm run build` 通过。

## 题目 9：前端隐藏手机登录和手机注册入口

### Prompt

请在 `web` 中临时隐藏手机登录和手机注册入口，不修改 `backend` 代码。

背景：

手机号登录/注册和微信登录暂不上线，本次只上线邮箱注册验证。

目标：

1. 登录页只展示邮箱登录，不展示手机登录 tab。
2. 注册页只展示邮箱注册，不展示手机注册 tab。
3. 不删除手机登录/注册代码，只隐藏入口或固定 `activeTab=email`，便于后续恢复。
4. 不影响邮箱登录、邮箱注册、邮箱验证页面。
5. 不修改后端接口。

建议改动范围：

- `web/src/views/LoginFormView.vue`
- `web/src/views/RegisterView.vue`
- 如还有旧登录/注册页入口，也检查但保持最小改动

不要修改：

- `backend` 目录
- 手机 API 封装
- 路由结构

### 验收

- 登录页看不到手机登录 tab。
- 注册页看不到手机注册 tab。
- 邮箱登录仍可提交。
- 邮箱注册成功后仍跳转 `/check-email`。
- `npm run build` 通过。

## 题目 10：邮箱验证上线文档与人工验收清单

### Prompt

请补充邮箱验证上线文档，不修改业务代码。

目标：

1. 文档说明邮箱注册后必须验证，未验证不能登录。
2. 文档记录错误码：
   - `403020`：邮箱尚未验证
   - `429003`：验证邮件发送过于频繁
3. 文档记录 Redis 限流规则：
   - 同邮箱 60 秒冷却
   - 同邮箱 1 小时最多 5 次
   - 同 IP 1 小时最多 20 次
   - 注册首次发信也受 IP 限流
4. 文档记录生产环境变量：
   - `MAIL_ENABLED=true`
   - `APP_BASE_URL=https://www.personalenglishai.com`
   - `COOKIE_SECURE=true`
   - `REDIS_HOST` / `REDIS_PORT`
   - `MAIL_HOST=smtpdm.aliyun.com`
   - `MAIL_FROM` 使用阿里云 DirectMail 已验证发信地址
5. 增加人工验收清单。

建议改动范围：

- `docs/auth/README.md`
- `docs/deploy/environment-variables.md`
- `README.md` 如需要只加链接或简短说明

不要修改：

- `backend/src`
- `web/src`

### 验收

- 文档清楚说明未验证邮箱不能登录。
- 文档包含 `403020` 和 `429003`。
- 文档包含 Redis 限流规则。
- 文档包含生产环境变量检查项。
- 文档包含人工联调步骤：
  - 注册新邮箱
  - 收到验证邮件
  - 未验证前登录失败
  - 点击验证链接
  - 验证后登录成功
  - 重发验证邮件限流
- 文档不包含真实 SMTP 密码、`JWT_SECRET`、数据库密码等敏感值。

## 建议执行顺序

```text
题目 1：后端错误码
  ↓
题目 2：未验证禁止登录
  ↓
题目 3：登录 cookie 安全测试
  ↓
题目 4：Redis Lua 限流服务
  ↓
题目 5：重发验证邮件限流接入
  ↓
题目 6：注册首次发信限流接入
  ↓
题目 7：登录页未验证体验
  ↓
题目 8：全局 403 与 check-email 页面
  ↓
题目 9：隐藏手机入口
  ↓
题目 10：文档和验收清单
```

## 分工建议

```text
后端：题目 1-6
前端：题目 7-9
文档/联调：题目 10
```

## 最终整体验收

- 新用户邮箱注册成功后进入 `/check-email`。
- 未点击验证链接前，用正确密码登录，返回 `403020`，前端提示邮箱未验证。
- 用错误密码登录未验证账号，返回普通登录失败 `401001`。
- 点击邮件验证链接后，`/verify-email` 显示验证成功。
- 验证成功后可以正常登录进入 `/app`。
- 重复点击重新发送验证邮件触发 60 秒冷却或 `429003`。
- 高频请求同邮箱/同 IP 会被 Redis 限流。
- 登录/注册页不展示手机入口。
- 后端测试通过：

```bash
./mvnw.cmd -q test
```

- 前端构建通过：

```bash
npm run build
```
