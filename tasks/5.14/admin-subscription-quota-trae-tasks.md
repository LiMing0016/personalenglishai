# Admin 订阅用户与 Token 额度管理 Trae 任务

## 背景

当前 `/admin/subscriptions` 还是页面骨架，管理员不能查看订阅用户、未订阅用户，也不能调整 Free 与付费套餐的 token 额度规则。第一版实现采用“规则配置优先”：

- 订阅有效的付费用户按套餐自然月额度控制。
- 未订阅或订阅过期用户按 Free 自然日额度控制。
- 不做单个用户特殊额度，不直接修改已用 token。
- 额度规则修改立即影响后续 quota check，并写入审计日志。

## 题目 1：额度规则数据模型

### 1A：为套餐增加日额度字段

**Prompt**

为 `subscription_plan` 增加 `daily_token_limit` 字段。Free 档位使用每日额度，Basic / Pro / Premium 继续使用 `monthly_token_limit`。更新实体、Mapper、初始化脚本和迁移脚本，确保新库和已有库都能获得 Free 每日额度。

**验收要求**

- `SubscriptionPlan` 包含 `dailyTokenLimit` 字段。
- `subscription_plan` 初始化数据包含 Free 的 `daily_token_limit`。
- 迁移脚本能为已有数据库添加字段并回填 Free 默认值。
- 付费套餐月额度字段保持兼容，不破坏现有订阅购买和兑换码逻辑。

### 1B：新增每日 token 聚合能力

**Prompt**

新增 `user_ai_token_usage_daily` 日聚合表，并扩展 `AiTokenUsageMapper`，支持按 `userId + usageDate` 幂等累计 token 使用量。事件明细仍保存在 `ai_token_usage_event`。

**验收要求**

- 新增日聚合表迁移和 fresh schema。
- `AiTokenUsageMapper` 支持 `upsertDailyUsage` 和 `selectDailyTokenUsed`。
- 同一个 `usage_event_id` 重复上报不会重复累计日额度。
- 日聚合与月聚合可以并存，互不覆盖。

## 题目 2：Admin 订阅 API

### 2A：实现订阅用户列表 API

**Prompt**

实现 `GET /api/admin/subscriptions`。接口返回用户、订阅状态、套餐、额度周期、已用、剩余、开始时间、到期时间，并支持关键词、套餐、状态、到期时间、分页筛选。

**验收要求**

- 返回已订阅用户和未订阅用户。
- 未订阅或过期用户显示 `free` 和 `daily` 额度周期。
- 有效付费用户显示对应套餐和 `monthly` 额度周期。
- 支持 `keyword`、`planCode`、`subscriptionStatus`、`expiresFrom`、`expiresTo`、`page`、`size` 参数。
- 后端强制校验 `admin.subscription.read` 权限。

### 2B：实现额度规则 API

**Prompt**

实现 `GET /api/admin/subscription/quota-rules` 和 `PUT /api/admin/subscription/quota-rules/{planCode}`。管理员可以查看 Free/Basic/Pro/Premium 的额度规则，并修改 Free 每日额度或付费套餐月额度。

**验收要求**

- `GET` 返回四个套餐的额度周期、日额度、月额度、是否启用。
- `PUT free` 修改 `daily_token_limit`。
- `PUT basic/pro/premium` 修改 `monthly_token_limit`。
- 无效套餐、空额度、非正数额度返回业务校验错误。
- 修改接口强制校验 `admin.subscription.write` 权限。

## 题目 3：额度判断逻辑

### 3A：更新 quota check

**Prompt**

更新 `SubscriptionService.assertAiTokenQuotaAvailable`。有效付费订阅使用当月聚合额度；无订阅、过期订阅或 Free 使用当天聚合额度。

**验收要求**

- Free 用户超过每日额度后被拦截。
- Free 用户次日自然恢复可用额度。
- 付费用户仍按自然月额度拦截。
- 过期付费订阅用户自动回落 Free 每日额度。
- 错误码仍为 `SUBSCRIPTION_TOKEN_QUOTA_EXCEEDED`，前端兼容不变。

### 3B：更新 usage 记录

**Prompt**

更新 `recordUsage`。成功插入事件后，根据用户当前额度周期写入日聚合或月聚合；事件明细始终保留。

**验收要求**

- Free 用户 usage 写入 `user_ai_token_usage_daily`。
- 有效付费用户 usage 写入 `user_ai_token_usage_monthly`。
- 重复事件不会重复聚合。
- token 计算口径仍使用 `totalTokens`，没有 provider 总数时 fallback 为 input + output + reasoning。

## 题目 4：Admin 前端订阅用户页

### 4A：接入真实订阅列表

**Prompt**

替换 `/admin/subscriptions` 骨架页，接入 `adminApi.listSubscriptions`。页面提供关键词、状态、套餐、到期时间筛选、分页、加载态、错误态和空状态。

**验收要求**

- 页面不再显示“接口待接入”或禁用筛选控件。
- 表格展示用户、套餐、订阅状态、额度周期、已用/剩余、开始时间、到期时间。
- 未订阅用户显示 Free / 每日额度。
- 查询按钮和分页会传递正确参数。
- API 失败时显示可恢复的错误提示。

### 4B：增加额度规则编辑区

**Prompt**

在 `/admin/subscriptions` 顶部或侧边增加“额度规则”编辑区。管理员可以编辑 Free 每日额度和付费套餐月额度，保存成功后刷新规则和列表。

**验收要求**

- Free 显示并编辑每日额度。
- Basic / Pro / Premium 显示并编辑月度额度。
- 保存按钮具备 loading/disabled 状态。
- 保存成功后重新拉取 quota rules 和 subscription list。
- 输入非正数时前端阻止提交并提示。

## 题目 5：权限、审计和文档

### 5A：权限与审计

**Prompt**

补充 `admin.subscription.read` 权限用于订阅列表读取，继续使用 `admin.subscription.write` 管理额度规则和兑换码。额度规则修改写入审计日志。

**验收要求**

- 超级管理员具备读写权限。
- 支持管理员至少具备订阅读取权限。
- `/admin/subscriptions` 导航使用 read 权限。
- 额度规则修改审计包含 planCode、修改前额度和修改后额度。

### 5B：文档与验证

**Prompt**

更新订阅额度文档和 Admin API 文档，说明 Free 每日额度、付费月额度、日/月聚合表、订阅用户列表接口和额度规则接口。

**验收要求**

- `docs/product/subscription/subscription-token-quota.md` 说明最新额度策略。
- API 文档包含 `GET /api/admin/subscriptions` 和 quota rules 接口。
- 后端测试覆盖 Free 每日额度、付费月额度、过期回落 Free、规则修改。
- 前端构建通过，文档构建通过。

