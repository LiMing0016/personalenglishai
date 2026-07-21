# PEAI Unified Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将学习助手、写作、翻译、阅读、听力和口语统一到一套单层宽导航中，并保留小屏幕 72px 紧凑模式。

**Architecture:** 提取共享的应用导航模型、图标和菜单组件，由 `AppRail.vue` 与 `AssistantSidebar.vue` 复用。普通应用继续由 `AppLayout.vue` 提供导航壳层；学习助手保留现有对话数据与事件边界，只替换其全局导航部分，避免迁移对话状态。

**Tech Stack:** Vue 3、TypeScript、Vue Router、Node assert 合约测试、Vite。

## Global Constraints

- 桌面展开宽度保持在 208–218px，本实现统一使用 218px。
- 紧凑模式宽度为 72px。
- 全局应用顺序固定为：学习助手、写作、翻译、阅读、听力、口语。
- 删除“首页”入口和 PEAI 应用下拉菜单。
- 整条展开侧栏最多一个绿色实心主按钮。
- 交互行高度不低于 44px；正文不小于 14px，辅助文字不小于 12px。
- 不新增依赖，不修改后端接口、写作编辑器状态、评分链路或持久化结构。
- 保留当前工作区中已有的 `AssistantSidebar.vue`、`AssistantPage.vue` 和测试改动，不覆盖无关内容。

---

### Task 1: 共享应用导航模型与菜单

**Files:**
- Create: `web/src/components/appNavigation.ts`
- Create: `web/src/components/AppNavigationIcon.vue`
- Create: `web/src/components/AppNavigationMenu.vue`
- Create: `web/tests/unifiedAppNavigation.test.ts`

**Interfaces:**
- Produces: `APP_NAV_ITEMS: readonly AppNavItem[]`
- Produces: `isAppRouteActive(path: string, activePrefix: string): boolean`
- Produces: `<AppNavigationMenu :collapsed="collapsed" @toggle="emit('toggleRail')" />`
- Consumes: Vue Router 当前路由和 `/brand/peai-logo.png`。

- [ ] **Step 1: Write the failing navigation contract test**

```ts
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const { APP_NAV_ITEMS, isAppRouteActive } = await import('../src/components/appNavigation.ts')
const menuSource = readFileSync(new URL('../src/components/AppNavigationMenu.vue', import.meta.url), 'utf8')

assert.deepEqual(APP_NAV_ITEMS.map((item) => item.label), [
  '学习助手', '写作', '翻译', '阅读', '听力', '口语',
])
assert.equal(isAppRouteActive('/app/writing/editor', '/app/writing'), true)
assert.equal(isAppRouteActive('/app/assistant', '/app/writing'), false)
assert.ok(menuSource.includes('APP_NAV_ITEMS'))
assert.ok(menuSource.includes('AppNavigationIcon'))
assert.ok(menuSource.includes('应用导航'))
assert.ok(!menuSource.includes('首页'))
```

- [ ] **Step 2: Run the contract test and verify failure**

Run: `node --experimental-strip-types web/tests/unifiedAppNavigation.test.ts`

Expected: FAIL because `appNavigation.ts` and `AppNavigationMenu.vue` do not exist.

- [ ] **Step 3: Implement the shared model and menu**

```ts
export type AppSkillIcon = 'assistant' | 'writing' | 'translation' | 'reading' | 'listening' | 'speaking'

export interface AppNavItem {
  to: string
  activePrefix: string
  label: string
  skillIcon: AppSkillIcon
}

export const APP_NAV_ITEMS = [
  { to: '/app/assistant', activePrefix: '/app/assistant', label: '学习助手', skillIcon: 'assistant' },
  { to: '/app/writing', activePrefix: '/app/writing', label: '写作', skillIcon: 'writing' },
  { to: '/app/translation', activePrefix: '/app/translation', label: '翻译', skillIcon: 'translation' },
  { to: '/app/vocabulary', activePrefix: '/app/vocabulary', label: '阅读', skillIcon: 'reading' },
  { to: '/app/listening', activePrefix: '/app/listening', label: '听力', skillIcon: 'listening' },
  { to: '/app/speaking', activePrefix: '/app/speaking', label: '口语', skillIcon: 'speaking' },
] as const satisfies readonly AppNavItem[]

export function isAppRouteActive(path: string, activePrefix: string) {
  return path === activePrefix || path.startsWith(`${activePrefix}/`)
}
```

`AppNavigationIcon.vue` 复用当前 `AppRail.vue` 的六组线性 SVG 路径；`AppNavigationMenu.vue` 使用 `RouterLink` 渲染图标与标签，折叠时隐藏标签但保留 `title` 和 `aria-label`。品牌按钮触发 `toggle`，不触发路由跳转。

- [ ] **Step 4: Run the contract test and verify pass**

Run: `node --experimental-strip-types web/tests/unifiedAppNavigation.test.ts`

Expected: PASS and output `unified-app-navigation-ok`.

- [ ] **Step 5: Commit the shared navigation unit**

```bash
git add web/src/components/appNavigation.ts web/src/components/AppNavigationIcon.vue web/src/components/AppNavigationMenu.vue web/tests/unifiedAppNavigation.test.ts
git commit -m "feat(ui): 提取统一应用导航组件"
```

### Task 2: 将普通应用侧栏改为单层宽导航

**Files:**
- Modify: `web/src/components/AppRail.vue`
- Modify: `web/src/layouts/AppLayout.vue`
- Modify: `web/tests/appRailChrome.test.ts`

**Interfaces:**
- Consumes: `AppNavigationMenu`、Vue Router 当前路由、现有 `railCollapsed` 偏好。
- Produces: 218px 展开侧栏、72px 紧凑侧栏和写作空间局部入口。

- [ ] **Step 1: Update the AppRail contract first**

将断言改为：

```ts
assert.ok(appRailSource.includes('AppNavigationMenu'))
assert.ok(appRailSource.includes('rail-context-section'))
assert.ok(appRailSource.includes('写作空间'))
assert.ok(appRailSource.includes('to="/app/writing/mode"'))
assert.ok(appRailSource.includes('to="/app/writing/dashboard"'))
assert.ok(appRailSource.includes('flex: 0 0 218px'))
assert.ok(appRailSource.includes('flex-basis: 72px'))
assert.ok(!appRailSource.includes("label: '工具箱'"))
```

删除旧断言：折叠状态只保留浮动 logo、`position: fixed`、本地 `appNavItems` 与重复 SVG。

- [ ] **Step 2: Run the AppRail contract and verify failure**

Run: `node --experimental-strip-types web/tests/appRailChrome.test.ts`

Expected: FAIL on missing `AppNavigationMenu` or 218px width.

- [ ] **Step 3: Implement the expanded and compact rail**

`AppRail.vue` 结构：

```vue
<aside class="app-rail" :class="{ 'app-rail--collapsed': collapsed }" aria-label="应用快捷导航">
  <AppNavigationMenu :collapsed="collapsed" @toggle="emit('toggleRail')" />

  <section v-if="!collapsed && isWritingRoute" class="rail-context-section" aria-label="写作空间">
    <div class="rail-section-label">写作空间</div>
    <RouterLink to="/app/writing/mode" class="rail-primary-action">＋ 新建作文</RouterLink>
    <RouterLink to="/app/writing" class="rail-context-link">写作练习</RouterLink>
    <RouterLink to="/app/writing/dashboard" class="rail-context-link">Dashboard</RouterLink>
  </section>

  <div class="rail-spacer" aria-hidden="true"></div>
  <RouterLink to="/app/me" class="rail-profile-link" aria-label="个人中心">我</RouterLink>
</aside>
```

CSS 要点：展开 `218px`，折叠 `72px`；侧栏只保留 `rail-primary-action` 一个绿色实心按钮；上下分组使用分隔线、12px 分组标题和至少 44px 的链接高度。`AppLayout.vue` 继续在非学习助手路由渲染 `AppRail`，不修改写作数据流。

- [ ] **Step 4: Run the AppRail contract and build**

Run: `node --experimental-strip-types web/tests/appRailChrome.test.ts`

Expected: PASS and output `app-rail-chrome-ok`.

Run: `npm run build` from `web/`.

Expected: `vue-tsc` and Vite complete successfully.

- [ ] **Step 5: Commit the ordinary-app shell**

```bash
git add web/src/components/AppRail.vue web/src/layouts/AppLayout.vue web/tests/appRailChrome.test.ts
git commit -m "feat(ui): 统一普通应用宽导航"
```

### Task 3: 将学习助手侧栏对齐统一导航

**Files:**
- Modify: `web/src/components/assistant/AssistantSidebar.vue`
- Modify: `web/src/pages/app/AssistantPage.vue`
- Modify: `web/tests/assistantUnifiedSidebar.test.ts`
- Modify: `web/tests/assistantAdaptiveSidebar.test.ts`
- Modify: `web/tests/assistantSidebarCollapse.test.ts`

**Interfaces:**
- Consumes: `AppNavigationMenu`、现有 `newConversation`、文件夹、最近对话、归档和 profile 路由。
- Produces: 218px 学习助手展开侧栏、72px 紧凑侧栏；全局应用始终可发现。

- [ ] **Step 1: Update assistant navigation contracts first**

核心断言：

```ts
assert.ok(sidebarSource.includes('AppNavigationMenu'))
assert.ok(sidebarSource.includes('助手空间'))
assert.ok(sidebarSource.includes('sidebar-new-chat-button'))
assert.ok(sidebarSource.includes('flex: 0 0 218px'))
assert.ok(sidebarSource.includes('flex-basis: 72px'))
assert.ok(!sidebarSource.includes('appSwitcherOpen'))
assert.ok(!sidebarSource.includes('collapsed-home-link'))
assert.ok(!sidebarSource.includes('aria-label="返回首页"'))
assert.ok(!sidebarSource.includes('sidebar-app-switcher'))
```

保留自适应断言：`viewportWidth`、resize listener、学习画布打开时自动使用紧凑模式。

- [ ] **Step 2: Run assistant contracts and verify failure**

Run:

```bash
node --experimental-strip-types web/tests/assistantUnifiedSidebar.test.ts
node --experimental-strip-types web/tests/assistantAdaptiveSidebar.test.ts
node --experimental-strip-types web/tests/assistantSidebarCollapse.test.ts
```

Expected: at least one FAIL because the old PEAI dropdown and home link still exist.

- [ ] **Step 3: Replace the assistant app switcher with the shared menu**

展开结构按顺序渲染：

```vue
<AppNavigationMenu :collapsed="collapsed" @toggle="collapsed ? requestOpenSidebar() : $emit('closeSidebar')" />
<template v-if="collapsed">
  <button
    type="button"
    class="collapsed-sidebar-button"
    title="新聊天"
    aria-label="新聊天"
    @click="$emit('newConversation')"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M4 20h4l11-11a2.8 2.8 0 0 0-4-4L4 16v4Z" />
      <path d="m13.5 6.5 4 4" />
    </svg>
  </button>
</template>
<template v-else>
  <section class="assistant-context-section" aria-label="助手空间">
    <div class="workspace-section-label">助手空间</div>
    <button class="sidebar-new-chat-button" @click="$emit('newConversation')">新聊天</button>
  </section>
</template>
```

把现有完整的 `<section class="chat-library-section">` 放在展开模板内，保持其中搜索、文件夹、最近、归档和会话菜单的子节点原样不变。删除 `appSwitcherOpen`、相关 watch、`sidebar-app-switcher`、`workspace-nav-grid`、重复的 `appNavItems` 和“首页”链接。`AssistantPage.vue` 将 `--assistant-sidebar-width` 从 `320px` 改为 `218px`，保留 `--assistant-sidebar-collapsed-width: 72px`、composer 对齐和覆盖层逻辑。

- [ ] **Step 4: Run assistant contracts and build**

Run Task 3 Step 2 的三个命令。

Expected: all PASS.

Run: `npm run build` from `web/`.

Expected: `vue-tsc` and Vite complete successfully.

- [ ] **Step 5: Commit the assistant shell**

```bash
git add web/src/components/assistant/AssistantSidebar.vue web/src/pages/app/AssistantPage.vue web/tests/assistantUnifiedSidebar.test.ts web/tests/assistantAdaptiveSidebar.test.ts web/tests/assistantSidebarCollapse.test.ts
git commit -m "feat(ui): 统一学习助手宽导航"
```

### Task 4: Visual and regression verification

**Files:**
- Modify only if verification reveals a scoped defect in Task 1–3 files.

**Interfaces:**
- Consumes: running app at `http://127.0.0.1:3300/app/assistant` and `/app/writing`.
- Produces: verified desktop and compact navigation behavior.

- [ ] **Step 1: Run the focused contract suite**

```bash
node --experimental-strip-types web/tests/unifiedAppNavigation.test.ts
node --experimental-strip-types web/tests/appRailChrome.test.ts
node --experimental-strip-types web/tests/assistantUnifiedSidebar.test.ts
node --experimental-strip-types web/tests/assistantAdaptiveSidebar.test.ts
node --experimental-strip-types web/tests/assistantSidebarCollapse.test.ts
node --experimental-strip-types web/tests/assistantPageChrome.test.ts
```

Expected: all commands exit 0.

- [ ] **Step 2: Run the production build**

Run: `npm run build` from `web/`.

Expected: build exits 0 with no TypeScript errors.

- [ ] **Step 3: Verify the running UI in the in-app browser**

Check both `/app/assistant` and `/app/writing`:

- expanded navigation shows the same six applications in the same order;
- learning assistant shows “助手空间” and one green “新聊天” button;
- writing shows “写作空间” and one green “新建作文” link;
- no “首页” or PEAI dropdown remains;
- active item uses text weight and non-color styling;
- compact mode is 72px and retains accessible labels;
- assistant composer and writing content are not covered.

- [ ] **Step 4: Review the final diff**

Run:

```bash
git diff --check
git diff -- web/src/components/appNavigation.ts web/src/components/AppNavigationIcon.vue web/src/components/AppNavigationMenu.vue web/src/components/AppRail.vue web/src/components/assistant/AssistantSidebar.vue web/src/layouts/AppLayout.vue web/src/pages/app/AssistantPage.vue web/tests/unifiedAppNavigation.test.ts web/tests/appRailChrome.test.ts web/tests/assistantUnifiedSidebar.test.ts web/tests/assistantAdaptiveSidebar.test.ts web/tests/assistantSidebarCollapse.test.ts
```

Expected: no whitespace errors and no unrelated changes.

- [ ] **Step 5: Final integration commit if verification required fixes**

```bash
git add web/src/components/appNavigation.ts web/src/components/AppNavigationIcon.vue web/src/components/AppNavigationMenu.vue web/src/components/AppRail.vue web/src/components/assistant/AssistantSidebar.vue web/src/layouts/AppLayout.vue web/src/pages/app/AssistantPage.vue web/tests/unifiedAppNavigation.test.ts web/tests/appRailChrome.test.ts web/tests/assistantUnifiedSidebar.test.ts web/tests/assistantAdaptiveSidebar.test.ts web/tests/assistantSidebarCollapse.test.ts
git commit -m "fix(ui): 完善统一导航响应式表现"
```

Skip this commit when verification required no code changes.
