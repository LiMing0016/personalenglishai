# 后端现状文档（Backend Current State）

本文档仅基于 `backend/` 当前代码。

## 1. 分层结构

后端代码主要分层：

- `common/`：过滤器、统一异常、响应结构、错误码
- `controller/`：HTTP 接口入口
- `service/`：业务逻辑
- `mapper/` + `resources/mapper/*.xml`：MyBatis 持久化
- `entity/`：实体模型
- `ai/`：AI command 编排与上下文处理
- `config/`：Web/密码/OpenAI 启动检查等配置
- `util/`：JWT 工具类等

## 2. 请求链路（现状）

1. 进入 `SecurityHeadersFilter`（Order 0）设置安全响应头。
2. 进入 `JwtAuthenticationFilter`（Order 1）执行 JWT 校验与白名单放行。
3. 路由到 Controller。
4. Controller 调用 Service。
5. Service 调用 Mapper 访问数据库。
6. 异常由 `GlobalExceptionHandler` 统一转换为 `ApiResponse`（或保留 controller 自定义返回）。

## 3. 鉴权现状

- 认证依赖 JWT `Authorization: Bearer ...`。
- JWT 解析后向 request 注入：`userId`、`nickname`、`tenantId`、`workspaceId`。
- 白名单：
  - `/api/v1/auth/*`
  - `/health`
  - `/api/ping`
- 开发特例：`/api/ai/command` 在 `dev/local` 环境可无 JWT 调试。

限制：当前没有角色级授权拦截（admin RBAC 尚未建立）。

## 4. 主要接口域

### 4.1 auth v1（`/api/v1/auth`）

控制器：`AuthControllerV1`

已实现能力：

- 邮箱注册/登录
- 滑动验证码生成与校验
- refresh/logout（refresh token 走 httpOnly cookie）
- 邮箱验证与重发
- 忘记密码与重置密码
- 短信验证码发送
- 手机 OTP 登录/密码登录/手机注册

### 4.2 用户中心（`/api/users/me`）

控制器：`UserController`、`UserProfileController`

已实现能力：

- 修改密码
- 读取个人资料
- 修改昵称
- 更新学段
- 查询能力画像
- 查询用户统计

### 4.3 写作（`/api/writing`）

控制器：`WritingController`

已实现能力：

- 同步评测：`POST /evaluate`
- 异步评测：`POST /evaluate/submit` + `GET /evaluate/tasks/{requestId}`
- AI 对话改写：`POST /chat`
- 分级润色：`POST /polish`
- 历史列表/详情
- 收藏切换

### 4.4 文档（`/api/docs`）

控制器：`DocumentController`

已实现能力：

- 文档创建
- 追加 revision（含 expectedLatestRevision）
- 读取最新/指定版本
- 删除（软删）

### 4.5 Rubric（`/api/v1/rubric`）

控制器：`RubricController`

已实现能力：

- 获取 active rubric（按 stage + mode）

### 4.6 AI（`/api/ai`）

控制器：`AICommandController`、`AiController`

已实现能力：

- `POST /api/ai/command`：意图编排（generate/rewrite/chat）
- `POST /api/ai/generate`：旧生成接口（仍保留）

## 5. AI 子系统现状

### 5.1 编排

- `AICommandConfig` 在启动时注册 handler：`generate`、`rewrite`、`chat`。
- `DefaultAIOrchestrator` 负责执行意图流程。

### 5.2 上下文

- `ai.context` + `ai.prompt` 负责上下文拼接。
- 支持 rule/python/hybrid 模式开关（配置项）。
- 可启用 Python context sidecar（见 compose）。

## 6. 统一错误与响应

### 6.1 `ApiResponse<T>`

字段：`code`、`message`、`data`、`traceId`。

### 6.2 全局异常处理

- 参数校验、不可读请求体、唯一键冲突、业务异常、系统异常。
- 错误码定义集中在 `ErrorCode`。

注意：并非所有接口都使用 `ApiResponse<T>` 包装，writing/doc/ping 存在裸 DTO/字符串返回。

## 7. 关键配置

主配置文件：`backend/src/main/resources/application.yml`

关键项：

- 数据库：`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
- JWT：`JWT_SECRET`、`JWT_ACCESS_TOKEN_SECONDS`、`JWT_REFRESH_TOKEN_SECONDS`
- 服务端口：`SERVER_PORT`（默认 18080）
- 邮件：`MAIL_*` + `MAIL_ENABLED`
- OpenAI 客户端：`OPENAI_BASE_URL`、timeout、retry、proxy、circuit breaker
- AI 上下文：`AI_CONTEXT_CONVERSATION_*`
- LanguageTool/Sapling 开关

## 8. 已知技术现状（非结论化）

- 认证与用户接口较多使用 `ApiResponse`，写作/文档接口更偏业务 DTO 直出。
- 管理员域尚未落地：无管理员控制器、无管理员鉴权、无管理员审计。
