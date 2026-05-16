# Admin 用户摘要抽屉前端 Trae 题目

## 背景

用户摘要抽屉用于不离开列表上下文的快速排查。管理员点击用户行后，应在右侧看到该用户的账号、订阅、作文、AI 和审计摘要，并能快速跳转到完整详情或独立业务模块。

设计稿：

<https://www.figma.com/design/etopP4oRFZh65MKkszGaR3>

目标页面：

```text
/admin/users
```

主要文件：

- `web/src/pages/admin/AdminUsersPage.vue`
- `web/src/api/admin.ts`

---

## 题目 1：右侧摘要抽屉

难度：中等

### Prompt

请在 `/admin/users` 页面实现右侧用户摘要抽屉。点击用户表格行时打开抽屉，请求 `GET /api/admin/users/{userId}/overview`，展示该用户的轻量摘要。抽屉需要接近 Figma：顶部身份区域、标签、多个摘要小卡片和底部快捷操作。

### 验收标准

- 点击用户行打开抽屉。
- 抽屉打开后高亮当前选中行。
- 抽屉关闭后保留当前筛选、分页和表格上下文。
- 抽屉宽度在桌面端约 360-420px。
- 宽屏时抽屉在表格右侧固定展示。
- 窄屏时抽屉可以降级为页面下方或覆盖层，但不能遮挡无法关闭。
- 抽屉顶部展示头像缩写、昵称、邮箱或手机号。
- 抽屉顶部有关闭按钮。

---

## 题目 2：摘要卡片内容

难度：中等

### Prompt

请补齐用户摘要抽屉的卡片内容，保证管理员能快速确认用户是否需要进一步排查。

卡片内容：

- 订阅与额度：套餐、订阅状态、额度使用、剩余额度、是否超额。
- 最近作文：最近 3 条评测记录。
- AI 使用：今日 token、本月 token、最近失败请求。
- 审计日志：最近 2-5 条管理员操作。

### 验收标准

- `overview.account` 展示账号状态、学段、管理员角色标签。
- `overview.subscription` 展示套餐和额度进度条。
- `overview.writing.recentEvaluations` 为空时显示暂无最近作文。
- `overview.aiUsage` 为空时显示暂无 AI 使用摘要。
- `overview.audit.recentLogs` 为空时显示暂无审计记录。
- 任一区块字段缺失时只影响该区块，不导致整个抽屉崩溃。
- 不展示密码、token、验证码、完整 prompt 或完整作文正文。

---

## 题目 3：快捷跳转

难度：简单

### Prompt

请在摘要抽屉底部实现快捷跳转按钮，让管理员可以从用户摘要直接进入完整详情、作文、订阅、AI 用量和审计模块。

快捷入口：

- 完整详情：`/admin/users/:id`
- 作文：`/admin/essays?userId=:id`
- 订阅：`/admin/subscriptions?userId=:id`
- AI：`/admin/model-usage?userId=:id`
- 审计：`/admin/audit-logs?targetUserId=:id`

### 验收标准

- 优先使用后端返回的 `quickLinks`。
- 后端没有返回 `quickLinks` 时，前端可按用户 ID 生成兜底链接。
- 点击完整详情进入 `/admin/users/:id`。
- 其他按钮保留对应查询参数。
- 跳转前不清空用户列表筛选状态。

---

## 题目 4：抽屉状态处理

难度：简单

### Prompt

请完善摘要抽屉的加载态、错误态和并发点击处理。管理员连续点击多行用户时，抽屉最终展示最后一次点击的用户摘要。

### 验收标准

- 抽屉加载中显示“正在加载用户摘要”。
- 加载失败显示错误提示和重试入口。
- 连续点击多个用户时，不展示过期请求的数据。
- 请求失败不关闭抽屉。
- 关闭抽屉后清理选中行状态。
- `npm run build` 通过。

