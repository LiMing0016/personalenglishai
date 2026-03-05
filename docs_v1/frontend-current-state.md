# 前端现状文档（Frontend Current State）

本文档仅基于 `web/` 当前代码。

## 1. 技术与目录

- 框架：Vue 3 + TypeScript + Vite
- 路由：Vue Router（history）
- 网络层：Axios（`src/api/http.ts`）

主要目录：

- `src/router`：路由与守卫
- `src/api`：接口封装
- `src/pages`、`src/views`：页面
- `src/layouts`：布局（`AppLayout`）
- `src/components`：组件
- `src/stores`：轻量状态（stageCache）
- `src/utils`：token、toast、校验工具

## 2. 登录态与鉴权链路

### 2.1 token 策略

- access token：内存 + localStorage（key=`auth_token`）
- refresh token：后端设置 httpOnly cookie，前端不可读

### 2.2 请求拦截

- 所有请求基于 `baseURL=/api`
- 若存在 token，自动注入 `Authorization`

### 2.3 响应拦截

- 401：
  - 非 refresh 请求：尝试 `POST /v1/auth/refresh`
  - refresh 失败：清 token，清 stageCache，跳 `/login`
- 403：toast 提示“无权限访问”

## 3. 路由现状

路由文件：`src/router/index.ts`

### 3.1 公共路由

- `/`
- `/login`
- `/login-form`（重定向到 `/login`）
- `/register`
- `/check-email`
- `/verify-email`
- `/forgot-password`
- `/reset-password`

### 3.2 业务路由（`/app`）

- `/app`（Dashboard）
- `/app/stage-setup`
- `/app/writing`
- `/app/vocabulary`
- `/app/listening`
- `/app/speaking`
- `/app/me`
- `/app/ai-test`

兼容重定向：

- `/app/writing/evaluate` -> `/app/writing`
- `/me` -> `/app/me`

### 3.3 守卫逻辑

- 未登录访问业务页：跳登录并附带 redirect。
- token 过期：先静默 refresh。
- 已登录访问登录/注册页：跳 `/app`。
- 已登录且未设置学段：强制跳 `/app/stage-setup`。

## 4. 页面与核心功能

### 4.1 认证相关页面

- `LoginFormView.vue`：邮箱/手机登录流程
- `RegisterView.vue`
- `ForgotPasswordView.vue`
- `ResetPasswordView.vue`
- `CheckEmail.vue` / `VerifyEmail.vue`

### 4.2 业务页面

- `DashboardView.vue`
- `StageSetupPage.vue`
- `WritingPage.vue`（模式选择 + 编辑器入口）
- `PersonalCenterPage.vue`
- `AiCommandTestPage.vue`

### 4.3 写作工作台组件

`src/components/writing/` 包含：

- 编辑器壳：`EditorShell.vue`
- 左区编辑：`DocEditor.vue`
- 右侧工具栏/面板：`ToolRail.vue`、`ToolPanel.vue`
- AI 面板：`ChatPanel.vue`、`RewritePanel.vue`、`ExplainPanel.vue`、`TranslatePanel.vue` 等
- 选区状态：`useWritingSelectionStore.ts`

## 5. API 封装现状

- `api/auth.ts`：auth 全链路接口
- `api/user.ts`：个人资料/能力/统计/改密
- `api/writing.ts`：评测、任务、历史、收藏、chat、polish、rubric
- `api/document.ts`：文档创建（其余 doc 接口主要在后端已实现）
- `api/ai.ts`：AI command 调用

详细字段级说明见：`api-field-matrix-implemented.md`。

## 6. UI 布局与样式现状

- `AppLayout.vue` 提供统一顶栏导航。
- 写作页与个人中心支持 `immersive`（沉浸）模式。
- 全局样式在 `src/styles/main.css`，认证与 gate 页面有独立样式文件。

## 7. 现状边界

- 当前前端没有管理员导航或 `/admin` 页面。
- 当前权限判断主要是登录态，不是角色态。
