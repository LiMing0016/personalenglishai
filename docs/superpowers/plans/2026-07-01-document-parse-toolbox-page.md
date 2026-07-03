# Document Parse Toolbox Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Toolbox document parsing page that matches the PaddleOCR two-pane workflow while preserving the existing translation workspace.

**Architecture:** Reuse the existing translation document import API, PDF preview component, and document parse data builders. Add a new route/page under `/app/tools/document-parse`; the page owns only toolbox UI state and delegates parsing/PDF rendering to existing modules.

**Tech Stack:** Vue 3, TypeScript, Vue Router, Axios translation API, pdfjs-based `PdfLearningCanvas`, Vite tests/build.

---

## File Structure

- Modify `web/src/router/index.ts`: add `/app/tools/document-parse` child route.
- Modify `web/src/components/AppRail.vue`: add a Toolbox navigation item and icon.
- Create `web/src/pages/app/DocumentParseToolboxPage.vue`: PaddleOCR-style toolbox page with recent files, upload, PDF source pane, parsed document pane, JSON tab, and parsing states.
- Modify `web/src/pages/app/translationWorkspaceData.ts`: keep existing functions; add small reusable helpers only if needed by the toolbox page.
- Modify or create `web/src/pages/app/documentParseToolboxData.test.ts`: test toolbox-specific pure data helpers if any are introduced.
- Run existing `web/src/pages/app/translationWorkspaceData.test.ts`: verify current translation workspace parse rendering remains intact.

## Task 1: Route And Navigation

**Files:**
- Modify: `web/src/router/index.ts`
- Modify: `web/src/components/AppRail.vue`

- [ ] **Step 1: Add failing route/navigation expectation manually**

Run after the first route/nav edit only:

```bash
cd web
npm run build
```

Expected before implementation: no toolbox route exists, so manual browser navigation to `/app/tools/document-parse` cannot render the page.

- [ ] **Step 2: Add route**

Add this child route under `/app`:

```ts
{
  path: 'tools/document-parse',
  name: 'DocumentParseToolbox',
  component: () => import('@/pages/app/DocumentParseToolboxPage.vue'),
  meta: { immersive: true },
}
```

- [ ] **Step 3: Add rail entry**

Extend `SkillIcon` with `toolbox`, add a toolbox item:

```ts
{ to: '/app/tools/document-parse', activePrefix: '/app/tools', label: '工具箱', skillIcon: 'toolbox' }
```

Render a simple tool/grid icon branch in `AppRail.vue` following existing inline SVG style.

## Task 2: Toolbox Page Shell

**Files:**
- Create: `web/src/pages/app/DocumentParseToolboxPage.vue`

- [ ] **Step 1: Build the static shell first**

Create a Vue SFC with:

```vue
<template>
  <main class="document-parse-toolbox">
    <aside class="toolbox-sidebar">...</aside>
    <section class="source-pane">...</section>
    <section class="parse-pane">...</section>
  </main>
</template>
```

The first screen must be the actual parsing workspace, not a landing page.

- [ ] **Step 2: Match PaddleOCR layout**

Use a 3-column app surface:

```css
.document-parse-toolbox {
  display: grid;
  grid-template-columns: 268px minmax(420px, 1fr) minmax(420px, 1fr);
  height: 100vh;
  overflow: hidden;
  background: #f6f8fb;
}
```

Use restrained borders, white panes, blue active tabs, and teal action accents consistent with the current app.

## Task 3: Reuse Existing Parse Flow

**Files:**
- Modify: `web/src/pages/app/DocumentParseToolboxPage.vue`

- [ ] **Step 1: Wire upload to current API**

Use:

```ts
import {
  getTranslationDocumentFileUrl,
  getTranslationDocumentKnowledge,
  importTranslationDocument,
  type TranslationDocumentParseResponse,
} from '@/api/translation'
```

Call:

```ts
const parsed = await importTranslationDocument(file, 'immersive', selectedParseMode.value, selectedProvider.value)
```

- [ ] **Step 2: Build a local draft without saving over translation behavior**

Use:

```ts
import {
  buildDocumentParsePages,
  createTranslationWorkspaceDraftFromParsedDocument,
  buildIntensiveReadingDocument,
} from '@/pages/app/translationWorkspaceData'
```

Keep the toolbox state in component refs; do not remove or alter `TranslationWorkspacePage.vue`.

- [ ] **Step 3: Poll/refresh knowledge after first response**

When the import returns `documentId`, call `getTranslationDocumentKnowledge(documentId)` on a slow interval while `parseStatus` is not complete. Stop polling on unmount.

## Task 4: Source PDF And Parsed Content

**Files:**
- Modify: `web/src/pages/app/DocumentParseToolboxPage.vue`

- [ ] **Step 1: Reuse `PdfLearningCanvas`**

Render:

```vue
<PdfLearningCanvas
  v-if="activeDocument?.pdfPreviewUrl"
  :document-id="activeDocument.id"
  :title="activeDocument.title"
  :src="activeDocument.pdfPreviewUrl"
  :blocks="activeDocument.blocks"
  :active-block-id="activeBlockId"
  :page-count="activeDocument.pageCount"
  :target-page="activePageNumber"
  @select-block="activeBlockId = $event"
  @page-change="activePageNumber = $event"
/>
```

- [ ] **Step 2: Render Paddle-style parse tab**

Use `buildDocumentParsePages(activeDocument.blocks)` and render page cards with block type classes. Include a JSON tab that displays the latest `TranslationDocumentParseResponse` in a `<pre>`.

- [ ] **Step 3: Preserve translation workspace**

Do not import or mutate `TranslationWorkspacePage.vue` behavior except for shared helpers already used by both pages.

## Task 5: Verification

**Files:**
- Test: `web/src/pages/app/translationWorkspaceData.test.ts`

- [ ] **Step 1: Run focused data tests**

```bash
cd web
npx tsx src/pages/app/translationWorkspaceData.test.ts
```

Expected: tests pass.

- [ ] **Step 2: Run frontend build**

```bash
cd web
npm run build
```

Expected: build passes with the new route and page.

- [ ] **Step 3: Manual browser check**

Open:

```text
http://127.0.0.1:3300/app/tools/document-parse
```

Expected: page renders with PaddleOCR-style sidebar, source pane, parsed pane, upload control, model selector, and JSON tab. Uploading a PDF starts local PaddleOCR and shows first parsed pages once the backend responds.

## Self-Review

- Spec coverage: route, toolbox entry, PaddleOCR two-pane layout, upload, source PDF, parsed result, JSON view, and translation-workspace preservation are covered.
- Placeholder scan: no TODO/TBD placeholders are used.
- Type consistency: page consumes existing `TranslationDocumentParseResponse`, `IntensiveReadingDocument`, and `DocumentParsePage` types without changing the API contract.
