# 测试矩阵与冒烟清单（Personal English AI）

更新时间：2026-03-03

## 1) 功能-测试矩阵模板

使用方式：
- 每新增功能、修复 bug、改动接口时，至少补一行。
- `已自动化` 仅填：`是/否/部分`。
- `风险等级` 建议：`高/中/低`。

| 模块 | 页面/接口 | 关键场景 | 预期结果 | 已自动化 | 风险等级 | 最后验证日期 | 备注 |
|---|---|---|---|---|---|---|---|
| 示例：登录 | POST /api/auth/... | 正确账号密码登录 | 返回 token，前端进入 /app | 部分 | 高 | 2026-03-03 | 补充 401 场景 |

---

## 2) 你的项目首批必测用例（建议本周完成）

### 2.1 账号与鉴权

| 模块 | 页面/接口 | 关键场景 | 预期结果 | 已自动化 | 风险等级 | 最后验证日期 | 备注 |
|---|---|---|---|---|---|---|---|
| 鉴权守卫 | 前端 router guard | 无 token 访问 /app/writing | 跳转 /login 并带 redirect | 否 | 高 |  | 对应 `web/src/router/index.ts` |
| 刷新机制 | POST /api/auth/refresh | access token 过期，refresh 有效 | 静默续签成功并继续访问 | 否 | 高 |  | 避免频繁掉线 |
| 鉴权失败 | 任意受保护 API | token 无效/过期 | 返回 401，前端清 token | 部分 | 高 |  | 防止死循环重试 |

### 2.2 个人中心与学段

| 模块 | 页面/接口 | 关键场景 | 预期结果 | 已自动化 | 风险等级 | 最后验证日期 | 备注 |
|---|---|---|---|---|---|---|---|
| 个人信息读取 | GET /api/users/me/profile | 正常读取 profile | 返回 userId/email/nickname/studyStage/aiMode | 否 | 高 |  | `UserProfileController#getProfile` |
| 学段更新 | PATCH /api/users/me/profile/stage | 首次设置学段 | user_profile 新增/更新成功 | 否 | 高 |  | `UserProfileService#updateStudyStage` |
| 学段清空 | PATCH /api/users/me/profile/stage | 传空字符串 | studyStage=null, aiMode=0 | 否 | 中 |  | 边界场景 |

### 2.3 写作评测主链路

| 模块 | 页面/接口 | 关键场景 | 预期结果 | 已自动化 | 风险等级 | 最后验证日期 | 备注 |
|---|---|---|---|---|---|---|---|
| 评测输入边界 | POST /api/writing/evaluate | 词数 < 20 | 返回 ESSAY_TOO_SHORT | 部分 | 高 |  | 已有部分测试，可补齐 |
| 评测输入边界 | POST /api/writing/evaluate | 词数 > 500 | 返回 ESSAY_TOO_LONG | 部分 | 高 |  | 同上 |
| 历史列表 | GET /api/writing/history | 正常分页 | 返回 items + total | 否 | 高 |  | 核心留存入口 |
| 历史详情权限 | GET /api/writing/history/{id} | 访问他人记录 | 404 或拒绝访问 | 否 | 高 |  | 数据隔离必测 |

### 2.4 文档系统

| 模块 | 页面/接口 | 关键场景 | 预期结果 | 已自动化 | 风险等级 | 最后验证日期 | 备注 |
|---|---|---|---|---|---|---|---|
| 创建文档 | POST /api/docs | 正常创建 | 返回 docId/latestRevision | 否 | 中 |  | |
| 版本追加 | POST /api/docs/{docId}/revisions | expectedRevision 冲突 | 返回冲突/失败 | 否 | 高 |  | 并发一致性 |
| 文档隔离 | GET /api/docs/{docId} | A 读取 B 的文档 | 不可读取 | 否 | 高 |  | 多用户隔离 |

---

## 3) 发布前冒烟清单（每次发版必跑）

执行建议：
- 先跑自动化，再跑这份 10-15 分钟人工冒烟。
- 任一高风险项失败，阻断发布。

| 序号 | 场景 | 操作 | 通过标准 |
|---|---|---|---|
| 1 | 登录 | 正确账号登录 | 进入 `/app`，顶部导航可见 |
| 2 | 路由守卫 | 退出后直接访问 `/app/writing` | 跳转 `/login` |
| 3 | 学段流程 | 新账号进入业务页 | 被引导至 `/app/stage-setup` |
| 4 | 写作评测 | 在写作页提交有效英文作文 | 能返回评分/结果，不报错 |
| 5 | 历史记录 | 打开历史列表和详情 | 列表可见，详情可打开 |
| 6 | AI 面板 | 打开右侧 AI 面板并发送一次指令 | 返回结果，页面不锁死 |
| 7 | 文档保存 | 编辑后刷新页面 | 内容按预期保留 |
| 8 | 个人中心 | 打开 `/app/profile` | 个人信息正常显示 |
| 9 | 退出登录 | 个人中心点击退出 | token 清除并回到登录页 |
| 10 | 基础健康 | 调用 `/api/ping` 和 `/api/health` | 返回成功 |

---

## 4) 执行节奏（防漏测）

1. 每修一个 bug，补一个回归用例（自动化优先）。
2. 每周五维护一次测试矩阵，把本周新增接口全部补齐状态。
3. 每次发版按第 3 节冒烟清单打勾，结果写入发布记录。

---

## 5) 当前可直接执行的命令

后端自动化测试：

```powershell
cd backend
./mvnw test
```

前端当前无自动化测试脚本（`web/package.json` 未配置 `test`）。
建议后续补充：`Vitest + Vue Test Utils`。

---

## 6) Automation Progress Log

### 2026-03-03 (backend)

- Added controller tests:
  - `AuthControllerV1Test`
  - `UserProfileControllerTest`
  - `WritingControllerTest` (extended)
  - `DocumentControllerTest`
- Added service tests:
  - `UserProfileServiceTest`
  - `DocumentServiceTest`

Covered risk cases:
- Auth: login/register/refresh/logout, sms send, phone login/register, reset password, verify endpoints.
- Writing: essay length boundaries, history pagination/detail auth isolation, task ownership.
- User profile: profile read fallback, stage update and aiMode branching.
- Documents: create/get/get revision/delete, revision conflict, owner isolation.

Verification commands (passed):

```powershell
cd backend
./mvnw test
```

### CI gate setup (GitHub)

Workflow file:
- `.github/workflows/backend-test.yml`

To enforce PR gate on `main`:
1. Open repository `Settings` -> `Branches`.
2. Add/Edit branch protection rule for `main`.
3. Enable `Require status checks to pass before merging`.
4. Select required check: `backend / mvnw test`.
