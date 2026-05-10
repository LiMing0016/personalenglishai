# 项目现状总览（Current State）

本文档只描述仓库当前已实现内容，不包含假设。

口径日期：2026-03-05。

## 1. 项目目标与当前边界

- 项目类型：英语学习 Web 应用（前后端分离）。
- 主要实现域：认证、个人中心、写作评测、文档版本、Rubric 读取、AI 指令。
- 尚未实现域：管理员端（`/admin` UI 与 `/api/admin/*` API）。

## 2. 技术栈

### 2.1 后端

- Java 17
- Spring Boot 3.2.5
- MyBatis 3.0.3
- MySQL 8
- JWT（jjwt 0.12.3）
- 校验：spring-boot-starter-validation
- 邮件：spring-boot-starter-mail
- AI 请求：spring-boot-starter-webflux
- 语法工具：LanguageTool `language-en:6.6`

### 2.2 前端

- Vue 3.4
- TypeScript 5.5
- Vite 5
- Vue Router 4
- Axios 1.7

### 2.3 运行配套

- Docker Compose（backend + redis + python context sidecar）
- 可叠加 nginx compose 文件

## 3. 仓库结构（主目录）

- `backend/`：Spring Boot 应用与测试
- `web/`：Vue 前端
- `python/context_sidecar/`：上下文侧车服务
- `deploy/nginx/`：Nginx 配置模板
- `docs/`：原始项目文档
- `docs_v1/`：本轮整理文档（不改原 docs）

## 4. 功能现状矩阵

| 领域 | 状态 | 说明 |
|---|---|---|
| 认证（邮箱） | 已实现 | 注册、登录、refresh、logout、邮件验证、找回密码 |
| 认证（手机） | 已实现 | 短信发送、验证码登录、密码登录、手机注册 |
| 滑动验证码 | 已实现 | 获取验证码 + 验证坐标 + 登录携带 captchaToken |
| 用户中心 | 已实现 | 资料读取、昵称/学段修改、密码修改、能力画像、统计 |
| 写作评测 | 已实现 | 同步评测、异步任务、任务查询、历史、详情、收藏 |
| 写作 AI 交互 | 已实现 | chat 改写、selectedText、分级润色、翻译（含个性化参数） |
| 文档管理 | 已实现 | 创建文档、追加 revision、读取、删除 |
| Rubric | 已实现（只读） | 可按 stage/mode 获取 active rubric |
| 管理员端 | 未实现 | 无管理员 API、无管理员 UI、无管理审计 |

## 5. 鉴权与错误处理现状

### 5.1 后端

- `JwtAuthenticationFilter` 实现鉴权入口。
- 白名单：`/api/v1/auth/*`、`/health`、`/api/ping`。
- 普通受保护接口要求 Bearer token。
- `/api/ai/command` 在 `dev/local` 且无 Bearer 时允许匿名调试。

### 5.2 前端

- Axios 请求拦截器自动注入 `Authorization`。
- 401：尝试 refresh，失败后清 token 跳登录。
- 403：仅 toast 提示“无权限访问”。

### 5.3 响应风格

当前接口存在两类风格：

1. `ApiResponse<T>` 包装（典型在 auth/user）。
2. 裸 JSON DTO 或字符串（典型在 writing/doc/ping）。

## 6. 前端路由现状

- 公共：`/` `/login` `/register` `/check-email` `/verify-email` `/forgot-password` `/reset-password`
- 业务：`/app` 下包含
  - `/app` Dashboard
  - `/app/stage-setup`
  - `/app/writing`
  - `/app/vocabulary`
  - `/app/listening`
  - `/app/speaking`
  - `/app/me`
  - `/app/ai-test`
- 当前无 `/admin` 路由。

## 7. 测试现状

后端测试类 12 个，覆盖认证、用户资料、写作、文档、LanguageTool 工具等。

示例：

- `AuthControllerV1Test`
- `UserProfileControllerTest`
- `WritingControllerTest`
- `DocumentControllerTest`
- `LanguageToolServiceTest`

## 8. 文档索引

- 详细接口字段：`api-field-matrix-implemented.md`
- 后端结构：`backend-current-state.md`
- 前端结构：`frontend-current-state.md`
- 数据模型：`database-current-state.md`
- 本地运行与配置：`local-dev-runbook.md`

