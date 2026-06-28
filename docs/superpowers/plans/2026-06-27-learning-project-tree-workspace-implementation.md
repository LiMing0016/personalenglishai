# Learning Project Tree Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the left side of the translation workspace from a PDF outline drawer into a light-theme learning project resource tree while preserving PDF outline navigation.

**Architecture:** Keep the existing `TranslationWorkspacePage.vue` shell, PDF canvas, tabs, persistence, and Agent flows. Replace the left drawer information architecture with a project explorer surface that groups resources into folders and embeds the existing PDF outline as a subtree. Use source-level tests to lock key UI strings and structural class names.

**Tech Stack:** Vue 3, TypeScript, Vite, existing Node source tests.

---

### Task 1: Lock the Project Explorer Contract

**Files:**
- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Write the failing test**

Add assertions requiring project-tree copy and structure:

```ts
for (const requiredProjectTreeCopy of [
  '学习项目',
  '项目资源树',
  '资料',
  'PDF 目录',
  '锚点笔记',
  '复习队列',
  '题库',
  '错题本',
  '提示词',
  'project-tree',
  'project-tree-folder',
  'project-tree-outline',
  'openProjectTreeResource',
]) {
  assert.ok(workspaceSource.includes(requiredProjectTreeCopy), `workspace project tree should render ${requiredProjectTreeCopy}`)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test tests/translationWorkspacePage.test.ts`

Expected: FAIL because the page does not yet include `项目资源树` or `project-tree` structures.

### Task 2: Build the Left Project Resource Tree

**Files:**
- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Test: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Add minimal tree types and computed folders**

Add `ProjectTreeFolder`, `ProjectTreeResource`, and computed folder data that derives from the current document, `studyNotes`, `userBookmarks`, and existing outline data. Keep it in page state for this first phase.

- [ ] **Step 2: Replace the outline-first left drawer markup**

Render a project header, quick actions, and folders:

```vue
<section class="project-tree" aria-label="项目资源树">
  <article v-for="folder in projectTreeFolders" :key="folder.id" class="project-tree-folder">
    ...
  </article>
</section>
```

Embed the existing PDF outline rows inside the `PDF 目录` folder using the current `filteredOutlineItems`, `selectOutlineItem`, `toggleOutlineNode`, and note count helpers.

- [ ] **Step 3: Add resource click handling**

Add `openProjectTreeResource(resource)`:

- `pdf` opens PDF tab and switches to PDF canvas.
- `anchor-note` opens the saved note through `openStudyNote`.
- `note` opens a standalone note tab.
- `asset`, `review`, `question-bank`, and `prompt` open a topic tab for the first front-end-only phase.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test tests/translationWorkspacePage.test.ts`

Expected: PASS.

### Task 3: Style the Light Learning IDE Explorer

**Files:**
- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Test: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Add CSS for the project tree**

Add styles for:

- `.project-tree-shell`
- `.project-tree-toolbar`
- `.project-tree`
- `.project-tree-folder`
- `.project-tree-folder__header`
- `.project-tree-resource`
- `.project-tree-outline`

Use existing light theme variables: `--ide-panel`, `--ide-panel-2`, `--ide-bg`, `--ide-border`, `--ide-accent`.

- [ ] **Step 2: Preserve compact behavior**

Update the existing compact media query so project-tree controls stack cleanly at narrow desktop widths.

- [ ] **Step 3: Run focused tests**

Run:

```bash
node --test tests/translationWorkspacePage.test.ts
npm run build
```

Expected: tests pass and production build succeeds.

### Task 4: Manual Review

**Files:**
- Review: `web/src/pages/app/TranslationWorkspacePage.vue`

- [ ] **Step 1: Check layout in browser**

Open the existing workspace URL and verify:

- Left side reads like a learning project tree.
- PDF directory still expands and navigates.
- The page remains white theme.
- The drawer does not overlap controls when the browser is narrowed.

- [ ] **Step 2: Report result**

Summarize changed files, tests run, and any remaining known limitations.
