# Admin 管理端排查与订阅运营 Trae 任务

## 背景

本轮管理员端开发围绕四个问题展开：

- 本地管理员账号无法登录管理端。
- 需要补充可直接登录的本地管理员账号。
- `/admin/subscriptions` 需要支持额度规则编辑。
- `/admin/subscriptions` 首屏需要补充数据库排查信息和管理员账号摘要，方便定位用户数据为 0、管理员账号缺失或权限绑定异常等问题。

要求所有任务保持现有认证和 Admin 权限链路，不新增绕过登录或绕过权限的临时接口。涉及敏感信息时，只展示账号、角色、状态和排查计数，不展示密码、密码 hash、JWT、refresh token 或数据库连接信息。

## 题目 1：管理员登录失败排查与修复

### 1A：定位管理员种子账号无法登录原因

**Prompt**

排查 `superadmin@peai.local` 使用 `Admin123!` 无法登录管理端的问题。沿着前端登录页、`POST /api/v1/auth/login`、后端 `BCryptPasswordEncoder.matches()` 和 `/api/admin/auth/me` 管理员身份校验链路定位失败点。要求输出明确根因，不允许通过前端硬编码、跳过密码校验或新增临时登录入口解决。

**验收标准**

- 能说明登录失败发生在密码校验、账号状态、邮箱验证、JWT 生成还是管理员身份校验中的哪一层。
- 如果是 seed 密码 hash 不匹配，能用测试证明 `Admin123!` 与脚本中的 hash 不匹配。
- 保持普通用户登录、注册、refresh token 和管理员 `/me` 权限校验逻辑不变。
- 不在前端增加管理员账号特判。
- 输出排查结论时包含涉及文件和接口路径。

### 1B：修复管理员种子账号登录能力

**Prompt**

修复 `backend/src/main/resources/db/seed_admin_accounts.sql`，确保本地管理员种子账号可使用文档中的初始密码登录。修复后种子脚本应能重复执行，并刷新已有管理员账号的密码 hash、邮箱验证状态、账号角色、账号状态、管理员角色绑定和基础 profile。

**验收标准**

- `superadmin@peai.local`、`supportadmin@peai.local`、`contentadmin@peai.local` 均可使用 `Admin123!` 登录。
- 三个账号保持 `email_verified = 1`、`role = admin`、`status = active`。
- 管理员角色绑定可重复执行，不重复创建脏数据。
- 后端测试覆盖 seed hash 与 `Admin123!` 匹配，以及 seed 脚本包含幂等更新逻辑。
- `docs/admin/index.md` 记录本地管理员账号、角色和初始密码。

## 题目 2：补充本地管理员账号

### 2A：新增 admin01/admin02/admin03 本地管理员账号

**Prompt**

在管理员 seed 脚本中新增三个本地开发管理员账号，账号分别为 `admin01@admin.com`、`admin02@admin.com`、`admin03@admin.com`，统一密码为 `Kiss497.*`。三个账号用于本地开发、排查和多人测试，默认具备 `super_admin` 管理员角色。

**验收标准**

- 三个账号均写入 `users`，且可重复执行 seed 脚本。
- 三个账号均为 `email_verified = 1`、`role = admin`、`status = active`。
- 三个账号均绑定 `super_admin` 管理员角色。
- 三个账号均有基础 `user_profile`，昵称分别可识别为 Admin 01、Admin 02、Admin 03。
- 不影响已有管理员账号和普通测试用户。

### 2B：验证新增管理员账号可登录并可进入管理端

**Prompt**

为新增的 `admin01/admin02/admin03@admin.com` 账号补充自动化或可重复的验证，确认 `Kiss497.*` 可匹配 seed 脚本 hash，并且登录后可以通过管理员身份校验进入管理端。

**验收标准**

- 后端测试验证 `Kiss497.*` 能匹配新增管理员账号的 bcrypt hash。
- 本地数据库执行 seed 后能查到三个新增管理员账号。
- 三个账号的 `admin_user_role` 均指向 `super_admin`。
- `/api/admin/auth/me` 对新增账号返回管理员身份和角色信息。
- 文档记录新增账号的用途和初始密码。

## 题目 3：订阅额度规则编辑区

### 3A：在订阅页展示并编辑套餐额度规则

**Prompt**

在 `/admin/subscriptions` 顶部或侧边增加“额度规则”编辑区。管理员可以查看并编辑 Free 每日额度，以及 Basic / Pro / Premium 付费套餐月度额度。页面应复用现有 Admin 订阅 API，不新增重复的套餐配置真源。

**验收标准**

- Free 显示并编辑每日额度。
- Basic / Pro / Premium 显示并编辑月度额度。
- 输入框展示当前 quota rule 的真实值。
- 保存成功后重新拉取 quota rules。
- 页面空数据或接口失败时有可理解的提示，不出现 `NaN` 或空白异常。

### 3B：补齐额度保存交互与前端校验

**Prompt**

完善额度规则保存交互。保存按钮需要具备 loading 和 disabled 状态；输入非正数、空值或无法解析为数字时，前端阻止提交并提示；保存成功后重新拉取 quota rules 和 subscription list，确保列表中的额度状态同步更新。

**验收标准**

- 保存按钮在请求中显示 loading 或等效状态，并禁止重复提交。
- 输入小于等于 0、空值或非数字时不会发起保存请求。
- 非法输入会给出明确前端提示。
- 保存成功后重新请求 quota rules。
- 保存成功后重新请求 subscription list。

## 题目 4：订阅页数据库排查与管理员账号信息

### 4A：扩展 overview 返回数据库排查信息

**Prompt**

扩展 `GET /api/admin/subscriptions/overview`，新增 `userDiagnostics` 字段，用于排查 `/admin/subscriptions` 首屏用户数据为 0 或统计口径异常的问题。字段应包含用户表行数、active 用户数、disabled 用户数、管理员账号数、普通用户数和最新用户创建时间。

**验收标准**

- `overview.userDiagnostics.databaseUserRows` 返回 `users` 表总行数。
- `overview.userDiagnostics.activeUsers` 返回 active 用户数。
- `overview.userDiagnostics.disabledUsers` 返回 disabled 用户数。
- `overview.userDiagnostics.adminUsers` 返回具备管理员角色绑定的账号数。
- `overview.userDiagnostics.regularUsers` 返回非管理员普通账号数。
- `overview.userDiagnostics.latestUserCreatedAt` 返回最新用户创建时间。

### 4B：在订阅页展示管理员账号摘要

**Prompt**

扩展 `GET /api/admin/subscriptions/overview`，新增 `adminUserPreview` 字段，并在 `/admin/subscriptions` 首屏展示最近的管理员账号摘要。该区域用于帮助排查管理员账号是否存在、角色是否绑定、状态是否可用。

**验收标准**

- `adminUserPreview` 返回最近若干管理员账号摘要。
- 每个账号包含 `id`、`nickname`、`email`、`status`、`studyStage`、`adminRoles`、`lastActiveAt`。
- 前端展示管理员昵称或邮箱、管理员角色、账号状态和学段。
- 不返回或展示密码、密码 hash、JWT、refresh token 或数据库连接信息。
- 前端契约测试覆盖 `userDiagnostics` 和 `adminUserPreview` 的消费。

## 题目 5：文档、测试和合并评估

### 5A：同步 Admin API 与后台文档

**Prompt**

补充 Admin 订阅 API 文档和 Admin 后台文档，说明 `/api/admin/subscriptions/overview` 新增数据库排查信息和管理员账号摘要，并说明这些字段仅用于后台排查，不包含敏感凭证。

**验收标准**

- `docs/api/admin-subscription.md` 包含 `userDiagnostics` response 示例和字段口径。
- `docs/api/admin-subscription.md` 包含 `adminUserPreview` response 示例和敏感信息限制。
- `docs/admin/index.md` 说明本地管理员账号和订阅页排查能力。
- 文档 `last_updated` 如有 frontmatter，应更新为实际修改日期。
- `docs` 构建通过。

### 5B：补齐自动化验证与最终验收

**Prompt**

为以上管理员端改动补齐后端和前端验证。后端覆盖 seed 账号密码、overview 排查字段、管理员账号摘要；前端覆盖额度规则编辑、非法输入阻止、保存后刷新、排查信息展示和管理员账号摘要展示。

**验收标准**

- 后端测试覆盖管理员 seed 账号密码和幂等逻辑。
- 后端测试覆盖 `overview.userDiagnostics` 和 `overview.adminUserPreview`。
- 前端测试覆盖额度规则保存前端校验和保存后刷新。
- 前端测试覆盖订阅页展示数据库排查信息和管理员账号摘要。
- `.\mvnw.cmd -q test`、`npm run build` in `web/`、`npm run build` in `docs/` 均通过。
