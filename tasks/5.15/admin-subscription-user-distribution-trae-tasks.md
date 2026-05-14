# Admin 订阅用户等级分布 Trae 任务

## 背景

`/admin/subscriptions` 已经接入订阅用户、普通用户、每日用户数据和额度规则，但首屏仍偏“额度运营”。当前需求是把该模块收敛为用户资产视角：

- 首屏只优先看用户数据。
- 展示 Free / Basic / Pro / Premium 用户分布。
- 用饼图展示不同订阅等级用户占比。
- 额度、token 消耗和规则管理继续保留，但不抢占首屏主视觉。

现有订阅等级来自 `subscription_plan.plan_code`：

- `free`
- `basic`
- `pro`
- `premium`

无订阅、Free、过期付费订阅用户都应按当前有效权益归入 `free`。

## 题目 1：订阅等级分布数据契约

### 1A：确认并固化订阅等级口径

**Prompt**

梳理当前订阅等级数据来源，确认 `subscription_plan.plan_code` 是 Admin 订阅等级分布的唯一等级口径。不要新增 enum 或硬编码独立等级表。输出 Free / Basic / Pro / Premium 的当前字段、额度周期、默认额度和排序规则，并在 Admin API 文档中明确“当前有效等级”的归类规则。

**验收要求**

- 明确 `subscription_plan` 是等级配置来源。
- 明确 `sort_order` 是展示顺序来源。
- 明确无订阅、Free、过期付费订阅用户归入 `free`。
- 不新增重复的等级枚举、配置文件或前端硬编码真源。
- API 文档说明等级分布的统计口径。

### 1B：为 overview 增加 planDistribution 响应字段

**Prompt**

扩展 `GET /api/admin/subscriptions/overview`，新增 `planDistribution` 字段。字段按当前有效等级返回每个套餐的人数、占比和排序信息。该字段必须覆盖所有 active 的 `subscription_plan`，即使某等级当前用户数为 0 也要返回。

**验收要求**

- `overview` 返回 `planDistribution` 数组。
- 每项包含 `planCode`、`planName`、`userCount`、`ratio`、`sortOrder`。
- `ratio` 按 `userCount / totalUsers * 100` 计算，保留合理小数。
- active 套餐全量返回，且按 `sortOrder ASC` 排序。
- 后端单元测试覆盖 Free / Basic / Pro / Premium 都出现在分布中。

## 题目 2：后端聚合实现

### 2A：实现当前有效等级 SQL 聚合

**Prompt**

在 Admin 订阅查询 Mapper 中实现当前有效等级聚合。付费用户只有在 `user_subscription.status = active`、`current_period_end > now` 且 `plan_code != free` 时归入对应付费套餐；其他用户归入 Free。聚合结果需要和用户列表使用一致的有效套餐判断逻辑。

**验收要求**

- SQL 使用当前时间判断有效订阅。
- 有效 Basic / Pro / Premium 用户分别归入对应套餐。
- 未订阅用户、过期用户、Free 用户归入 Free。
- 不重复统计用户；`user_subscription.user_id` 唯一约束保持兼容。
- 聚合结果不会影响现有 `GET /api/admin/subscriptions` 列表查询。

### 2B：在 Service 层合并 overview 与分布

**Prompt**

更新 `AdminSubscriptionService.getOverview()`，在保留现有用户指标的基础上合并 `planDistribution`。Service 层负责统一传入当前时间，避免 overview 主指标和等级分布使用不同时间点导致口径不一致。

**验收要求**

- `getOverview()` 仍返回原有字段，保持前端兼容。
- `planDistribution` 与 overview 使用同一个 `now`。
- 单元测试验证 mapper 收到的日期、月份和当前时间。
- 失败时不吞异常，继续交给全局异常处理。
- 不在 Controller 中拼装业务数据。

## 题目 3：Admin 前端用户数据首屏

### 3A：重排订阅页顶部概览

**Prompt**

调整 `/admin/subscriptions` 首屏结构，把顶部卡片改为用户数据优先。展示总用户、今日新增用户、订阅用户、普通用户、今日新增订阅和 7 日订阅转化率。把 token 消耗、超额用户和额度规则从首屏主视觉中降级，避免该模块看起来像额度报表。

**验收要求**

- 首屏标题和说明强调用户资产、用户分层和订阅等级。
- 顶部主要指标不再展示 `今日 Free token`、`今日付费 token`。
- 保留用户列表、每日用户数据和额度规则功能。
- 空数据时指标显示 0，不出现 `NaN` 或空白。
- 移动端下指标和分布区域不重叠。

### 3B：补齐前端类型与数据归一化

**Prompt**

更新 `web/src/api/admin.ts` 和订阅页数据处理逻辑，增加 `AdminSubscriptionPlanDistribution` 类型，并在页面中对 `planDistribution` 做数值归一化和排序。前端不能假设后端一定返回完整数字类型。

**验收要求**

- `AdminSubscriptionOverview` 包含 `planDistribution`。
- 新增 `AdminSubscriptionPlanDistribution` 类型。
- 页面把 `userCount`、`ratio`、`sortOrder` 转为 number。
- 缺失或空数组时页面显示可理解的空状态。
- 前端契约测试覆盖 `planDistribution` 字段消费。

## 题目 4：订阅等级分布饼图

### 4A：接入 ECharts PieChart

**Prompt**

在 `/admin/subscriptions` 增加“订阅等级分布”饼图，使用项目已有 ECharts 依赖，不引入新图表库。饼图数据来自 `overview.planDistribution`，展示 Free / Basic / Pro / Premium 用户数和占比。

**验收要求**

- 页面使用 ECharts `PieChart`。
- 饼图 tooltip 展示等级、人数和占比。
- legend 展示套餐名称。
- 用户数全为 0 时不初始化空图，显示空状态。
- 窗口 resize 时图表能自适应。

### 4B：图表生命周期与页面稳定性

**Prompt**

封装订阅等级分布图表的初始化、更新、resize 和销毁逻辑，避免重复初始化导致内存泄漏。页面重新拉取 overview 后，饼图要刷新到最新数据。

**验收要求**

- 图表 setup 逻辑独立成 `useSubscriptionDistributionChart` 或同等清晰边界。
- overview 数据刷新后图表重新 setOption。
- 组件卸载时 dispose 图表并移除 resize 监听。
- `npm run build` 不出现 TypeScript 错误。
- 前端测试覆盖页面包含“订阅等级分布”和 PieChart 逻辑。

## 题目 5：文档、测试和验收

### 5A：补充 API 与产品文档

**Prompt**

更新 Admin 订阅 API 文档、Admin 后台文档和订阅 Token 额度文档，说明 `/api/admin/subscriptions/overview` 新增 `planDistribution`，并说明等级分布和 Free 归类口径。

**验收要求**

- `docs/api/admin-subscription.md` 包含 `planDistribution` response 示例。
- `docs/admin/index.md` 说明订阅页首屏展示等级分布。
- `docs/product/subscription/subscription-token-quota.md` 说明 Admin overview 可查看 Free / Basic / Pro / Premium 分布。
- 文档 `last_updated` 更新为实际修改日期。
- `docs` 构建通过。

### 5B：端到端验证清单

**Prompt**

为本功能补充自动化验证：后端单元测试覆盖等级分布，前端契约测试覆盖 API 类型、页面文案和图表逻辑。完成后运行后端测试、前端构建和文档构建。

**验收要求**

- `AdminSubscriptionServiceTest` 覆盖 `planDistribution`。
- `web/tests/adminSubscriptionQuota.test.ts` 覆盖 `planDistribution`、`订阅等级分布`、`PieChart`。
- `.\mvnw.cmd -q test` 通过。
- `npm run build` in `web/` 通过。
- `npm run build` in `docs/` 通过。

