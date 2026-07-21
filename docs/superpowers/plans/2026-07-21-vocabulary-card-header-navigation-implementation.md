# 单词卡头部发音与连续浏览 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有单词卡详情页实现产品化发音头部，以及按单词库筛选和排序上下文跨页浏览上一张、下一张卡片。

**Architecture:** 路由 query 保存单词库上下文，纯函数负责解析、序列化和相邻卡片计算，`VocabularyView.vue` 负责跨页加载与路由切换。独立发音 composable 负责真实音频、浏览器语音降级和资源清理，详情组件只负责展示播放状态与触发交互。

**Tech Stack:** Vue 3、TypeScript、Vue Router、TanStack Query、lucide-vue-next、Node test runner、Vite

## Global Constraints

- 只实施设计文档的阶段一，不增加 Kokoro、Java API、数据库或部署配置。
- 复用 `VocabularyCoreContent.phonetics[].audioUrl`，没有真实音频时使用 Web Speech API。
- 不新增前端运行时依赖、Pinia store、localStorage 或 sessionStorage。
- 前后导航只在详情路由携带有效单词库上下文时显示。
- 切换卡片和组件卸载时必须停止旧音频，并忽略旧播放事件。
- 编辑模式和卡片 mutation 进行中禁止切换卡片。
- 返回单词库时恢复关键词、状态、来源、排序、页码和每页数量。
- 正式图标全部使用 `lucide-vue-next`，不使用字符箭头或音乐符号。

---

## File Structure

- Create `web/src/features/vocabulary/vocabularyCardNavigation.ts`: 路由上下文解析、序列化、位置和相邻卡片纯函数。
- Create `web/src/composables/useVocabularyPronunciation.ts`: 音频播放、Web Speech 降级、播放状态和清理。
- Modify `web/src/components/vocabulary/VocabularyCoreSummary.vue`: 把音标展示升级为可播放按钮并向父组件发送音标。
- Modify `web/src/components/vocabulary/VocabularyCardInspector.vue`: 产品化标题发音、播放状态和连续浏览控制。
- Modify `web/src/views/VocabularyView.vue`: 同步路由筛选上下文、跨页加载相邻卡片并向详情组件传递导航模型。
- Create `web/tests/vocabularyCardNavigation.test.ts`: 路由和跨页纯逻辑测试。
- Create `web/tests/vocabularyPronunciation.test.ts`: 音频优先、语音降级和清理测试。
- Modify `web/tests/vocabularyCoreSummary.test.ts`: 音标按钮和事件契约测试。
- Modify `web/tests/vocabularyCardInspector.test.ts`: 头部与导航 UI 契约测试。
- Modify `web/tests/vocabularyDepositionWorkspace.test.ts`: 路由上下文保存和恢复测试。
- Modify `docs/architecture/vocabulary-deposition.md`: 记录阶段一播放和连续浏览数据流。

### Task 1: 路由上下文和相邻卡片纯函数

**Files:**
- Create: `web/src/features/vocabulary/vocabularyCardNavigation.ts`
- Create: `web/tests/vocabularyCardNavigation.test.ts`

**Interfaces:**
- Produces: `parseVocabularyNavigationQuery(query): VocabularyCardFilters | null`
- Produces: `buildVocabularyNavigationQuery(filters): Record<string, string>`
- Produces: `resolveVocabularyCardSequence(currentPage, cardUid, previousPage?, nextPage?): VocabularyCardSequence | null`

- [ ] **Step 1: Write the failing tests**

覆盖默认值、非法页码、全部筛选字段往返、页内相邻项、跨页相邻项、第一张、最后一张和当前卡片不在上下文页的情况。

```ts
assert.deepEqual(parseVocabularyNavigationQuery({
  vc: '1', keyword: 'rece', status: 'ready', source: 'manual', sort: 'az', page: '2', size: '20',
}), {
  keyword: 'rece', status: 'ready', sourceType: 'manual', sort: 'az', page: 2, size: 20,
})
assert.equal(parseVocabularyNavigationQuery({}), null)
assert.equal(resolveVocabularyCardSequence(page, 'card_2')?.position, 2)
```

- [ ] **Step 2: Run the test and verify RED**

Run: `cd web && npx tsx --test tests/vocabularyCardNavigation.test.ts`

Expected: FAIL because `vocabularyCardNavigation.ts` does not exist.

- [ ] **Step 3: Implement the pure helpers**

Use a `vc=1` marker to distinguish navigation context from unrelated route query values. Clamp `page >= 1` and `1 <= size <= 100`; reject unsupported status and sort values instead of guessing them.

```ts
export interface VocabularyCardSequenceTarget {
  cardUid: string
  displayTerm: string
}

export interface VocabularyCardSequence {
  previous: VocabularyCardSequenceTarget | null
  next: VocabularyCardSequenceTarget | null
  position: number
  total: number
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd web && npx tsx --test tests/vocabularyCardNavigation.test.ts`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add web/src/features/vocabulary/vocabularyCardNavigation.ts web/tests/vocabularyCardNavigation.test.ts
git commit -m "feat(ui): 增加单词卡连续浏览上下文"
```

### Task 2: 发音播放控制器

**Files:**
- Create: `web/src/composables/useVocabularyPronunciation.ts`
- Create: `web/tests/vocabularyPronunciation.test.ts`

**Interfaces:**
- Produces: `useVocabularyPronunciation(options?)`
- Produces: `play({ term, language, audioUrl }): Promise<'audio' | 'speech' | 'failed'>`
- Produces: reactive `state`, `activeLanguage`, `message` and synchronous `stop()`.

- [ ] **Step 1: Write the failing tests**

Inject small audio and speech adapters so Node tests verify real orchestration without browser globals.

```ts
const playback = useVocabularyPronunciation({ createAudio, speech })
assert.equal(await playback.play({ term: 'receive', language: 'en-GB', audioUrl: '/receive.mp3' }), 'audio')
assert.equal(await playback.play({ term: 'receive', language: 'en-US', audioUrl: null }), 'speech')
playback.stop()
assert.equal(playback.state.value, 'idle')
```

Cover audio rejection falling back to speech, a second play stopping the first source, stale completion not resetting the new state, and unmount cleanup through the exported stop behavior.

- [ ] **Step 2: Run the test and verify RED**

Run: `cd web && npx tsx --test tests/vocabularyPronunciation.test.ts`

Expected: FAIL because the composable does not exist.

- [ ] **Step 3: Implement minimal playback orchestration**

Create one `HTMLAudioElement` at a time. Set `onplaying`, `onended` and `onerror` before `play()`. On failure, call `speechSynthesis.cancel()`, create `SpeechSynthesisUtterance`, set `lang` and `rate = 0.9`, then speak. Guard callbacks with a monotonically increasing request ID.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd web && npx tsx --test tests/vocabularyPronunciation.test.ts`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add web/src/composables/useVocabularyPronunciation.ts web/tests/vocabularyPronunciation.test.ts
git commit -m "feat(ui): 增加单词发音播放控制器"
```

### Task 3: 产品化详情头部和音标按钮

**Files:**
- Modify: `web/src/components/vocabulary/VocabularyCoreSummary.vue`
- Modify: `web/src/components/vocabulary/VocabularyCardInspector.vue`
- Modify: `web/tests/vocabularyCoreSummary.test.ts`
- Modify: `web/tests/vocabularyCardInspector.test.ts`

**Interfaces:**
- Consumes: `useVocabularyPronunciation()` from Task 2.
- Consumes: `VocabularyCardSequence` from Task 1.
- Produces: inspector prop `navigation?: VocabularyCardSequence | null`.
- Produces: inspector emit `navigate: ['previous' | 'next']`.
- Produces: core summary emit `pronounce: [VocabularyCoreContent['phonetics'][number]]`.

- [ ] **Step 1: Write failing component contract tests**

Require `Volume2`, `LoaderCircle`, `ChevronLeft` and `ChevronRight` imports, a clickable word title, accessible phonetic buttons, stable playback status, navigation position, adjacent word labels and disabled navigation while editing.

```ts
assert.match(inspector, /aria-label=`播放.*默认发音`/)
assert.match(inspector, /@navigate|emit\('navigate'/)
assert.match(coreSummary, /emit\('pronounce', phonetic\)/)
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `cd web && npx tsx --test tests/vocabularyCoreSummary.test.ts tests/vocabularyCardInspector.test.ts`

Expected: FAIL on the missing playback and navigation contracts.

- [ ] **Step 3: Implement the header and core phonetic controls**

The title button and speaker button call the same default pronunciation method. `VocabularyCoreSummary` emits the selected phonetic. The toolbar navigation renders on the right, uses fixed-size icon buttons, and displays adjacent terms only above `1024px`. Playback feedback uses an `aria-live="polite"` region and does not change layout height.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run: `cd web && npx tsx --test tests/vocabularyCoreSummary.test.ts tests/vocabularyCardInspector.test.ts`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add web/src/components/vocabulary/VocabularyCoreSummary.vue web/src/components/vocabulary/VocabularyCardInspector.vue web/tests/vocabularyCoreSummary.test.ts web/tests/vocabularyCardInspector.test.ts
git commit -m "feat(ui): 打磨单词卡发音头部"
```

### Task 4: 详情路由跨页接线

**Files:**
- Modify: `web/src/views/VocabularyView.vue`
- Modify: `web/tests/vocabularyDepositionWorkspace.test.ts`

**Interfaces:**
- Consumes: Task 1 route helpers and `VocabularyCardSequence`.
- Produces: `navigateVocabularyCard(direction)` and route-preserving `selectVocabularyCard` / `returnToVocabularyCollection`.

- [ ] **Step 1: Write failing route integration tests**

Verify list selection serializes the current filters, detail route restores filters, back preserves them, navigation passes the computed model, and crossing a page calls `listVocabularyCards` with the adjacent page before routing.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `cd web && npx tsx --test tests/vocabularyDepositionWorkspace.test.ts`

Expected: FAIL because the view does not preserve navigation context.

- [ ] **Step 3: Implement route and cross-page orchestration**

Initialize detail filters from `parseVocabularyNavigationQuery(route.query)`. When the current page boundary has another page, load only that adjacent page on navigation, update `vocabularyFilters`, and route to its first or last item with the new page query. Keep direct links without `vc=1` free of sequence UI.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `cd web && npx tsx --test tests/vocabularyDepositionWorkspace.test.ts`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add web/src/views/VocabularyView.vue web/tests/vocabularyDepositionWorkspace.test.ts
git commit -m "feat(ui): 接通单词卡跨页浏览"
```

### Task 5: 文档、完整验证和浏览器验收

**Files:**
- Modify: `docs/architecture/vocabulary-deposition.md`
- Modify: `docs/superpowers/plans/2026-07-21-vocabulary-card-header-navigation-implementation.md`

**Interfaces:**
- Consumes: Tasks 1-4 completed behavior.
- Produces: 可复现的验证记录和阶段二实施边界。

- [ ] **Step 1: Update architecture documentation**

Document `vc=1` route context, cross-page navigation, `audioUrl -> Web Speech` stage-one fallback and that Kokoro remains stage two.

- [ ] **Step 2: Run all vocabulary tests**

Run: `cd web && npx tsx --test "tests/vocabulary*.test.ts"`

Expected: all vocabulary tests pass with no unhandled rejection.

- [ ] **Step 3: Run the production build**

Run: `cd web && npm run build`

Expected: `vue-tsc` and Vite complete successfully.

- [ ] **Step 4: Verify in the in-app browser**

At `1440x900`, `1024x768` and `390x844`, verify title audio, phonetic audio, loading/playing feedback, same-page navigation, cross-page navigation, back context, first/last disabled controls, sticky toolbar, long words, and no horizontal overflow. Compare the running page against the approved product-header prototype at the same viewport before accepting the result.

- [ ] **Step 5: Mark the plan complete and commit documentation**

```powershell
git add docs/architecture/vocabulary-deposition.md docs/superpowers/plans/2026-07-21-vocabulary-card-header-navigation-implementation.md
git commit -m "docs(ui): 记录单词卡连续浏览实现"
```

## Stage Two Handoff

Kokoro 免费音频补全必须使用单独实施计划，覆盖 Python 模型依赖和 smoke test、Java 鉴权与缓存接口、文件存储、超时降级、部署变量和真实 GPU 验证。阶段二开始前重新确认固定的 Kokoro 模型版本、voice 版本、权重许可和音频缓存目录，不在阶段一中预埋未使用接口。
