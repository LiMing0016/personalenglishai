# Admin 后台体验改造 Trae 实现题目

## 背景

`/admin` 需要从当前轻量管理后台升级为主流 SaaS 风格管理员端。后台定位是运营治理优先，用来管理用户、订阅权益、作文评测、内容资产、AI 排查能力和项目资产。

设计依据：`docs/admin/index.md`。

实现边界：

- 不重写用户端 `/app`。
- 不引入新的前端框架、组件库或状态库。
- 优先复用现有 Vue 3、TypeScript、Vue Router、Axios、ECharts。
- 已有 `/admin/users`、`/admin/essays`、`/admin/prompts`、`/admin/rubrics`、`/admin/audit-logs` 必须保持可用。
- 没有真实 API 的新模块可以先做空状态或集中 mock，但不能伪装成真实数据。
- 所有权限裁剪只是前端体验优化，后端权限校验不能省略。
- 每个小题完成后都要说明实际运行的验证命令。

## 本轮已纳入实现基线

以下能力已经进入本轮实现范围，后续题目应在此基础上继续增强，而不是重新设计入口：

- `/admin` 已使用方案 A 的分组导航。
- `/admin/subscriptions` 已作为订阅用户页面入口。
- `/admin/subscription/redeem-codes` 已作为兑换码列表页面入口。
- `/admin/subscription/quota-ledger` 已作为权益流水页面入口。
- `/admin/prompt-assets` 已作为 Prompt 资产页面入口。
- `/admin/materials` 已作为素材页面入口。
- `/admin/scoring-config` 已作为评分配置页面入口。
- `/admin/agent-debug/runs` 和 `/admin/agent-debug/runs/:id` 已合并到业务管理员端。
- `/ops/agent/*` 暂时保留兼容，不作为业务管理员端主入口。

这些页面首期允许是清晰的骨架或空状态，但必须避免展示伪造的真实生产数据。后续实现应优先补真实 API、权限、审计和可操作流程。

---

## 题目 1：Admin 信息架构与布局底座

### 1A：建立可维护的 Admin 导航模型

#### Prompt

为 `/admin` 建立一套结构化导航模型，替代在 `AdminLayout.vue` 中直接写扁平菜单。

要求：

1. 新增或抽离 `adminNavigation` 配置，表达分组、菜单、路由、权限、状态和徽标。
2. 分组必须包含：
   - 总览
   - 用户运营
   - 订阅与权益
   - 作文与评测
   - 内容资产
   - AI 与 Agent
   - 审计与系统
3. 菜单至少覆盖：
   - `/admin/dashboard`
   - `/admin/users`
   - `/admin/subscriptions`
   - `/admin/subscription/redeem-codes`
   - `/admin/essays`
   - `/admin/prompts`
   - `/admin/rubrics`
   - `/admin/prompt-assets`
   - `/admin/materials`
   - `/admin/scoring-config`
   - `/admin/agent-debug/runs`
   - `/admin/model-usage`
   - `/admin/audit-logs`
   - `/admin/admin-users`
4. 每个菜单都要声明 `permission` 或明确标记为公共管理员入口。
5. 支持 `implemented | placeholder | external` 状态，未实现模块不能表现成已完成。
6. 不能在多个组件里复制同一份菜单定义。

#### 验收要求

- 导航配置有明确 TypeScript 类型。
- `AdminLayout` 从统一配置读取菜单。
- 现有已实现页面仍能被导航到。
- 未实现页面可以显示为占位入口或隐藏，但状态在配置中清楚表达。
- 搜索代码时不存在第二份重复的 admin 菜单数组。

### 1B：重构 AdminLayout 全局骨架

#### Prompt

基于 1A 的导航模型，重构 `AdminLayout.vue`，形成主流后台布局。

要求：

1. 左侧固定导航按分组展示，支持当前路由 active 状态。
2. 顶部栏显示：
   - 当前页面标题
   - 面包屑
   - 刷新入口
   - 返回主站入口
   - 当前管理员账号信息
3. `getAdminMe()` 只在合理边界调用，不要让每个子页面重复拉取同一份管理员权限。
4. 无权限菜单不展示；有权限但未实现的菜单必须进入明确占位页。
5. 宽屏下主内容应充分利用空间，窄屏下导航不能遮挡主表格。
6. 不要破坏 router guard 现有管理员校验。

#### 验收要求

- `/admin/dashboard`、`/admin/users`、`/admin/essays`、`/admin/prompts`、`/admin/rubrics`、`/admin/audit-logs` 正常渲染。
- 当前路由在侧边栏中有明确 active 状态。
- 模拟低权限账号时，对应菜单被隐藏。
- 浏览器宽度缩小到 768px 时，顶部栏、导航和主内容不重叠。
- `npm run build` 通过。

---

## 题目 2：后台视觉系统与通用页面模式

### 2A：建立 Admin 基础视觉 tokens 和状态组件样式

#### Prompt

把当前后台视觉调整为主流 SaaS 管理工具风格，并沉淀基础样式 tokens。

要求：

1. 背景使用浅灰或白色，不使用大面积渐变、装饰图案或营销式 hero。
2. 卡片圆角控制在 8px 左右。
3. 定义后台专用颜色变量：
   - background
   - surface
   - border
   - text
   - muted
   - primary
   - success
   - warning
   - danger
   - info
4. 统一按钮样式：
   - primary
   - secondary
   - ghost
   - danger
5. 统一状态标签样式：
   - success
   - warning
   - danger
   - neutral
   - info
6. 表格长文本必须有截断策略，不能撑破布局。
7. 不新增 UI 组件库。

#### 验收要求

- 后台不再呈现大屏或营销页风格。
- 卡片、按钮、输入框、状态标签视觉统一。
- 危险按钮和普通按钮有明显区别。
- 表格中长作文摘要、题目摘要、JSON 字段不会撑破页面。
- `web/src/styles/main.css` 中 admin 相关样式命名有一致前缀。

### 2B：抽象列表页和详情页的统一交互规范

#### Prompt

为 Admin 列表页和详情页建立统一页面模式，并至少改造两个已有页面证明模式可用。

要求：

1. 列表页统一结构：
   - 标题区
   - 筛选栏
   - 操作栏
   - 表格
   - 分页
   - loading / error / empty 状态
2. 详情页统一结构：
   - 标题区
   - 状态摘要
   - 标签页或分区
   - 元信息侧栏或元信息块
   - 关键操作区
3. 至少改造 `AdminUsersPage.vue` 和 `AdminEssaysPage.vue`，保持功能不退化。
4. 查询按钮、回车查询、分页切换行为要一致。
5. 错误状态不能只吞掉异常，必须有可见提示。

#### 验收要求

- 用户列表和作文列表的筛选、表格、分页视觉一致。
- loading、empty、error 三种状态都能通过代码路径或 mock 验证。
- 回车查询不会导致页面刷新。
- 分页切换不会丢失当前筛选条件。
- 改造后原有详情跳转仍可用。

---

## 题目 3：Dashboard 运营工作台

### 3A：定义 Dashboard 数据契约和集中 mock

#### Prompt

为 `/admin/dashboard` 定义稳定的数据契约，支持后续从 mock 平滑切换到真实 API。

要求：

1. 定义 Dashboard payload 类型，至少包含：
   - `summary`
   - `todoItems`
   - `quickActions`
   - `userTrend`
   - `subscriptionTrend`
   - `writingTrend`
   - `modelUsage`
   - `meta`
2. `summary` 至少包含：
   - 新增用户
   - 活跃用户
   - 订阅新增
   - 写作提交
   - 评测失败率
   - 模型成本
3. `todoItems` 至少覆盖：
   - 异常评测任务
   - 待审核内容
   - 即将到期订阅
   - 高频错误
4. mock 数据必须集中在 dashboard 模块内，不允许散落在 Vue template。
5. `meta.dataSource` 必须能区分 `mock | api`。
6. 类型设计要保留权限裁剪需要的字段。

#### 验收要求

- Dashboard 类型完整，不使用大面积 `any`。
- mock provider 返回结构稳定，空数组不返回 null。
- 页面可以显示当前数据来源是 mock 还是 api。
- mock 数据切换时间范围后能给出一致的日期口径。
- 单独阅读类型文件即可理解 Dashboard 数据形状。

### 3B：实现运营工作台页面

#### Prompt

将 `/admin/dashboard` 改造为运营工作台，而不是普通图表集合。

要求：

1. 首屏展示 6 个关键指标：
   - 新增用户
   - 活跃用户
   - 订阅新增
   - 写作提交
   - 评测失败率
   - 模型成本
2. 待处理事项必须可点击进入对应模块或明确显示“暂无实现”。
3. 快捷入口至少包含：
   - 用户搜索
   - 作文排查
   - 新建题目
   - Rubric 管理
   - Agent Debug
4. 趋势区展示用户增长、订阅转化、写作提交、模型 usage。
5. 没权限的模块不展示对应卡片或快捷入口。
6. ECharts 实例必须在组件卸载时 dispose，并支持 resize。

#### 验收要求

- Dashboard 能回答“今天要处理什么”和“去哪里处理”。
- 低权限账号看不到无权限模块入口。
- 点击快捷入口不会进入 404。
- 图表在窗口缩放后不变形。
- 没有数据时展示空状态，而不是空白图表。

---

## 题目 4：订阅与权益模块

### 4A：设计订阅与权益前端 API 边界和页面骨架

#### Prompt

新增订阅与权益模块的前端 API 边界、类型和页面骨架。

建议路由：

```text
/admin/subscriptions
/admin/subscription/redeem-codes
/admin/subscription/quota-ledger
```

要求：

1. 定义订阅用户、兑换码批次、权益流水的 TypeScript 类型。
2. 定义 `adminSubscriptionApi` 或等价 API 边界。
3. 如果后端接口未完全具备，页面必须显示明确空状态或“接口待接入”，不能伪造为真实生产数据。
4. 路由接入 `/admin`，并受权限控制。
5. 不破坏现有 `AdminSubscriptionController` 的兑换码生成接口。

#### 验收要求

- 三个订阅页面路由可以正常访问。
- 类型定义能覆盖套餐、状态、时间、额度和来源。
- 没有真实接口时页面不报错。
- 导航只在有订阅权限时显示。
- `npm run build` 通过。

### 4B：实现订阅用户、兑换码、权益流水列表交互

#### Prompt

实现订阅与权益模块的首版列表交互。

要求：

1. 订阅用户列表支持：
   - 关键词
   - 套餐
   - 状态
   - 到期时间
2. 兑换码列表支持：
   - 批次号
   - 状态
   - 创建时间
   - 已兑换 / 总数量
3. 权益流水支持：
   - 用户
   - 类型
   - 变更数量
   - 来源
   - 时间
4. 写操作入口可以先 disabled，但必须说明原因。
5. 筛选、分页、空状态遵循题目 2 的统一页面模式。

#### 验收要求

- 三个列表的筛选条件不会互相污染。
- 分页切换保留当前筛选条件。
- 空状态文案区分“暂无数据”和“接口未接入”。
- disabled 写操作有清晰原因，不让管理员误以为卡死。
- 不影响用户详情页未来接入订阅摘要。

---

## 题目 5：内容资产中心

### 5A：统一内容资产信息架构和状态口径

#### Prompt

将题库、Rubric、Prompt、素材、评分配置统一组织为“内容资产”分组。

要求：

1. 保留已有：
   - `/admin/prompts`
   - `/admin/rubrics`
2. 新增占位或骨架：
   - `/admin/prompt-assets`
   - `/admin/materials`
   - `/admin/scoring-config`
3. 内容资产统一使用状态口径：
   - `draft`
   - `active`
   - `archived`
4. 题库和 Rubric 现有编辑、启停、激活能力不能退化。
5. 新模块必须在页面上说明当前接入状态。
6. 内容资产写操作必须受权限控制。

#### 验收要求

- 内容资产分组结构清晰。
- 已有题库列表、详情、新建、编辑仍可用。
- 已有 Rubric 列表、详情、克隆、激活仍可用。
- 新增 Prompt、素材、评分配置页面不会 404。
- 状态标签样式与题目 2 保持一致。

### 5B：设计内容资产详情页和危险操作流程

#### Prompt

为内容资产建立统一详情页和危险操作规范，并优先应用到题库或 Rubric 详情。

要求：

1. 详情页必须包含：
   - 标题和状态
   - 版本或来源信息
   - 关键字段摘要
   - 编辑区
   - 审计或更新时间信息
2. 危险操作包括：
   - 停用题目
   - 激活 Rubric
   - 覆盖评分配置
   - 归档 Prompt 或素材
3. 危险操作必须二次确认。
4. 二次确认文案必须包含资源 ID 或名称。
5. 操作成功后刷新详情数据，不能只改本地显示。

#### 验收要求

- 至少一个已有内容详情页应用新的详情结构。
- 停用、激活等操作有二次确认。
- 用户取消确认后不会发请求。
- 操作成功后重新拉取详情或列表。
- 操作失败时显示错误提示，不吞异常。

---

## 题目 6：作文排查与 Agent Debug 入口

### 6A：增强作文详情的排查信息结构

#### Prompt

增强 `/admin/essays/:id` 详情页，使其更适合排查评分和异步任务问题。

要求：

1. 保留作文原文、题目、评分结果。
2. 增加或整理以下字段：
   - evaluationId
   - documentId
   - requestId
   - taskStatus
   - taskError
   - submittedAt
   - completedAt
   - user
3. 原始 JSON 默认折叠，提供复制入口。
4. 任务状态异常时显示明显 warning 或 danger 状态。
5. 预留关联 Agent run 入口；没有 run id 时显示“暂未关联”。

#### 验收要求

- 正常评测和异常评测都有可读状态。
- 原始 JSON 不会默认挤占首屏。
- 点击复制 JSON 能复制结构化内容。
- 没有 `requestId` 或 `taskStatus` 时页面不报错。
- 返回作文列表后筛选和分页状态尽量保留。

### 6B：将 Agent Debug 合并到 `/admin` 支持入口

#### Prompt

把 Agent Debug 作为 `/admin` 的支持排查入口，而不是孤立的运维后台。

建议路由：

```text
/admin/agent-debug/runs
/admin/agent-debug/runs/:id
```

要求：

1. 页面信息架构参考 `docs/ai-debug/index.md`。
2. 列表页字段至少预留：
   - run id
   - user
   - conversation
   - intent
   - workflow
   - model
   - tokens
   - latency
   - status
   - createdAt
3. 详情页至少预留：
   - RouteRequest
   - RoutingDecision
   - steps
   - Prompt snapshots
   - Model IO
   - Usage
   - OpenAI trace
4. 未接入真实 Debug Recorder 时显示明确空状态。
5. 下载或复制完整 debug JSON 必须受 `admin.agent_debug.export` 或等价权限控制。

#### 验收要求

- Agent Debug 可以从 `/admin` 导航进入。
- 未接入真实数据时不展示假 run。
- 有权限和无权限状态下操作按钮表现不同。
- 详情页不存在 run 时显示 404 或明确错误态。
- 不影响已存在 `/ops/agent/*` 页面，除非明确决定迁移并同步文档。

---

## 题目 7：权限、审计、文档和验证闭环

### 7A：补齐 Admin 权限矩阵和前后端权限闭环

#### Prompt

为 Admin 后台体验改造补齐权限矩阵，并确保前端隐藏和后端校验一致。

要求：

1. 梳理并记录现有权限：
   - `admin.users.read`
   - `admin.users.write`
   - `admin.essays.read`
   - `admin.prompts.read`
   - `admin.prompts.write`
   - `admin.rubrics.read`
   - `admin.rubrics.write`
   - `admin.audit.read`
   - `admin.subscription.write`
2. 设计新增权限：
   - `admin.dashboard.read`
   - `admin.subscription.read`
   - `admin.assets.read`
   - `admin.assets.write`
   - `admin.scoring_config.write`
   - `admin.agent_debug.read`
   - `admin.agent_debug.export`
   - `admin.model_usage.read`
3. 前端导航、按钮、危险操作按权限裁剪。
4. 后端 controller 或 service 必须继续执行权限校验。
5. 无权限访问 URL 时应得到明确 403 或跳转处理。

#### 验收要求

- 权限矩阵写入文档或代码注释可追踪位置。
- 无权限账号看不到入口。
- 直接访问无权限 URL 不能绕过后端校验。
- 权限不足时不会清除用户 token。
- 管理员登录态缓存不会导致切换账号后看到旧权限菜单。

### 7B：补齐审计日志、文档同步和最终验证

#### Prompt

为 Admin 后台体验改造建立完成标准，覆盖审计日志、文档同步和验证命令。

要求：

1. 关键操作需要写审计日志：
   - 用户状态变更
   - 管理员角色变更
   - 题库变更
   - Rubric 变更
   - Prompt / 素材 / 评分配置变更
   - 兑换码生成或停用
   - Debug JSON 下载
2. 审计日志至少包含：
   - adminUserId
   - action
   - resourceType
   - resourceId
   - targetUserId
   - beforeJson
   - afterJson
   - ip
   - userAgent
   - createdAt
3. 更新 `docs/admin/index.md`，确保实际路由、模块状态、权限名称一致。
4. 如新增 API，补充或更新 `docs/api/` 对应文档。
5. 如新增表或字段，补充或更新 `docs/data/` 对应文档。
6. 最终至少运行：
   - `npm run build`
   - `cd docs && npm run build`
7. 如果改了后端权限或审计逻辑，还要运行后端测试。

#### 验收要求

- 审计日志能查到关键管理员操作。
- 文档与实际实现没有明显矛盾。
- 前端构建通过。
- 文档站构建通过。
- 后端改动如存在，测试通过或明确说明未运行原因。
- 最终说明哪些模块是真实 API，哪些仍是 mock 或空状态。
