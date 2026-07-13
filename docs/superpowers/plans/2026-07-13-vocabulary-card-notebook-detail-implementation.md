# Vocabulary Card Notebook Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单词卡详情从狭窄右侧栏改为可阅读、可编辑、带章节导航的独立全页笔记视图。

**Architecture:** 保留现有 Vue Router、TanStack Query、卡片 DTO 和 mutation 契约，只在持久化 `card_` 路由中切换为全页详情。扩展现有安全 Markdown 解析器，使助手页面保持兼容，同时为单词卡提供禁用图片和 `<br>` 的严格渲染结果；详情组件消费统一的 `{ html, sections }`，并使用页面滚动与 `IntersectionObserver` 同步章节导航。

**Tech Stack:** Vue 3、TypeScript、Vue Router、TanStack Query、Node test runner、tsx、Playwright、Vite。

---

## Scope Check

本计划只覆盖单词卡前端详情体验。它不修改数据库、后端 API、主题 Prompt、生成模型、卡片版本格式或来源接入。五个任务形成一条连续、可独立验证的前端交付链，不需要拆成多个子项目。

## File Structure

### Create

- `web/src/components/vocabulary/VocabularyMarkdownRenderer.vue`: 只读、安全的单词卡 Markdown 展示，向父组件上报二级标题章节。
- `web/src/components/vocabulary/vocabularyCardSections.ts`: 组合固定章节与 Markdown 章节的纯函数。
- `web/tests/vocabularyMarkdownRenderer.test.ts`: 严格渲染配置、空状态和章节上报组件契约。
- `web/tests/vocabularyCardSections.test.ts`: 章节组合、空来源/历史和顺序的纯函数测试。

### Modify

- `web/src/components/assistant/markdown.ts`: 增加可配置的安全 Markdown 文档渲染和章节提取，保留 `renderAssistantMarkdown` 兼容包装。
- `web/src/components/assistant/markdown.test.ts`: 覆盖严格 `<br>`、禁用图片、二级标题和重复标题锚点。
- `web/src/composables/useVocabularyCards.ts`: 移除已不再参与详情渲染的 legacy template 查询。
- `web/src/views/VocabularyView.vue`: 区分列表、持久化卡片详情和旧关键词链接，移除列表页右侧详情栏。
- `web/src/components/vocabulary/VocabularyCardInspector.vue`: 改为全页章节式详情，保留现有编辑、生成、冲突和删除状态流。
- `web/src/components/vocabulary/VocabularyMarkdownEditor.vue`: 收敛为编辑模式专用 textarea。
- `web/tests/vocabularyDepositionWorkspace.test.ts`: 锁定持久化详情和旧关键词路由分流。
- `web/tests/vocabularyLearningPage.test.ts`: 锁定列表页不再渲染右侧详情栏。
- `web/tests/vocabularyCardInspector.test.ts`: 锁定阅读/编辑、章节、状态和现有 mutation 行为。
- `web/tests/vocabularyCoreSummary.test.ts`: 调整 Markdown editor 只用于编辑模式的契约。
- `web/tests/vocabularyDepositionFlow.spec.ts`: 覆盖真实全页详情、Markdown 阅读、编辑保存和响应式行为。
- `docs/architecture/vocabulary-deposition.md`: 更新前端详情和验证命令。

## Task 1: Extend the Shared Safe Markdown Renderer

**Files:**
- Modify: `web/src/components/assistant/markdown.ts`
- Modify: `web/src/components/assistant/markdown.test.ts`

- [ ] **Step 1: Write failing strict-rendering tests**

在 `web/src/components/assistant/markdown.test.ts` 中保留现有助手测试，并增加：

```ts
import { renderMarkdownDocument } from './markdown.ts'

test('strict markdown escapes html breaks and disables images', () => {
  const document = renderMarkdownDocument(
    'first<br>second\n\n![secret](https://example.com/secret.png)',
    { allowHtmlBreaks: false, allowImages: false },
  )

  assert.match(document.html, /&lt;br&gt;/)
  assert.doesNotMatch(document.html, /<br\/>|<img/)
  assert.match(document.html, /secret/)
})

test('markdown document extracts ordered h2 sections with unique ids', () => {
  const document = renderMarkdownDocument('## 例句\n\nA\n\n## **例句**\n\nB', {
    headingAnchors: true,
  })

  assert.deepEqual(document.sections, [
    { id: 'markdown-section-1', title: '例句', level: 2 },
    { id: 'markdown-section-2', title: '例句', level: 2 },
  ])
  assert.match(document.html, /id="markdown-section-1"/)
  assert.match(document.html, /id="markdown-section-2"/)
})
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run from `web`:

```powershell
npx tsx --test src/components/assistant/markdown.test.ts
```

Expected: FAIL because `renderMarkdownDocument` is not exported.

- [ ] **Step 3: Add the configurable document result**

在 `web/src/components/assistant/markdown.ts` 中增加并导出：

```ts
export interface MarkdownSection {
  id: string
  title: string
  level: 2
}

export interface MarkdownDocument {
  html: string
  sections: MarkdownSection[]
}

export interface MarkdownRenderOptions {
  allowImages?: boolean
  allowHtmlBreaks?: boolean
  headingAnchors?: boolean
}
```

将现有渲染流程收口到：

```ts
export function renderMarkdownDocument(
  markdown: string,
  options: MarkdownRenderOptions = {},
): MarkdownDocument
```

实现约束：

- `allowImages` 默认 `true`，为 `false` 时只渲染图片替代文本。
- `allowHtmlBreaks` 默认 `false`，仅在显式为 `true` 时把转义后的 `<br>` 恢复为 `<br/>`。
- `headingAnchors` 默认 `false`；启用时只为 `##` 生成 `markdown-section-N` 和 `sections`。
- 章节标题移除 `**` 和反引号等行内标记后作为纯文本返回。
- 所有 heading id 通过 `escapeAttribute` 写入 HTML。
- `renderAssistantMarkdown(markdown)` 改为返回 `renderMarkdownDocument(markdown, { allowImages: true, allowHtmlBreaks: true }).html`，保证现有助手行为不变。

- [ ] **Step 4: Run parser tests and verify they pass**

```powershell
npx tsx --test src/components/assistant/markdown.test.ts
```

Expected: all Markdown tests PASS, including the existing safe `<br>` assistant test and new strict-mode tests.

- [ ] **Step 5: Commit the parser boundary**

```powershell
git add web/src/components/assistant/markdown.ts web/src/components/assistant/markdown.test.ts
git commit -m "feat(ui): 增加安全 Markdown 文档渲染"
```

## Task 2: Add Vocabulary Markdown and Section Components

**Files:**
- Create: `web/src/components/vocabulary/VocabularyMarkdownRenderer.vue`
- Create: `web/src/components/vocabulary/vocabularyCardSections.ts`
- Create: `web/tests/vocabularyMarkdownRenderer.test.ts`
- Create: `web/tests/vocabularyCardSections.test.ts`

- [ ] **Step 1: Write failing component and section tests**

`web/tests/vocabularyCardSections.test.ts`：

```ts
import assert from 'node:assert/strict'
import test from 'node:test'
import { buildVocabularyCardSections } from '../src/components/vocabulary/vocabularyCardSections.ts'

test('builds core markdown source and history sections in reading order', () => {
  assert.deepEqual(buildVocabularyCardSections(
    [{ id: 'markdown-section-1', title: '例句', level: 2 }],
    true,
    true,
  ), [
    { id: 'core-information', title: '核心信息' },
    { id: 'markdown-section-1', title: '例句' },
    { id: 'card-sources', title: '来源' },
    { id: 'card-history', title: '历史' },
  ])
})

test('omits empty source and history sections', () => {
  assert.deepEqual(buildVocabularyCardSections([], false, false), [
    { id: 'core-information', title: '核心信息' },
  ])
})
```

`web/tests/vocabularyMarkdownRenderer.test.ts` 读取 Vue 源码并断言：

```ts
assert.match(source, /renderMarkdownDocument/)
assert.match(source, /allowImages:\s*false/)
assert.match(source, /allowHtmlBreaks:\s*false/)
assert.match(source, /headingAnchors:\s*true/)
assert.match(source, /v-html="document\.html"/)
assert.match(source, /暂无主题内容/)
assert.match(source, /sections-change/)
```

- [ ] **Step 2: Run tests and verify missing files fail**

```powershell
npx tsx --test tests/vocabularyMarkdownRenderer.test.ts tests/vocabularyCardSections.test.ts
```

Expected: FAIL because the renderer and section helper do not exist.

- [ ] **Step 3: Implement the pure section builder**

在 `vocabularyCardSections.ts` 导出：

```ts
import type { MarkdownSection } from '@/components/assistant/markdown'

export interface VocabularyCardSection {
  id: string
  title: string
}

export function buildVocabularyCardSections(
  markdownSections: MarkdownSection[],
  hasSources: boolean,
  hasHistory: boolean,
): VocabularyCardSection[] {
  return [
    { id: 'core-information', title: '核心信息' },
    ...markdownSections.map(({ id, title }) => ({ id, title })),
    ...(hasSources ? [{ id: 'card-sources', title: '来源' }] : []),
    ...(hasHistory ? [{ id: 'card-history', title: '历史' }] : []),
  ]
}
```

- [ ] **Step 4: Implement the strict read-only renderer**

`VocabularyMarkdownRenderer.vue`：

- prop: `markdown: string`。
- computed: `renderMarkdownDocument(markdown, strict options)`。
- `watch` 文档 sections，并以 `{ immediate: true }` 发出 `sections-change`。
- Markdown 非空时只把解析器返回的安全 HTML传给 `v-html`。
- Markdown 为空时显示“暂无主题内容”。
- 样式覆盖标题、段落、列表、引用、表格和代码块；正文使用继承字体，不模拟编辑器。
- 组件不发网络请求，不读取主题或卡片状态。

- [ ] **Step 5: Run the focused tests and build**

```powershell
npx tsx --test tests/vocabularyMarkdownRenderer.test.ts tests/vocabularyCardSections.test.ts
npm run build
```

Expected: focused tests PASS and build exits 0.

- [ ] **Step 6: Commit the vocabulary reading primitives**

```powershell
git add web/src/components/vocabulary/VocabularyMarkdownRenderer.vue web/src/components/vocabulary/vocabularyCardSections.ts web/tests/vocabularyMarkdownRenderer.test.ts web/tests/vocabularyCardSections.test.ts
git commit -m "feat(vocabulary): 增加单词卡 Markdown 阅读组件"
```

## Task 3: Split Collection and Persistent Card Routes

**Files:**
- Modify: `web/src/views/VocabularyView.vue`
- Modify: `web/src/composables/useVocabularyCards.ts`
- Modify: `web/tests/vocabularyDepositionWorkspace.test.ts`
- Modify: `web/tests/vocabularyLearningPage.test.ts`

- [ ] **Step 1: Write failing route-composition assertions**

在 `vocabularyDepositionWorkspace.test.ts` 和 `vocabularyLearningPage.test.ts` 中锁定：

```ts
assert.match(view, /isPersistentVocabularyCardRoute/)
assert.match(view, /vocabulary-card-page/)
assert.doesNotMatch(view, /<aside class="vocabulary-card-detail"/)
assert.match(view, /legacyVocabularyCardKeyword/)
assert.match(view, /cardUid\.startsWith\(['"]card_['"]\)/)
assert.doesNotMatch(view, /selectedVocabularyTemplate/)
assert.doesNotMatch(view, /:template=/)
assert.match(view, /vocabulary-card-page__skeleton/)
assert.match(view, /单词卡不存在或已被删除/)
assert.match(view, /无权查看这张单词卡/)
assert.match(view, /detailQuery\.refetch/)
```

在 composable 源码断言不再导入或创建 `listVocabularyTemplates` / `templateQuery`。

- [ ] **Step 2: Run route tests and verify they fail**

```powershell
npx tsx --test tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyLearningPage.test.ts
```

Expected: FAIL because the collection still owns a right-side inspector and template render gate.

- [ ] **Step 3: Add an explicit persistent-route discriminator**

在 `VocabularyView.vue` 使用现有 `persistentVocabularyCardUid()` 建立响应式判断：

```ts
const isPersistentVocabularyCardRoute = computed(() => (
  isVocabularyCardRoute() && Boolean(persistentVocabularyCardUid())
))
```

保留 `legacyVocabularyCardKeyword()`。非 `card_` 参数仍进入 collection，继续设置 `vocabularyFilters.keyword`，不启用详情查询。

- [ ] **Step 4: Split the template branches**

将 collection 分成两个互斥分支：

```vue
<section
  v-else-if="activeView === 'collection' && !isPersistentVocabularyCardRoute"
  class="vocabulary-page collection-page"
>
  <!-- existing capture panel and list -->
</section>

<section
  v-else-if="activeView === 'collection'"
  class="vocabulary-page vocabulary-card-page"
  aria-label="单词卡详情"
>
  <!-- loading, error, or VocabularyCardInspector -->
</section>
```

约束：

- 删除 `.vocabulary-workspace-page` 的两列详情布局和空右栏。
- 详情渲染只依赖 `detailQuery.data.value`，不再依赖 `selectedVocabularyTemplate`。
- 删除传给 inspector 的 `template` / `templates`。
- 加载状态使用稳定尺寸的 `vocabulary-card-page__skeleton`，包含标题、工具栏和正文占位，并使用 `role="status"`；不得回退为列表和空右栏。
- 增加 `vocabularyDetailErrorKind(error)`，读取 Axios `response.status`：`404` 显示“单词卡不存在或已被删除”，`403` 显示“无权查看这张单词卡”，其他错误显示“单词卡详情加载失败”。
- 404/403 状态只提供返回动作；其他错误提供“返回单词库”和 `detailQuery.refetch()`。错误状态不启动额外轮询。
- `returnToVocabularyCollection()` 和删除后的返回行为保持不变。

- [ ] **Step 5: Remove the unused template query**

从 `useVocabularyCards.ts` 删除 `listVocabularyTemplates` import、`templateQuery` 创建和返回值；从 `VocabularyView.vue` 删除对应解构和 computed。不得改变卡片、revision 或 mutation query keys。

- [ ] **Step 6: Run route tests and build**

```powershell
npx tsx --test tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyLearningPage.test.ts
npm run build
```

Expected: route tests PASS; TypeScript confirms没有残留 template props/query；build exits 0.

- [ ] **Step 7: Commit route separation**

```powershell
git add web/src/views/VocabularyView.vue web/src/composables/useVocabularyCards.ts web/tests/vocabularyDepositionWorkspace.test.ts web/tests/vocabularyLearningPage.test.ts
git commit -m "refactor(vocabulary): 分离单词库与全页详情路由"
```

## Task 4: Rebuild the Inspector as a Notebook Detail

**Files:**
- Modify: `web/src/components/vocabulary/VocabularyCardInspector.vue`
- Modify: `web/src/components/vocabulary/VocabularyMarkdownEditor.vue`
- Modify: `web/tests/vocabularyCardInspector.test.ts`
- Modify: `web/tests/vocabularyCoreSummary.test.ts`
- Test: `web/tests/vocabularyCardSections.test.ts`

- [ ] **Step 1: Write failing notebook-layout assertions**

扩展 `vocabularyCardInspector.test.ts`：

```ts
assert.match(inspector, /VocabularyMarkdownRenderer/)
assert.match(inspector, /buildVocabularyCardSections/)
assert.match(inspector, /IntersectionObserver/)
assert.match(inspector, /aria-current/)
assert.match(inspector, /core-information/)
assert.match(inspector, /card-sources/)
assert.match(inspector, /card-history/)
assert.match(inspector, /阅读/)
assert.match(inspector, /编辑/)
assert.doesNotMatch(inspector, /activeTab/)
assert.doesNotMatch(inspector, /template:\s*VocabularyTemplate/)
assert.doesNotMatch(inspector, /templates:\s*VocabularyTemplate\[\]/)
assert.match(inspector, /正在生成单词卡/)
assert.match(inspector, /正在生成新版本，当前内容可继续阅读/)
assert.match(inspector, /发现待确认的新版本/)
assert.match(inspector, /本次生成失败，当前内容未受影响/)
assert.match(inspector, /暂时没有可阅读的卡片内容/)
assert.match(inspector, /aria-label="更多单词卡操作"/)
assert.match(inspector, /aria-expanded/)
assert.match(inspector, /aria-live="polite"/)
assert.match(inspector, /saveAnnouncement/)
```

保留现有保存、重试、主题切换、冲突和软删除断言。更新 `vocabularyCoreSummary.test.ts`，要求 editor 不再接收 `readonly` 展示职责。

- [ ] **Step 2: Run focused tests and verify they fail**

```powershell
npx tsx --test tests/vocabularyCardInspector.test.ts tests/vocabularyCoreSummary.test.ts tests/vocabularyCardSections.test.ts
```

Expected: FAIL because the inspector still uses tabs and readonly textarea.

- [ ] **Step 3: Replace tabs with the notebook document structure**

在 `VocabularyCardInspector.vue`：

- 删除 `activeTab`。
- 删除未使用的 `VocabularyTemplate` 类型和 props。
- 保留 `editing`、`editMarkdown`、theme 选择、mutation、conflict 和 draft identity 状态。
- 顶部使用返回按钮、单词标题、音标/词性摘要、状态和主题名称。
- 操作区提供阅读/编辑 segmented control；编辑时显示取消/保存，阅读时显示重新生成。
- 主体使用两列：章节导航 + 居中正文。
- `core-information` 中渲染 `VocabularyCoreSummary`。
- 阅读模式渲染 `VocabularyMarkdownRenderer`；编辑模式渲染 `VocabularyMarkdownEditor`。
- 来源和历史作为正文后续 section，不再是 tabs。
- 来源/历史为空时不生成导航项，但其空状态仍可在生成或兼容场景需要时由正文提示承担。
- 原有重新生成、删除和冲突对话框保持语义与 mutation payload 不变。

新增明确的可读内容和页面状态计算：

```ts
const hasReadableRevision = computed(() => Boolean(props.card.activeRevisionUid))
const isGenerating = computed(() => (
  props.card.status === 'captured'
  || props.card.status === 'generating'
  || props.card.generationStatus === 'pending'
  || props.card.generationStatus === 'running'
))
```

状态矩阵必须逐项实现：

- `captured` / `generating` 且没有 active revision：标题保留，正文显示“正在生成单词卡...”稳定占位，不伪造 core/Markdown。
- 正在生成且存在 active revision：旧正文继续可读，顶部显示“正在生成新版本，当前内容可继续阅读”。
- `needs_review` 且有 conflict candidate：显示“发现待确认的新版本”，保留现有冲突处理入口。
- `generationOutcome=partial` 且 `warning=markdown_unavailable`：core 可读，Markdown 区显示“主题内容待完善”及重新生成动作。
- 生成失败且存在 active revision：旧正文继续可读，提示“本次生成失败，当前内容未受影响”并提供重试。
- 生成失败且没有 active revision：正文显示“暂时没有可阅读的卡片内容”及重试，不渲染空 core 摘要为成功内容。

生成状态文案集中到单一 `role="status" aria-live="polite"` 区域。区域只绑定稳定业务状态计算，不把每次轮询或加载计数写入文案，避免重复播报。增加视觉隐藏的保存通知：

```ts
const saveAnnouncement = ref('')
```

保存成功设置为“单词卡已保存”，保存失败设置为“保存失败，请重试”；模板中使用独立的 `<p class="sr-only" aria-live="polite">{{ saveAnnouncement }}</p>`。现有 toast 继续负责视觉反馈，live region 负责辅助技术，不重复创建第二份保存状态源。

- [ ] **Step 4: Wire dynamic sections and window scroll tracking**

维护：

```ts
const markdownSections = ref<MarkdownSection[]>([])
const activeSectionId = ref('core-information')
const sections = computed(() => buildVocabularyCardSections(
  markdownSections.value,
  props.card.sources.length > 0,
  Boolean(props.listVocabularyRevisions?.items.length),
))
```

实现 `scrollToSection(id)`，使用 `document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })`。在 `onMounted` / `watch` 中创建 `IntersectionObserver`，观察当前章节元素；`onBeforeUnmount` 和章节变化时断开旧 observer。使用负的顶部 `rootMargin` 适配 sticky 工具栏，并把当前项写入 `aria-current="location"`。

- [ ] **Step 5: Make the Markdown editor edit-only**

在 `VocabularyMarkdownEditor.vue`：

- 移除 `readonly` prop 和 readonly 样式。
- 保留原始字符串、不调用 `.trim()`。
- 保留 textarea、20,000 限制、字符计数和错误提示。
- label/id 支持页面中唯一实例。

保存成功仍立即采用服务端 revision Markdown并返回阅读模式；取消恢复当前卡片 Markdown。

- [ ] **Step 6: Implement responsive notebook styling**

CSS 约束：

- 桌面 `>= 1024px`：章节导航约 `180px`，sticky；正文 `minmax(0, 840px)` 居中。
- `768px` 至 `1023px`：章节导航改为顶部横向滚动，不压缩正文。
- `< 768px`：标题和操作自然换行，正文单列；阅读/编辑、保存或重新生成等主操作保持可见。
- `< 768px`：使用项目已有 `@vueuse/core` 的 `useMediaQuery('(max-width: 767px)')` 切换操作结构。增加带 `aria-expanded` 的“更多”菜单，主题选择和删除只在该菜单中呈现；菜单支持点击按钮关闭、按 `Escape` 关闭，并在切换卡片时重置。桌面端继续内联显示主题选择和删除，不同时渲染两份可聚焦控件。
- 不嵌套装饰性卡片；正文 section 使用分隔线和留白。
- 长单词、主题名、标题、来源 URL 和冲突内容使用 `min-width: 0` / `overflow-wrap: anywhere`。
- 所有按钮保持现有 6px 左右圆角和绿色主操作体系。

- [ ] **Step 7: Run focused tests and build**

```powershell
npx tsx --test tests/vocabularyCardInspector.test.ts tests/vocabularyCoreSummary.test.ts tests/vocabularyCardSections.test.ts tests/vocabularyMarkdownRenderer.test.ts
npm run build
```

Expected: all focused tests PASS and build exits 0.

- [ ] **Step 8: Commit the notebook detail**

```powershell
git add web/src/components/vocabulary/VocabularyCardInspector.vue web/src/components/vocabulary/VocabularyMarkdownEditor.vue web/tests/vocabularyCardInspector.test.ts web/tests/vocabularyCoreSummary.test.ts
git commit -m "feat(vocabulary): 实现章节式单词卡详情"
```

## Task 5: Verify the Full User Flow and Update Documentation

**Files:**
- Modify: `web/tests/vocabularyDepositionFlow.spec.ts`
- Modify: `docs/architecture/vocabulary-deposition.md`
- Verify: all files from Tasks 1-4

- [ ] **Step 1: Add failing end-to-end expectations**

在现有 API mock 基础上增加或调整 Playwright 场景：

先把测试内 `Card['status']` 扩展为 `captured | generating | ready | failed | needs_review`，并允许 `activeRevisionUid`、core 和 Markdown 在无可读版本场景为 null，以便真实构造完整生成状态矩阵。

```ts
test('opens a persisted card as a full-page readable notebook', async ({ page }) => {
  await installApiMocks(page, [makeCard({
    cardUid: 'card_ready',
    displayTerm: 'supposed',
    normalizedTerm: 'supposed',
    markdown: '## 语境精讲\n\nUsed when a claim may not be true.\n\n## 例句\n\nThe supposed expert left.',
  })])

  await page.goto('/app/vocabulary?tab=collection')
  await page.getByText('supposed', { exact: true }).click()

  await expect(page).toHaveURL(/\/app\/vocabulary\/cards\/card_ready/)
  await expect(page.getByRole('heading', { name: 'supposed' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '单词卡章节' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '语境精讲' })).toBeVisible()
  await expect(page.locator('textarea')).toHaveCount(0)
  await expect(page.getByText('批量录入')).toHaveCount(0)
})
```

再覆盖：

- 点击“编辑”后 textarea 出现，修改并保存后回到阅读模式。
- 模板接口返回 500 时有效卡片仍能显示。
- `/app/vocabulary/cards/supposed` 仍进入带 `supposed` 过滤词的单词库。
- 生成失败但已有 active revision 时旧正文仍可读且有重试入口。
- Markdown 为空时核心信息可见并显示“暂无主题内容”。
- 来源和历史章节可定位。
- 无 active revision 的 `captured` / `generating` 卡片显示生成占位，完成 mock 状态切换后 live region 更新且正文出现。
- 保存成功后 `aria-live="polite"` 区域包含“单词卡已保存”。
- 手机视口打开“更多”菜单后主题和删除可见，按 `Escape` 关闭；切换到另一张卡片后菜单保持关闭。

同时迁移当前文件中与旧设计冲突的断言：

- 把只读模式下查找 textarea 的断言改为查找渲染后的 heading/paragraph，并断言 textarea 数量为 0。
- 把“编辑 Markdown”按钮改为新的“编辑”模式控制；保存按钮使用新工具栏文案。
- 把来源/历史 `role=tab` 点击改为章节导航链接和页面 section 断言。
- 删除 `/templates` 必须请求一次的断言，改为断言详情在模板接口失败或不请求模板时仍可显示。
- 更新所有依赖旧右栏布局或旧 tab 容器的 locator，保留原有保存冲突、重新生成确认、partial warning 和软删除业务断言。

上述生成状态和手机菜单必须使用 Playwright 的点击、键盘和可见性断言验证，不以读取 Vue 源码代替行为测试。

- [ ] **Step 2: Run the Chromium flow and verify new assertions fail before final fixes**

Run from `web`：

```powershell
$env:E2E_MOCK_AUTH='1'
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium
```

Expected: new notebook-detail assertions initially expose any remaining routing, label or layout mismatches.

- [ ] **Step 3: Validate responsive behavior in the same test**

为关键阅读场景依次使用：

```ts
await page.setViewportSize({ width: 1440, height: 900 })
await page.setViewportSize({ width: 1024, height: 768 })
await page.setViewportSize({ width: 390, height: 844 })
```

每个视口断言：

- `document.documentElement.scrollWidth <= window.innerWidth`。
- 标题、章节导航、阅读/编辑和重新生成按钮可见且不重叠。
- 手机端章节导航可横向滚动，正文没有固定窄栏。

- [ ] **Step 4: Run complete frontend verification**

```powershell
npx tsx --test src/components/assistant/markdown.test.ts tests/vocabularyMarkdownRenderer.test.ts tests/vocabularyCardSections.test.ts tests/vocabularyDepositionWorkspace.test.ts tests/vocabularyLearningPage.test.ts tests/vocabularyCardInspector.test.ts tests/vocabularyCoreSummary.test.ts
npm run build
$env:E2E_MOCK_AUTH='1'
npx playwright test tests/vocabularyDepositionFlow.spec.ts --project=chromium
cd ..\docs
npm run build
```

Expected: all Node tests PASS, Web build exits 0, Chromium E2E PASS with no uncaught runtime errors, VitePress build exits 0 without dead links.

- [ ] **Step 5: Perform visual browser QA**

在本地开发服务器中打开真实 `supposed` 卡片，并在 `1440x900`、`1024x768`、`390x844` 检查：

- 正文是页面主内容，列表和批量录入不同时出现。
- Markdown 显示为文档，不显示源码标记。
- 章节导航定位和当前章节高亮正确。
- 编辑、保存、取消、主题切换、重新生成、来源、历史、冲突和删除仍可用。
- 页面没有空白正文、横向溢出、按钮覆盖或长文本撑破。

保存三种视口截图用于实现验证，但不要提交临时截图到仓库。

- [ ] **Step 6: Update architecture documentation**

修改 `docs/architecture/vocabulary-deposition.md`：

- 说明持久化 `card_` 路由使用独立全页详情。
- 说明旧关键词路由仍进入过滤后的单词库。
- 说明 core JSON 结构化展示，主题 Markdown 默认严格渲染，编辑时才显示源码。
- 把新增 Node 测试加入前端验证命令。

按照 `docs/AGENTS.md` 从 `docs` 目录运行：

```powershell
npm run build
```

Expected: VitePress build exits 0；不得通过忽略死链绕过失败。

- [ ] **Step 7: Check diff and commit verification/docs**

```powershell
git diff --check
git status --short
git add web/tests/vocabularyDepositionFlow.spec.ts docs/architecture/vocabulary-deposition.md
git commit -m "test(vocabulary): 覆盖全页单词卡详情"
```

## Final Acceptance Checklist

- [ ] `/app/vocabulary?tab=collection` 不再显示右侧详情栏。
- [ ] 持久化 `card_` 路由只显示全页详情。
- [ ] 旧关键词卡片链接继续过滤单词库。
- [ ] 有效详情不依赖 legacy template 查询。
- [ ] Markdown 默认安全渲染，严格模式不执行 `<br>` 或图片。
- [ ] 编辑模式保留原字符串、长度限制和乐观并发保存。
- [ ] 动态章节、来源、历史和当前章节高亮正确。
- [ ] 生成、部分失败、失败、冲突和删除状态不回归。
- [ ] 桌面、中等屏幕和手机无重叠或横向溢出。
- [ ] Node tests、`npm run build` 和 Chromium E2E 全部通过。
- [ ] 架构文档与实际路由、渲染和验证命令一致。
