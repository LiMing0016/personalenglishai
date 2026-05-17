# Admin 用户 AI 用量前端 Trae 题目

## 背景

管理员需要看见用户每日消耗的 token、对应模型和 cache-hit token，用于后续节约成本和算账。后端会以 `ai_token_usage_event` 作为明细账本，前端需要提供清晰的日报和明细展示。

本题目只覆盖前端展示；Python usage 回传 Java、数据库迁移和聚合接口由后端题目承接。

设计稿：

<https://www.figma.com/design/etopP4oRFZh65MKkszGaR3>

目标入口：

```text
/admin/users/:id
/admin/model-usage?userId=:id
```

主要文件：

- `web/src/pages/admin/AdminUserDetailPage.vue`
- `web/src/pages/admin/AdminUsersPage.vue`
- `web/src/api/admin.ts`

---

## 题目 1：用户详情 AI 使用记录 Tab

难度：中等

### Prompt

请完善 `/admin/users/:id` 的“AI 使用记录”Tab，让管理员能查看该用户最近模型调用明细。字段需要覆盖模型、provider、总 token、cache-hit token、请求时间和 traceId。

明细字段：

- 请求时间。
- provider。
- model。
- featureKey。
- inputTokens。
- cachedInputTokens。
- outputTokens。
- reasoningTokens。
- totalTokens。
- traceId。

### 验收标准

- 表格展示最近 AI 使用记录。
- `cachedInputTokens` 独立列展示，不混入总 token。
- `totalTokens` 使用醒目但克制的数字展示。
- `traceId` 长文本截断但可复制。
- 空数据时显示暂无 AI 用量记录。
- 不展示完整 prompt、完整用户输入或完整模型输出。
- 字段缺失时显示 `-`。

---

## 题目 2：每日模型用量汇总卡

难度：中等

### Prompt

请在用户详情“AI 使用记录”Tab 顶部增加每日模型用量汇总区。该区域用于成本核算，重点展示某用户每天在不同模型上的 token 消耗和 cache 命中情况。

建议展示字段：

- 日期。
- provider。
- model。
- 请求次数。
- inputTokens。
- cachedInputTokens。
- outputTokens。
- reasoningTokens。
- totalTokens。
- cacheHitRate。

### 验收标准

- 支持按日期范围筛选，默认最近 7 天。
- 支持按 provider 筛选。
- 支持按 model 筛选。
- 每行展示 `日期 + provider + model` 的聚合结果。
- `cacheHitRate = cachedInputTokens / inputTokens`，无 inputTokens 时显示 `-`。
- 汇总区和明细区视觉区分清楚。
- 数字使用千分位格式。

---

## 题目 3：用户摘要抽屉 AI 摘要

难度：简单

### Prompt

请增强用户摘要抽屉中的 AI 摘要卡片，让管理员在列表页就能快速判断用户今日 token、本月 token、cache 命中和最近失败情况。

### 验收标准

- 展示今日 total tokens。
- 展示本月 total tokens。
- 展示今日 cached input tokens。
- 展示 cache hit rate。
- 展示最近失败请求数。
- 点击“查看 AI”跳转 `/admin/model-usage?userId=:id`。
- 后端暂未返回 cache 字段时，前端显示 `-`，不报错。

---

## 题目 4：模型用量独立页用户过滤

难度：中等

### Prompt

请为 `/admin/model-usage` 页面补齐 `userId` 查询参数识别和展示。用户从用户中心点击“AI”进入模型用量模块时，页面应自动按该用户过滤，并保留清晰的用户筛选状态。

### 验收标准

- URL 中存在 `userId` 时自动带入筛选。
- 页面展示当前正在查看的用户 ID。
- 可以清除用户筛选，恢复全局模型用量视图。
- 筛选不会丢失日期范围。
- 返回用户详情或用户中心的入口清晰。
- `npm run build` 通过。

---

## 题目 5：API 类型和空状态兼容

难度：简单

### Prompt

请补齐 AI 用量相关前端类型，保证后端聚合接口和最近明细接口接入后，前端不用大面积改模板。

建议类型：

- `AdminUserAiUsageRecord`
- `AdminUserAiUsageDailyModelSummary`
- `AdminUserAiUsageQuery`

### 验收标准

- 类型包含 `inputTokens`、`cachedInputTokens`、`outputTokens`、`reasoningTokens`、`totalTokens`。
- 类型包含 `provider`、`model`、`featureKey`、`traceId`。
- 聚合类型包含 `usageDate`、`requestCount`、`cacheHitRate`。
- 所有字段允许为空并在页面有兜底展示。
- `npm run build` 通过。

