# Admin 管理员种子账号登录修复 Trae 任务

## 背景

当前本地管理员入口使用统一登录页，管理员账号先走 `POST /api/v1/auth/login` 获取 JWT，再通过 `/api/admin/auth/me` 校验管理员角色。

截图中的账号：

- `superadmin@peai.local`
- `Admin123!`

登录失败的根因是 `backend/src/main/resources/db/seed_admin_accounts.sql` 中写死的 bcrypt hash 与 `Admin123!` 不匹配，导致 `BCryptPasswordEncoder.matches()` 返回 false，后端返回 `AUTH_LOGIN_FAILED`。

本轮目标是确保本地管理员种子账号可稳定登录，并避免后续种子脚本或文档再次漂移。

## 题目 1：修复管理员种子账号密码 hash

### Prompt

修复 `backend/src/main/resources/db/seed_admin_accounts.sql` 中管理员种子账号共用的密码 hash，使以下三个本地管理员账号都能使用 `Admin123!` 登录：

- `superadmin@peai.local`
- `supportadmin@peai.local`
- `contentadmin@peai.local`

修复时必须保持现有认证链路不变：前端仍调用 `/api/v1/auth/login`，后端仍使用 `BCryptPasswordEncoder.matches()` 校验密码，不新增管理员专用登录接口，不绕过验证码、邮箱验证、JWT 或管理员权限校验。

### 验收要求

- `Admin123!` 能匹配种子脚本中的 bcrypt hash。
- 三个管理员账号仍保持 `email_verified = 1`、`role = admin`、`status = active`。
- `superadmin@peai.local` 登录后能通过 `/api/admin/auth/me` 返回 `super_admin` 角色。
- 不修改普通用户注册、普通用户登录、手机登录和 refresh token 逻辑。
- 不在前端硬编码管理员账号或管理员登录特判。

### 验证要求

- 增加或更新后端测试，直接验证种子脚本中的 hash 可以匹配 `Admin123!`。
- 运行目标测试。
- 如本地数据库已有旧账号，执行更新后的种子脚本并确认 `superadmin@peai.local` 的 hash 已刷新。

## 题目 2：管理员种子脚本幂等化与文档固化

### Prompt

增强 `backend/src/main/resources/db/seed_admin_accounts.sql`，让脚本可以重复执行。已有本地管理员账号时，脚本应刷新密码 hash、邮箱验证状态、用户角色、账号状态、管理员角色绑定和基础 `user_profile`，而不是因为 `users.email` 唯一键冲突直接失败。

同时补充 Admin 文档，明确本地开发管理员账号、角色和初始密码，避免排查时账号口径不一致。

### 验收要求

- `users` 插入使用 `ON DUPLICATE KEY UPDATE` 刷新关键字段。
- `admin_user_role` 插入可重复执行，不重复创建角色记录。
- `user_profile` 插入可重复执行，不因已有 profile 失败。
- 脚本通过邮箱重新查询账号 id，不依赖 `LAST_INSERT_ID()` 处理已有账号。
- `docs/admin/index.md` 记录三个本地管理员账号、角色和初始密码。
- 新增测试覆盖脚本包含幂等更新逻辑，防止后续回退为一次性 seed。

### 验证要求

- 运行管理员种子账号相关单元测试。
- 运行后端测试套件。
- 手动或 SQL 验证当前数据库中三个管理员账号的 `email_verified`、`role`、`status` 和 `admin_user_role` 正确。

