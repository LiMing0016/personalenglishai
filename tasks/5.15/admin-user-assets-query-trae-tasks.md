# Admin 用户资产查询 Trae 任务

## 背景

管理员端应承担常规用户数据查询能力，避免运营、客服和产品同学依赖直接 SQL 查询数据库。直接 SQL 只应作为工程排障手段，并受只读、审批和审计约束。

本轮目标是先把和用户有关的核心信息收敛到 `/admin/users`：

- 用户列表。
- 搜索。
- 状态。
- 学段。
- 账号角色与管理员角色。
- 订阅。
- 额度。
- 最近活跃。
- 注册时间。

要求复用现有 Admin 权限链路，不暴露密码、密码 hash、JWT、refresh token、验证码、数据库连接信息或其它敏感凭证。

## 题目 1：用户资产列表数据契约

### 1A：扩展 `/api/admin/users` 查询参数

**Prompt**

扩展 `GET /api/admin/users` 查询能力，让管理员可以按用户资产维度筛选用户。保留原有 `keyword`、`status`、`registerSource`、`adminRole`、`studyStage`、`lastActiveFrom`、`lastActiveTo`、`page`、`size` 参数，并新增账号角色、订阅套餐、订阅状态、是否超额和注册时间范围筛选。

**验收标准**

- 支持 `role=user/admin` 筛选账号角色。
- 支持 `planCode=free/basic/pro/premium` 筛选当前有效套餐。
- 支持 `subscriptionStatus=free/active/expired` 筛选订阅状态。
- 支持 `overLimit=true` 只看已超额用户。
- 支持 `createdFrom`、`createdTo` 筛选注册时间范围。
- 原有关键词、状态、学段、管理员角色、最近活跃时间筛选继续可用。
- `page` 最小为 1，`size` 最大不超过 100。

### 1B：扩展用户列表响应字段

**Prompt**

扩展 `GET /api/admin/users` 响应字段，让用户列表直接展示账号、学习、角色、订阅和额度摘要。订阅与额度口径必须复用现有订阅模块判断逻辑，不新增第二套套餐配置真源。

**验收标准**

- 每条用户包含 `id`、`email`、`phone`、`nickname`、`status`、`registerSource`、`role`、`adminRoles`。
- 每条用户包含 `studyStage`、`createdAt`、`lastActiveAt`。
- 每条用户包含 `planCode`、`planName`、`subscriptionStatus`。
- 每条用户包含 `quotaPeriod`、`tokenLimit`、`tokenUsed`、`tokenRemaining`、`overLimit`。
- 每条用户包含 `currentPeriodStart`、`currentPeriodEnd`、`usageMonth`、`usageDate`。
- 响应不包含密码、密码 hash、token、验证码或数据库连接信息。

## 题目 2：订阅与额度 SQL 聚合

### 2A：统一当前有效订阅口径

**Prompt**

在 Admin 用户查询 Mapper 中实现当前有效套餐判断。有效付费订阅必须满足 `user_subscription.status = active`、`current_period_end > now` 且 `plan_code != free`；否则归入 Free。该口径需要与 `/api/admin/subscriptions` 用户分层列表保持一致。

**验收标准**

- 有效 Basic / Pro / Premium 用户归入对应付费套餐。
- 未订阅用户归入 Free。
- Free 用户归入 Free。
- 过期付费用户归入 Free，订阅状态显示 `expired`。
- SQL 不重复统计用户。
- 不影响现有 `/api/admin/subscriptions` 查询。

### 2B：计算当前额度使用摘要

**Prompt**

在 Admin 用户列表查询中加入当前额度摘要。Free 使用当前自然日额度，付费套餐使用当前自然月额度。已用、剩余、是否超额字段应能直接用于前端展示和筛选。

**验收标准**

- Free 用户 `quotaPeriod=daily`。
- Basic / Pro / Premium 用户 `quotaPeriod=monthly`。
- `tokenLimit` 来自 `subscription_plan` 当前规则。
- `tokenUsed` 来自当前自然日或自然月聚合表。
- `tokenRemaining` 不小于 0。
- `overLimit` 可用于 SQL 筛选已超额用户。

## 题目 3：用户列表前端查询页

### 3A：补齐筛选栏

**Prompt**

改造 `/admin/users` 筛选栏，补齐用户资产查询常用条件：关键词、状态、学段、账号角色、管理员角色、套餐、订阅状态、是否超额、注册时间范围和最近活跃时间范围。查询时将空筛选项剔除，并把日期范围转换成后端可查询的时间边界。

**验收标准**

- 页面有搜索邮箱/手机号/昵称输入框。
- 页面有状态、学段、账号角色、管理员角色、套餐、订阅状态、额度状态筛选。
- 页面有注册开始/结束日期筛选。
- 页面有活跃开始/结束日期筛选。
- 点击查询时回到第一页。
- 点击重置时清空筛选并重新查询。
- 空筛选项不会作为无意义参数传给后端。

### 3B：补齐用户列表字段展示

**Prompt**

改造 `/admin/users` 表格，使其按用户资产视角展示关键字段。表格应优先服务运营和排查：一行内能看清用户身份、账号状态、学段、角色、订阅、额度、注册时间和最近活跃。

**验收标准**

- 表格展示 ID、用户昵称、邮箱或手机号。
- 表格展示状态和学段。
- 表格展示账号角色和管理员角色。
- 表格展示当前套餐和订阅状态。
- 表格展示已用额度、额度上限、剩余额度和是否超额。
- 表格展示注册时间和最近活跃时间。
- 行点击仍能进入用户详情。
- 空字段显示 `-`，不出现 `undefined`、`null` 或 `NaN`。

## 题目 4：用户详情订阅与额度摘要

### 4A：扩展用户详情接口

**Prompt**

扩展 `GET /api/admin/users/{userId}`，在用户详情响应中新增 `subscription` 字段，返回单个用户当前订阅与额度摘要。字段口径与用户列表保持一致。

**验收标准**

- `subscription` 包含 `planCode`、`planName`、`subscriptionStatus`。
- `subscription` 包含 `quotaPeriod`、`tokenLimit`、`tokenUsed`、`tokenRemaining`、`overLimit`。
- `subscription` 包含 `currentPeriodStart`、`currentPeriodEnd`、`usageMonth`、`usageDate`。
- 用户不存在时仍返回 404。
- 不改变原有账号信息、能力画像、统计摘要和最近评测字段。

### 4B：在用户详情页展示订阅与额度

**Prompt**

在 `/admin/users/:id` 用户详情页新增“订阅与额度”区域，展示当前套餐、订阅状态、额度周期、已用/上限、剩余额度、是否超额、周期开始/结束和用量口径。

**验收标准**

- 详情页展示“订阅与额度”标题。
- 展示当前套餐和订阅状态。
- 展示额度周期、已用额度、额度上限、剩余额度。
- 展示是否超额。
- 展示订阅周期开始和结束时间。
- 展示当前用量口径日期或月份。
- 空字段显示 `-`，额度数字格式化展示。

## 题目 5：文档、测试和验收

### 5A：补充 Admin 用户 API 文档

**Prompt**

新增或更新 Admin 用户 API 文档，说明 `/api/admin/users` 和 `/api/admin/users/{userId}` 的用户资产查询字段、筛选参数和敏感信息限制。同步更新 API 索引和 Admin 后台文档。

**验收标准**

- `docs/api/admin-users.md` 说明列表查询参数。
- `docs/api/admin-users.md` 说明列表响应字段。
- `docs/api/admin-users.md` 说明详情响应中的 `subscription` 字段。
- `docs/api/index.md` 链接 Admin 用户 API。
- `docs/admin/index.md` 说明 `/admin/users` 支持用户资产查询。
- 文档声明不返回密码、密码 hash、JWT、refresh token 或数据库连接信息。

### 5B：补齐自动化验证

**Prompt**

为 Admin 用户资产查询补充自动化验证。后端测试覆盖 Service 层传参、订阅与额度字段归一化、管理员角色 CSV 转数组；前端测试覆盖筛选栏、列表字段、详情页订阅与额度区域和 API 类型字段。

**验收标准**

- 后端测试覆盖新增筛选参数传入 Mapper。
- 后端测试覆盖列表响应包含订阅和额度字段。
- 后端测试覆盖 `adminRolesCsv` 被转换为 `adminRoles` 数组。
- 前端测试覆盖页面包含套餐、订阅、额度、注册时间、最近活跃等展示。
- 前端测试覆盖详情页“订阅与额度”区域。
- `.\mvnw.cmd -q test` 通过。
- `npm run build` in `web/` 通过。
- `npm run build` in `docs/` 通过。
