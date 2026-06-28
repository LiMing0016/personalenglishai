# Translation Learning IDE Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the PDF translation workspace into a VSCode-style learning IDE shell with workspace tabs, a resource Explorer, and a context-aware right panel that switches between Agent and anchored-note workbench modes.

**Architecture:** Keep the existing `TranslationWorkspacePage.vue` as the integration surface for phase 1, but introduce small focused local components/types inside the same module before extracting files later. Preserve the current PDF canvas, selection flow, note state, backend workspace-state persistence, and tests; change layout hierarchy and right-panel mode behavior without introducing a new persistence layer.

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Vite, Node built-in test runner, existing `PdfLearningCanvas.vue`, existing translation workspace data/api helpers.

---

## Scope

This plan implements phase 1 from `docs/superpowers/specs/2026-06-27-translation-learning-ide-design.md`.

Included:

- VSCode-style outer shell: dark title bar, activity bar, Explorer, editor tabs, center editor, right auxiliary panel, status bar.
- Workspace tabs as front-end state for current PDF, note stubs, and topic stubs.
- Left Explorer reorganized around opened resources, current PDF outline, import/new-note actions, notes, assets, and search.
- Right panel mode split: default `Agent` and active anchored-note `笔记工作台`.
- Existing anchored note creation continues to work from PDF selection.
- Existing tests and build continue to pass.

Excluded:

- Full backend model for multi-PDF tabs.
- Real persistent independent note documents.
- Cross-PDF knowledge graph or global topic aggregation.
- Full extraction of every UI section into separate `.vue` files.

## Current Code Map

- `web/src/pages/app/TranslationWorkspacePage.vue`
  - Existing monolithic workspace page.
  - Owns toolbar, left side drawer, PDF/text center panel, right Agent/note panel, notes/bookmarks/assets state, resize/collapse state, persistence restore/save.
  - Phase 1 edits stay here to reduce integration risk.
- `web/src/components/translation/PdfLearningCanvas.vue`
  - Existing PDF canvas, geometry selection, popover, note anchors.
  - Keep behavior intact; only small event/label changes if needed.
- `web/src/pages/app/translationWorkspaceData.ts`
  - Existing document/block/context model.
  - Do not change unless a type import is required.
- `web/tests/translationWorkspacePage.test.ts`
  - Current source-level regression tests for workspace routing, copy, persistence, layout markers, notes, responsive behavior.
  - Add source-level assertions for IDE shell and panel modes.
- `web/tests/pdfLearningCanvas.test.ts`
  - Current source-level PDF selection and anchor tests.
  - Keep passing; only add assertions if selection popover labels change.
- `docs/superpowers/specs/2026-06-27-translation-learning-ide-design.md`
  - Product design source of truth for this phase.

## State Model Additions

Add these local types and refs in `TranslationWorkspacePage.vue`:

```ts
type WorkspaceTabKind = 'pdf' | 'anchor-note' | 'standalone-note' | 'topic'
type AgentPanelMode = 'agent' | 'note-workbench' | 'note-assistant' | 'topic-organizer'

interface WorkspaceTab {
  id: string
  kind: WorkspaceTabKind
  title: string
  subtitle?: string
  documentId?: string
  noteId?: string
  topicId?: string
  dirty?: boolean
}

const workspaceTabs = ref<WorkspaceTab[]>([])
const activeWorkspaceTabId = ref<string | null>(null)
const agentPanelMode = ref<AgentPanelMode>('agent')
```

Phase 1 can derive one default PDF tab from `readingDocument`. Stub note/topic tabs may be generated locally for UI flow and tests, but should not claim full persistence.

## Task 1: Lock The IDE Shell Contract With Failing Tests

**Files:**

- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Add failing tests for the VSCode-style shell markers**

Append source-level assertions near the existing layout assertions:

```ts
assert.ok(
  workspaceSource.includes('class="workspace-ide-titlebar"')
    && workspaceSource.includes('class="workspace-tabs"')
    && workspaceSource.includes('class="workspace-explorer"')
    && workspaceSource.includes('class="workspace-editor-area"')
    && workspaceSource.includes('class="workspace-status-bar workspace-status-bar--ide"'),
  'workspace should expose a VSCode-style titlebar, tabs, explorer, editor area, and IDE status bar',
)

assert.ok(
  workspaceSource.includes('workspaceTabs')
    && workspaceSource.includes('activeWorkspaceTabId')
    && workspaceSource.includes('WorkspaceTabKind')
    && workspaceSource.includes('openStandaloneNoteTab')
    && workspaceSource.includes('openTopicTab'),
  'workspace should model PDF, note, and topic resources as IDE tabs',
)
```

- [ ] **Step 2: Add failing tests for the right panel mode split**

Append:

```ts
assert.ok(
  workspaceSource.includes('agentPanelMode')
    && workspaceSource.includes("agentPanelMode === 'note-workbench'")
    && workspaceSource.includes('class="note-workbench-panel"')
    && workspaceSource.includes('返回 Agent'),
  'workspace should switch the right panel from Agent to a note workbench when editing an anchored note',
)

assert.ok(
  workspaceSource.includes('aiCandidateContent')
    && workspaceSource.includes('appendAiCandidateToNote')
    && workspaceSource.includes('AI 候选补充'),
  'workspace should keep Agent output as a candidate before the user appends it to the active note',
)
```

- [ ] **Step 3: Run the test to verify it fails**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
```

Expected: FAIL with at least one assertion mentioning `VSCode-style titlebar` or `note workbench`.

- [ ] **Step 4: Commit only if this task is executed in isolation**

Do not commit the failing test alone on a shared branch unless the implementation task follows immediately.

## Task 2: Introduce Workspace Tabs And Resource Actions

**Files:**

- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Add tab state types and computed defaults**

In `<script setup>`, near existing type declarations, add:

```ts
type WorkspaceTabKind = 'pdf' | 'anchor-note' | 'standalone-note' | 'topic'
type AgentPanelMode = 'agent' | 'note-workbench' | 'note-assistant' | 'topic-organizer'

interface WorkspaceTab {
  id: string
  kind: WorkspaceTabKind
  title: string
  subtitle?: string
  documentId?: string
  noteId?: string
  topicId?: string
  dirty?: boolean
}
```

Near existing top-level refs, add:

```ts
const workspaceTabs = ref<WorkspaceTab[]>([])
const activeWorkspaceTabId = ref<string | null>(null)
const agentPanelMode = ref<AgentPanelMode>('agent')
```

Add computed:

```ts
const activeWorkspaceTab = computed(() => {
  return workspaceTabs.value.find((tab) => tab.id === activeWorkspaceTabId.value) ?? workspaceTabs.value[0] ?? null
})

const activeWorkspaceTabKind = computed<WorkspaceTabKind>(() => activeWorkspaceTab.value?.kind ?? 'pdf')
```

- [ ] **Step 2: Initialize the default PDF tab when a document loads**

Add a watcher close to document restore/default sync watchers:

```ts
watch(readingDocument, (document) => {
  if (!document) {
    workspaceTabs.value = []
    activeWorkspaceTabId.value = null
    return
  }

  const pdfTabId = `pdf-${document.id}`
  const existingTabs = workspaceTabs.value.filter((tab) => tab.id !== pdfTabId)
  const pdfTab: WorkspaceTab = {
    id: pdfTabId,
    kind: 'pdf',
    title: document.title,
    subtitle: 'PDF',
    documentId: document.id,
  }
  workspaceTabs.value = [pdfTab, ...existingTabs]
  if (!activeWorkspaceTabId.value) activeWorkspaceTabId.value = pdfTabId
}, { immediate: true })
```

- [ ] **Step 3: Add tab action helpers**

Add:

```ts
function activateWorkspaceTab(tabId: string) {
  const tab = workspaceTabs.value.find((item) => item.id === tabId)
  if (!tab) return
  activeWorkspaceTabId.value = tab.id
  if (tab.kind === 'pdf') {
    agentPanelMode.value = 'agent'
    documentView.value = 'pdf-canvas'
  } else if (tab.kind === 'anchor-note' || tab.kind === 'standalone-note') {
    agentPanelMode.value = 'note-assistant'
  } else {
    agentPanelMode.value = 'topic-organizer'
  }
}

function closeWorkspaceTab(tabId: string) {
  const nextTabs = workspaceTabs.value.filter((tab) => tab.id !== tabId)
  workspaceTabs.value = nextTabs
  if (activeWorkspaceTabId.value !== tabId) return
  activeWorkspaceTabId.value = nextTabs[0]?.id ?? null
  if (!activeWorkspaceTabId.value) agentPanelMode.value = 'agent'
}

function openStandaloneNoteTab() {
  const tabId = `standalone-note-${Date.now()}`
  workspaceTabs.value.push({
    id: tabId,
    kind: 'standalone-note',
    title: 'Untitled Note',
    subtitle: '独立笔记',
    dirty: true,
  })
  activateWorkspaceTab(tabId)
}

function openTopicTab() {
  const tabId = `topic-${Date.now()}`
  workspaceTabs.value.push({
    id: tabId,
    kind: 'topic',
    title: '排序算法',
    subtitle: '专题',
  })
  activateWorkspaceTab(tabId)
}
```

- [ ] **Step 4: Run focused test**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
```

Expected: Still fail until template markers are added in Task 3.

## Task 3: Rebuild The Outer Shell Into A VSCode-Style IDE Layout

**Files:**

- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Replace the top `workspace-toolbar` content with an IDE titlebar**

Keep the route/back action and `completeLearningSession`, but change the header to a compact IDE chrome:

```vue
<header class="workspace-ide-titlebar">
  <div class="workspace-brand">
    <span class="workspace-brand__mark" aria-hidden="true">E</span>
    <div>
      <strong>Personal English AI</strong>
      <small>学习工作台</small>
    </div>
  </div>

  <label class="workspace-command-center">
    <span class="sr-only">搜索或输入命令</span>
    <input type="search" placeholder="搜索 PDF、笔记、知识点，或输入命令..." />
  </label>

  <div class="workspace-titlebar-actions">
    <button type="button" class="back-button" aria-label="返回翻译列表" title="返回翻译列表" @click="goBackToHub">
      <span class="back-button-icon" aria-hidden="true">←</span>
    </button>
    <button type="button" class="primary-action" @click="completeLearningSession">完成学习</button>
  </div>
</header>
```

- [ ] **Step 2: Add workspace tabs above the center editor**

Inside `workspace-canvas-panel`, before the current text/PDF conditional, add:

```vue
<header class="workspace-tabs" aria-label="已打开学习资源">
  <button
    v-for="tab in workspaceTabs"
    :key="tab.id"
    type="button"
    class="workspace-tab"
    :class="[`workspace-tab--${tab.kind}`, { active: tab.id === activeWorkspaceTabId, dirty: tab.dirty }]"
    @click="activateWorkspaceTab(tab.id)">
    <span>{{ tab.subtitle }}</span>
    <strong>{{ tab.title }}</strong>
    <small v-if="tab.dirty">●</small>
    <small v-else aria-hidden="true">×</small>
  </button>
  <button type="button" class="workspace-tab workspace-tab--new" aria-label="新建学习资源" @click="openStandaloneNoteTab">+</button>
</header>
```

- [ ] **Step 3: Wrap the existing center content in `workspace-editor-area`**

Change:

```vue
<section class="workspace-canvas-panel" aria-label="阅读区">
```

to keep the outer section but add an inner editor:

```vue
<section class="workspace-canvas-panel" aria-label="阅读区">
  <header class="workspace-tabs">...</header>
  <div class="workspace-editor-area" :class="`workspace-editor-area--${activeWorkspaceTabKind}`">
    <!-- existing text/PDF content -->
  </div>
</section>
```

For phase 1, non-PDF tabs may render a placeholder:

```vue
<section v-if="activeWorkspaceTabKind !== 'pdf'" class="note-document-editor">
  <p>{{ activeWorkspaceTab?.subtitle }}</p>
  <h2>{{ activeWorkspaceTab?.title }}</h2>
  <textarea placeholder="这里是完整笔记编辑区，后续阶段接入真实笔记内容。" />
</section>
<template v-else>
  <!-- existing text/PDF conditional -->
</template>
```

- [ ] **Step 4: Run focused test**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
```

Expected: IDE shell/tab assertions pass; right-panel assertions may still fail.

## Task 4: Reorganize The Left Explorer

**Files:**

- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Rename the side drawer semantics to Explorer without breaking state**

Keep `activeSidePanel`, `sidePanelOptions`, outline/bookmark/note/assets/search content. Add class markers and copy:

```vue
<aside
  class="workspace-outline-panel workspace-side-drawer workspace-explorer"
  :class="{ 'workspace-panel--collapsed': isOutlineCollapsed }"
  aria-labelledby="outline-title">
```

Change the header copy to:

```vue
<p>学习资源</p>
<h2 id="outline-title">EXPLORER</h2>
<span>{{ sidePanelSummary }}</span>
```

- [ ] **Step 2: Add an opened resources section above the existing panel switcher**

Add below the header:

```vue
<section class="workspace-opened-resources" aria-label="已打开学习资源">
  <p>已打开</p>
  <button
    v-for="tab in workspaceTabs"
    :key="`drawer-${tab.id}`"
    type="button"
    :class="{ active: tab.id === activeWorkspaceTabId }"
    @click="activateWorkspaceTab(tab.id)">
    <span>{{ tab.subtitle }}</span>
    <strong>{{ tab.title }}</strong>
  </button>
</section>
```

- [ ] **Step 3: Add import/new-note action row**

Reuse current available actions. If there is no real import function in phase 1, keep a disabled/placeholder action with a toast:

```ts
function openImportPdfEntry() {
  showToast('PDF 导入入口将在下一阶段接入当前上传流程', 'info')
}
```

Template:

```vue
<section class="workspace-resource-actions" aria-label="导入与新建">
  <button type="button" @click="openImportPdfEntry">导入 PDF</button>
  <button type="button" @click="openStandaloneNoteTab">新建笔记</button>
  <button type="button" @click="openTopicTab">新建专题</button>
</section>
```

- [ ] **Step 4: Add tests for Explorer actions**

In `web/tests/translationWorkspacePage.test.ts`, add:

```ts
assert.ok(
  workspaceSource.includes('workspace-opened-resources')
    && workspaceSource.includes('workspace-resource-actions')
    && workspaceSource.includes('导入 PDF')
    && workspaceSource.includes('新建专题'),
  'workspace Explorer should expose opened resources and import/new note/topic actions',
)
```

- [ ] **Step 5: Run focused test**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
```

Expected: PASS for Explorer-related assertions.

## Task 5: Split Right Panel Into Agent And Note Workbench Modes

**Files:**

- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Switch to note workbench mode when creating a note from PDF selection**

In `startNoteFromPdfSelection` and `openNoteComposer`, set:

```ts
agentPanelMode.value = 'note-workbench'
isAgentCollapsed.value = false
```

When cancelling or saving an anchored note, set:

```ts
agentPanelMode.value = 'agent'
aiCandidateContent.value = ''
```

- [ ] **Step 2: Add AI candidate state**

Add near `noteAgentPrompt`:

```ts
const aiCandidateContent = ref('')
```

Change `askAgentToAppendNote` so it does not directly modify `noteComposer.value.content`. Instead, after the Agent answer:

```ts
aiCandidateContent.value = answer.answer.trim()
showToast('Agent 已生成候选补充', 'success')
```

Add:

```ts
function appendAiCandidateToNote() {
  appendAgentAnswerToNoteComposer(aiCandidateContent.value)
  aiCandidateContent.value = ''
}
```

Keep `appendAgentAnswerToNoteComposer` available for the existing chat-message append button.

- [ ] **Step 3: Extract the active composer UI into a dedicated note workbench section**

Inside right `<aside>`, render:

```vue
<template v-if="agentPanelMode === 'note-workbench'">
  <section class="note-workbench-panel" aria-label="笔记工作台">
    <header class="note-workbench-header">
      <div>
        <p>笔记工作台</p>
        <strong>P{{ selectedPdfContext?.pageNumber || currentPdfPage }} · 选区</strong>
      </div>
      <button type="button" @click="agentPanelMode = 'agent'">返回 Agent</button>
    </header>

    <nav class="note-workbench-tabs" aria-label="笔记工作台模式">
      <button type="button" class="active">写笔记</button>
      <button type="button" @click="askAgentToAppendNote('结合当前选区，补充一段适合写入学习笔记的解释。')">问 AI</button>
      <button type="button" @click="askAgentToAppendNote('把当前选区整理成 3 条复习要点。')">整理</button>
    </nav>

    <!-- move/reuse existing active note composer form here -->

    <section class="ai-candidate-card" aria-label="AI 候选补充">
      <p>AI 候选补充</p>
      <blockquote>{{ aiCandidateContent || 'Agent 的补充会先出现在这里，确认后再追加到笔记。' }}</blockquote>
      <button type="button" :disabled="!aiCandidateContent" @click="appendAiCandidateToNote">追加到笔记</button>
    </section>
  </section>
</template>

<template v-else>
  <!-- existing Agent default sections: context, translation, toolbar, asset candidates, conversation, command -->
</template>
```

Do not duplicate the note composer in both branches. If no active composer exists, show the current `study-note-panel` list in Agent mode.

- [ ] **Step 4: Update tests to reflect candidate behavior**

Ensure the tests added in Task 1 pass. Add this assertion:

```ts
assert.ok(
  !workspaceSource.includes('回答会追加到上面的笔记正文')
    && workspaceSource.includes('Agent 的补充会先出现在这里，确认后再追加到笔记。'),
  'workspace should present Agent output as confirmable note candidates instead of automatic note-body writes',
)
```

- [ ] **Step 5: Run focused test**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
```

Expected: PASS.

## Task 6: Apply VSCode-Style CSS Without Hurting PDF Reading

**Files:**

- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Test: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Add IDE shell CSS variables**

At the top of the component stylesheet, add or update:

```css
.intensive-workspace-page {
  --ide-bg: #1f2329;
  --ide-panel: #252a31;
  --ide-panel-2: #2d333b;
  --ide-border: #3b4652;
  --ide-text: #d7dde5;
  --ide-muted: #9aa6b5;
  --ide-accent: #2dd4bf;
  --reader-bg: #f5f8fb;
  background: var(--ide-bg);
}
```

- [ ] **Step 2: Style the titlebar, tabs, Explorer, editor area, and right panel**

Add focused CSS blocks:

```css
.workspace-ide-titlebar {
  display: grid;
  grid-template-columns: minmax(220px, auto) minmax(280px, 520px) auto;
  align-items: center;
  gap: 16px;
  min-height: 48px;
  padding: 8px 16px;
  background: var(--ide-bg);
  color: var(--ide-text);
  border-bottom: 1px solid var(--ide-border);
}

.workspace-tabs {
  display: flex;
  align-items: end;
  gap: 2px;
  min-height: 54px;
  padding: 10px 12px 0;
  background: var(--ide-panel);
  border-bottom: 1px solid var(--ide-border);
}

.workspace-tab {
  min-width: 150px;
  max-width: 280px;
  height: 44px;
  border: 0;
  border-radius: 8px 8px 0 0;
  background: var(--ide-panel-2);
  color: var(--ide-text);
}

.workspace-tab.active {
  background: var(--reader-bg);
  color: #102033;
}

.workspace-explorer {
  background: var(--ide-panel);
  color: var(--ide-text);
}

.workspace-editor-area {
  min-height: 0;
  background: var(--reader-bg);
}

.note-workbench-panel {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr) auto;
  min-height: 0;
  padding: 16px;
  gap: 14px;
}
```

Adapt exact sizing to the current grid and existing class names. Do not restyle `PdfLearningCanvas.vue` into dark mode; only its surrounding shell changes.

- [ ] **Step 3: Add compact desktop responsive rules**

Maintain current compact behavior, but align it with the IDE columns:

```css
@media (max-width: 1440px) {
  .workspace-shell--ide {
    grid-template-columns:
      44px
      minmax(190px, 260px)
      0
      minmax(420px, 1fr)
      0
      minmax(300px, 360px);
  }

  .workspace-tab {
    min-width: 120px;
  }
}
```

- [ ] **Step 4: Add test markers for dark shell and light reader**

In `translationWorkspacePage.test.ts`, add:

```ts
assert.ok(
  workspaceSource.includes('--ide-bg')
    && workspaceSource.includes('--reader-bg')
    && workspaceSource.includes('.workspace-tab.active'),
  'workspace should use a dark IDE shell while keeping the active reader surface light',
)
```

- [ ] **Step 5: Run focused test**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
```

Expected: PASS.

## Task 7: Full Verification And Commit

**Files:**

- Verify: `web/tests/translationWorkspacePage.test.ts`
- Verify: `web/tests/pdfLearningCanvas.test.ts`
- Verify: `web/package.json`

- [ ] **Step 1: Run source regression tests**

Run:

```bash
cd web
node --test tests/translationWorkspacePage.test.ts
node --test tests/pdfLearningCanvas.test.ts
```

Expected: both PASS.

- [ ] **Step 2: Run production build**

Run:

```bash
cd web
npm run build
```

Expected: build succeeds. Existing chunk-size warnings are acceptable if unchanged.

- [ ] **Step 3: Manually smoke test in browser**

Open the existing workspace route and verify:

- Default right panel says `Agent`.
- Selecting PDF text shows popover.
- Clicking `记笔记` switches right panel to `笔记工作台`.
- Agent output goes to `AI 候选补充`.
- Clicking `追加到笔记` appends to the note body.
- `保存笔记` returns the right panel to Agent mode.
- Top tabs show PDF and any note/topic stubs.
- Narrowing browser width does not overlap Explorer controls with outline rows.

- [ ] **Step 4: Inspect git diff**

Run:

```bash
git diff -- web/src/pages/app/TranslationWorkspacePage.vue web/tests/translationWorkspacePage.test.ts web/tests/pdfLearningCanvas.test.ts
git diff --check -- web/src/pages/app/TranslationWorkspacePage.vue web/tests/translationWorkspacePage.test.ts web/tests/pdfLearningCanvas.test.ts
```

Expected: no whitespace errors; diff only covers phase 1 implementation files.

- [ ] **Step 5: Commit**

Stage only implementation files:

```bash
git add web/src/pages/app/TranslationWorkspacePage.vue web/tests/translationWorkspacePage.test.ts web/tests/pdfLearningCanvas.test.ts
git commit -m "feat(ui): 实现学习 IDE 工作台布局"
```

Do not stage unrelated dirty files under `tasks/`, `.playwright-mcp/`, `output/`, `resources/`, or generated scratch files.

## Rollback Notes

If layout becomes unstable during implementation:

1. Keep `PdfLearningCanvas.vue` unchanged.
2. Revert only template/CSS changes in `TranslationWorkspacePage.vue`.
3. Preserve state additions only if tests still pass and they do not alter runtime behavior.
4. Re-run `node --test tests/translationWorkspacePage.test.ts` before continuing.

## Merge Readiness

This phase can be merged to `main` if:

- Source tests pass.
- `npm run build` passes.
- Browser smoke test confirms the workspace remains usable at normal and compact desktop widths.
- The implementation commit does not include unrelated dirty files.
