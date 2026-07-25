# 学习助手默认首页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让所有访问 `/app` 的用户进入唯一规范地址 `/app/assistant`，并彻底移除旧仪表盘页面。

**Architecture:** 保留 `/app` 的 `AppLayout.vue` 父路由和现有 `LearningAssistant` 子路由，将空子路由改成按路由名称重定向，同时透传原始 query 与 hash。公共首页、登录流程和个人中心继续使用 `/app` 作为业务入口，由路由层统一收口；旧 `DashboardView.vue` 删除且不提供隐藏入口。

**Tech Stack:** Vue 3、TypeScript、Vue Router 4、Vite、Node.js 源码契约测试

## Global Constraints

- `/app/assistant` 必须继续作为学习助手唯一的规范地址。
- `/app` 重定向必须保留原请求的 query 和 hash。
- `/app/assistant` 的组件、路由名称 `LearningAssistant` 和 `meta: { immersive: true }` 保持不变。
- 无学段用户仍优先进入 `/app/stage-setup`。
- 不修改公共首页 `web/src/pages/Home.vue`，也不调整写作、翻译、词汇、听力、口语和个人中心路由。
- 不新增依赖、API、鉴权状态或持久化结构。
- 不覆盖或提交工作区中与本需求无关的现有改动。

---

## File Structure

- `web/src/router/index.ts`：把 `/app` 空子路由改为命名路由重定向，并保留 query/hash。
- `web/tests/assistantRouting.test.ts`：锁定默认入口、规范地址、参数透传和旧首页清理约束。
- `web/src/views/DashboardView.vue`：删除，不保留旧仪表盘实现或隐藏入口。

### Task 1: 将 `/app` 收口到学习助手规范路由

**Files:**
- Modify: `web/tests/assistantRouting.test.ts:1-35`
- Modify: `web/src/router/index.ts:81-146`

**Interfaces:**
- Consumes: Vue Router 的 `RouteRecordRedirectOption` 回调参数 `to`，使用 `to.query` 与 `to.hash`。
- Produces: `/app` 空子路由重定向对象 `{ name: 'LearningAssistant', query: to.query, hash: to.hash }`。

- [ ] **Step 1: 写出失败的默认首页路由测试**

在 `web/tests/assistantRouting.test.ts` 的 `routerSource` 定义后增加空子路由切片，并在现有助手路由断言前增加四个断言：

```ts
const defaultAppRouteBlock = routerSource.slice(
  routerSource.indexOf("path: ''", routerSource.indexOf("path: '/app'")),
  routerSource.indexOf("path: 'stage-setup'"),
)

assert.ok(
  defaultAppRouteBlock.includes("name: 'LearningAssistant'"),
  '/app should redirect to the named LearningAssistant route',
)
assert.ok(
  defaultAppRouteBlock.includes('query: to.query'),
  '/app redirect should preserve query parameters',
)
assert.ok(
  defaultAppRouteBlock.includes('hash: to.hash'),
  '/app redirect should preserve the URL hash',
)
assert.ok(
  !defaultAppRouteBlock.includes('component:'),
  '/app should not render a second homepage component',
)
assert.ok(
  routerSource.includes("path: 'stage-setup'"),
  'stage setup route should remain registered',
)
assert.ok(
  routerSource.includes("if (stageCache.value === '')"),
  'users without a study stage should still trigger the stage guard',
)
assert.ok(
  routerSource.includes("next({ path: '/app/stage-setup', query: { redirect: to.fullPath } })"),
  'stage guard should preserve the original business entry as its redirect target',
)
```

- [ ] **Step 2: 运行测试并确认它因缺少重定向而失败**

Run:

```powershell
cd web
node tests/assistantRouting.test.ts
```

Expected: FAIL，首个新增断言报告 `/app should redirect to the named LearningAssistant route`。

- [ ] **Step 3: 用最小路由改动实现规范重定向**

将 `web/src/router/index.ts` 中 `/app` 的空子路由替换为：

```ts
{
  path: '',
  redirect: (to) => ({
    name: 'LearningAssistant',
    query: to.query,
    hash: to.hash,
  }),
},
```

保持后面的学习助手路由原样：

```ts
{
  path: 'assistant',
  name: 'LearningAssistant',
  component: () => import('@/pages/app/AssistantPage.vue'),
  meta: { immersive: true },
},
```

- [ ] **Step 4: 运行路由测试并确认通过**

Run:

```powershell
cd web
node tests/assistantRouting.test.ts
```

Expected: PASS，并输出 `assistant-routing-ok`。

- [ ] **Step 5: 只提交本任务的路由与测试文件**

```powershell
cd F:\personalenglishai
git diff -- web/src/router/index.ts web/tests/assistantRouting.test.ts
git commit --only -m "feat(ui): 将学习助手设为默认首页" -- web/src/router/index.ts web/tests/assistantRouting.test.ts
```

Expected: 提交中仅包含 `web/src/router/index.ts` 和 `web/tests/assistantRouting.test.ts`；`--only` 不会带入用户已经暂存的其他文件。

### Task 2: 删除旧仪表盘并锁定清理约束

**Files:**
- Modify: `web/tests/assistantRouting.test.ts:1-55`
- Delete: `web/src/views/DashboardView.vue`

**Interfaces:**
- Consumes: Node.js `existsSync(URL)` 文件存在性检查。
- Produces: 仓库中不再存在 `web/src/views/DashboardView.vue`，路由源码不再包含 `DashboardView.vue` 或 `name: 'Dashboard'`。

- [ ] **Step 1: 写出失败的旧首页清理测试**

把 `web/tests/assistantRouting.test.ts` 的文件系统导入改为：

```ts
import { existsSync, readFileSync } from 'node:fs'
```

在 `routerSource` 定义后增加旧文件 URL，并在默认路由断言之后增加清理断言：

```ts
const legacyDashboardUrl = new URL('../src/views/DashboardView.vue', import.meta.url)

assert.ok(
  !routerSource.includes('DashboardView.vue'),
  'router should not import the legacy dashboard page',
)
assert.ok(
  !routerSource.includes("name: 'Dashboard'"),
  'router should not register the legacy Dashboard route name',
)
assert.equal(
  existsSync(legacyDashboardUrl),
  false,
  'legacy DashboardView.vue should be deleted',
)
```

- [ ] **Step 2: 运行测试并确认它因旧文件仍存在而失败**

Run:

```powershell
cd web
node tests/assistantRouting.test.ts
```

Expected: FAIL，并报告 `legacy DashboardView.vue should be deleted`；路由引用断言此时已由 Task 1 满足。

- [ ] **Step 3: 删除旧仪表盘页面**

删除且不替换以下文件：

```text
web/src/views/DashboardView.vue
```

不创建 `/app/dashboard` 兼容路由，也不把旧仪表盘内容迁移到助手页面。

- [ ] **Step 4: 运行清理测试并确认通过**

Run:

```powershell
cd web
node tests/assistantRouting.test.ts
```

Expected: PASS，并输出 `assistant-routing-ok`。

- [ ] **Step 5: 只提交本任务的测试与删除文件**

```powershell
cd F:\personalenglishai
git diff -- web/tests/assistantRouting.test.ts web/src/views/DashboardView.vue
git commit --only -m "refactor(ui): 删除旧应用首页" -- web/tests/assistantRouting.test.ts web/src/views/DashboardView.vue
```

Expected: 提交中仅包含 `web/tests/assistantRouting.test.ts` 和被删除的 `web/src/views/DashboardView.vue`；`--only` 不会带入用户已经暂存的其他文件。

### Task 3: 完成入口、构建与浏览器验收

**Files:**
- Verify: `web/tests/assistantRouting.test.ts`
- Verify: `web/tests/homeEntryExperience.test.ts`
- Verify: `web/src/router/index.ts`
- Verify: `web/src/pages/app/AssistantPage.vue`

**Interfaces:**
- Consumes: 已完成的 `/app` 重定向和现有学习助手页面。
- Produces: 路由契约、公共入口、TypeScript/Vite 构建与实际浏览器行为的验收证据。

- [ ] **Step 1: 运行相关源码契约测试**

Run:

```powershell
cd F:\personalenglishai\web
node tests/assistantRouting.test.ts
node tests/homeEntryExperience.test.ts
```

Expected: 两条命令均以退出码 0 完成，依次输出 `assistant-routing-ok` 与 `home-entry-experience-ok`。

- [ ] **Step 2: 运行前端完整构建**

Run:

```powershell
cd F:\personalenglishai\web
npm run build
```

Expected: `vue-tsc` 与 `vite build` 均成功，命令退出码为 0；若出现现有非阻断 chunk-size 警告，记录但不扩展本需求范围。

- [ ] **Step 3: 检查旧首页引用已经清零**

Run:

```powershell
cd F:\personalenglishai
rg -n "DashboardView|name: 'Dashboard'" web/src
```

Expected: 运行时源码中无匹配，`rg` 退出码为 1 仅表示搜索结果为空。

- [ ] **Step 4: 在已登录且已有学段的浏览器会话中验证 `/app`**

打开：

```text
http://127.0.0.1:3300/app?entry=home#conversation
```

Expected:

- 浏览器最终地址为 `http://127.0.0.1:3300/app/assistant?entry=home#conversation`。
- 学习助手侧栏、会话区和输入框正常显示。
- 页面中不再出现旧仪表盘的欢迎横幅、统计卡片、学习模块卡片或能力趋势占位区。

- [ ] **Step 5: 确认无学段保护契约已被路由测试覆盖**

检查 Step 1 的 `assistantRouting.test.ts` 已通过以下三个断言：`stage-setup` 路由仍注册、`stageCache.value === ''` 分支仍存在、该分支仍把 `to.fullPath` 写入 `/app/stage-setup` 的 `redirect` query。

Expected: 不需要无学段测试账号即可确认本次改动没有删除或改写现有学段守卫；如果执行环境另有无学段账号，可将实际跳转作为补充浏览器验证记录。

- [ ] **Step 6: 汇总验证结果并评估合并**

记录实际执行的测试、构建和浏览器场景；若某项因缺少测试账号未运行，要明确标注。检查本需求两个代码提交只包含计划列出的文件。由于当前分支存在其他未整理改动，本需求完成后先报告“功能具备合并条件”，不要直接把整个分支合并到 `main`。
