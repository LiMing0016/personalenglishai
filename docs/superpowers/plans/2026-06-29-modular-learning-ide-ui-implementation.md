# Modular Learning IDE UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first Vue front-end framework for the modular learning IDE, including Obsidian-style knowledge references, user-added learning modules, and the revised no-activity-bar workspace layout.

**Architecture:** Keep the existing PDF canvas and backend restoration flow intact. Add a small typed learning-IDE model layer, presentational Vue components under `web/src/components/learning-ide`, and wire those components into `TranslationWorkspacePage.vue` without moving high-risk persistence or PDF rendering logic yet.

**Tech Stack:** Vue 3, TypeScript, Vite, Node source-contract tests with `npx tsx`, existing scoped CSS.

---

## File Structure

- Create `web/src/types/learningIde.ts` for stable UI/data contracts shared by the new components.
- Create `web/src/pages/app/learningIdeMock.ts` for first-version module catalog, knowledge card, backlinks, graph, and output dock seed data.
- Create `web/src/composables/useLearningModules.ts` for deriving enabled/disabled module groups from the catalog.
- Create `web/tests/learningIdeModel.test.ts` to lock the module catalog and Obsidian-style graph/reference behavior before implementation.
- Create `web/src/components/learning-ide/*.vue` for focused presentational pieces:
  - `LearningIdeTopBar.vue`
  - `LearningResourcePanel.vue`
  - `LearningModuleLibrary.vue`
  - `WorkspaceTabs.vue`
  - `KnowledgeCardView.vue`
  - `BacklinksPanel.vue`
  - `LocalGraphPanel.vue`
  - `ContextAssistantPanel.vue`
  - `LearningOutputDock.vue`
  - `PdfSelectionActionToolbar.vue`
- Modify `web/src/pages/app/TranslationWorkspacePage.vue` to import and use the new components, remove the far-left Activity Bar, and expose the knowledge-card side context in the main canvas.
- Modify `web/tests/translationWorkspacePage.test.ts` to assert the new modular shell and absence of the Activity Bar.

### Task 1: Model Contracts and Failing Test

**Files:**
- Create: `web/tests/learningIdeModel.test.ts`
- Create later: `web/src/types/learningIde.ts`
- Create later: `web/src/pages/app/learningIdeMock.ts`
- Create later: `web/src/composables/useLearningModules.ts`

- [ ] **Step 1: Write the failing test**

Add assertions that import the not-yet-created model helpers:

```ts
import assert from 'node:assert/strict'
import {
  buildLearningModuleGroups,
  demoKnowledgeGraph,
  demoLearningIdeContext,
  demoModuleCatalog,
  resolveBacklinksForKnowledgeNode,
} from '../src/pages/app/learningIdeMock.ts'

assert.ok(demoModuleCatalog.some((item) => item.id === 'pdf-explainer'))
assert.ok(demoModuleCatalog.some((item) => item.id === 'mistake-book'))
assert.ok(demoModuleCatalog.some((item) => item.id === 'word-cards'))
assert.ok(demoModuleCatalog.some((item) => item.id === 'knowledge-cards'))

const groups = buildLearningModuleGroups(demoModuleCatalog)
assert.ok(groups.some((group) => group.id === 'base' && group.modules.some((item) => item.id === 'pdf-explainer')))
assert.ok(groups.some((group) => group.id === 'practice' && group.modules.some((item) => item.id === 'mistake-book')))

const quadraticBacklinks = resolveBacklinksForKnowledgeNode(demoLearningIdeContext, 'knowledge-quadratic-function')
assert.ok(quadraticBacklinks.some((item) => item.sourceType === 'pdf-selection'))
assert.ok(quadraticBacklinks.some((item) => item.sourceType === 'note'))
assert.ok(quadraticBacklinks.some((item) => item.sourceType === 'mistake'))

assert.ok(demoKnowledgeGraph.nodes.some((node) => node.type === 'pdf-selection'))
assert.ok(demoKnowledgeGraph.edges.some((edge) => edge.relation === 'references'))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx tsx tests/learningIdeModel.test.ts`

Expected: fails because `learningIdeMock.ts` does not exist.

- [ ] **Step 3: Implement the typed model and helpers**

Create `learningIde.ts`, `learningIdeMock.ts`, and `useLearningModules.ts` with the exact exported symbols used by the test. The first version should model module catalog entries, wiki links, backlinks, block references, tags, graph nodes, and output-dock items.

- [ ] **Step 4: Run model test**

Run: `npx tsx tests/learningIdeModel.test.ts`

Expected: PASS.

### Task 2: Presentational Component Set

**Files:**
- Create: `web/src/components/learning-ide/LearningIdeTopBar.vue`
- Create: `web/src/components/learning-ide/LearningResourcePanel.vue`
- Create: `web/src/components/learning-ide/LearningModuleLibrary.vue`
- Create: `web/src/components/learning-ide/WorkspaceTabs.vue`
- Create: `web/src/components/learning-ide/KnowledgeCardView.vue`
- Create: `web/src/components/learning-ide/BacklinksPanel.vue`
- Create: `web/src/components/learning-ide/LocalGraphPanel.vue`
- Create: `web/src/components/learning-ide/ContextAssistantPanel.vue`
- Create: `web/src/components/learning-ide/LearningOutputDock.vue`
- Create: `web/src/components/learning-ide/PdfSelectionActionToolbar.vue`

- [ ] **Step 1: Implement thin components**

Each component receives plain props and emits simple intent events. Components must not call APIs, touch storage, or duplicate workspace persistence state.

- [ ] **Step 2: Build type-check**

Run: `npm run build`

Expected: type errors may appear until Task 3 wires imports. Fix only new component typing errors at this stage.

### Task 3: Wire Components Into Translation Workspace

**Files:**
- Modify: `web/src/pages/app/TranslationWorkspacePage.vue`
- Modify: `web/tests/translationWorkspacePage.test.ts`

- [ ] **Step 1: Update source-contract test**

Assert the workspace imports the new component set, removes `workspace-activity-bar`, keeps `PdfLearningCanvas`, and exposes `LearningModuleLibrary`, `KnowledgeCardView`, `BacklinksPanel`, `LocalGraphPanel`, `ContextAssistantPanel`, and `LearningOutputDock`.

- [ ] **Step 2: Run test to verify it fails**

Run: `npx tsx tests/translationWorkspacePage.test.ts`

Expected: FAIL before the page imports/wires the new components.

- [ ] **Step 3: Refactor the page shell**

Use the new top bar, resource panel, tabs, knowledge context, assistant panel, module library, and output dock while retaining existing functions for PDF selection, note creation, Agent questions, bookmarks, and persistence.

- [ ] **Step 4: Run updated tests**

Run:

```bash
npx tsx tests/learningIdeModel.test.ts
npx tsx tests/translationWorkspacePage.test.ts
```

Expected: PASS.

### Task 4: Final Verification

**Files:**
- Verify all changed front-end files.

- [ ] **Step 1: Run full frontend build**

Run: `npm run build`

Expected: PASS.

- [ ] **Step 2: Review docs and merge readiness**

Check whether the implementation changed architecture or APIs beyond the existing plan/spec docs. Record whether this branch can be merged to `main` after review.
