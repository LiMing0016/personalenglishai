import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const routerSource = readFileSync(
  new URL('../src/router/index.ts', import.meta.url),
  'utf8',
)

const workspaceSource = readFileSync(
  new URL('../src/pages/app/TranslationWorkspacePage.vue', import.meta.url),
  'utf8',
)

const pdfCanvasSource = readFileSync(
  new URL('../src/components/translation/PdfLearningCanvas.vue', import.meta.url),
  'utf8',
)

function extractCssBlock(source: string, selector: string) {
  const start = source.indexOf(selector)
  assert.notEqual(start, -1, `Expected CSS selector ${selector} to exist`)
  const bodyStart = source.indexOf('{', start)
  const bodyEnd = source.indexOf('\n}', bodyStart)
  assert.ok(bodyStart > start && bodyEnd > bodyStart, `Expected CSS block for ${selector}`)
  return source.slice(bodyStart, bodyEnd)
}

assert.ok(
  routerSource.includes("path: 'translation/workspace/:id'"),
  'router should expose a translation workspace route',
)
assert.ok(
  routerSource.includes("name: 'TranslationWorkspace'"),
  'router should name the translation workspace route',
)
assert.ok(
  !workspaceSource.includes('{{ readingDocument.sourceLabel || sourceTypeLabels[readingDocument.sourceType] }} · {{ readingDocument.parseStatus }} · {{ readingDocument.progress }}% · {{ modeLabels[activeMode] }}'),
  'workspace toolbar should not duplicate source, parse status, progress, and mode under the title',
)
assert.ok(
  !workspaceSource.includes('class="canvas-panel-header"')
    && workspaceSource.includes('document-view-tabs document-view-tabs--compact'),
  'workspace canvas should use a compact toolbar view switcher instead of a full reading-area header',
)
assert.ok(
  workspaceSource.includes('aria-label="返回翻译列表"')
    && workspaceSource.includes('title="返回翻译列表"')
    && workspaceSource.includes('class="back-button-icon"'),
  'workspace back action should be compact so it does not push into the document title',
)
assert.ok(
  workspaceSource.includes('grid-template-columns: 32px minmax(0, 1fr) auto')
    && workspaceSource.includes('.document-heading {\n  min-width: 0;'),
  'workspace toolbar should reserve a fixed compact back column and allow the title to truncate',
)

for (const requiredCopy of [
  '沉浸精读',
  '外刊精读',
  '考试精读',
  '技术文档',
  '阅读区',
  'Agent',
  '上下文',
  '当前段落',
  '学习资产',
  '精读文本',
  'PDF 学习画布',
  '目录导航',
  '当前选区',
  '当前页 / 当前段落',
  '学习笔记',
  '返回',
  '完成学习',
  '调整左侧目录宽度',
  '调整右侧 Agent 宽度',
  '收起左侧目录导航',
  '收起右侧 Agent',
  '展开右侧 Agent',
]) {
  assert.ok(workspaceSource.includes(requiredCopy), `workspace page should render ${requiredCopy}`)
}

for (const removedVisibleChrome of [
  'class="workspace-titlebar-document"',
  '<small>学习工作台</small>',
  '<p>学习资源</p>',
  '<h2 id="outline-title">EXPLORER</h2>',
  'class="workspace-opened-resources"',
  'aria-label="已打开学习资源"',
]) {
  assert.ok(!workspaceSource.includes(removedVisibleChrome), `workspace page should remove ${removedVisibleChrome}`)
}

for (const removedCopy of [
  'AI INTENSIVE READING',
  'PDF WORKBENCH',
  'AGENT CONSOLE',
  'CURRENT CONTEXT',
  'PAGE NAVIGATOR',
  '生成学习报告',
  '返回翻译中心',
  'outline-document-card',
]) {
  assert.ok(!workspaceSource.includes(removedCopy), `workspace page should remove ${removedCopy}`)
}

assert.ok(
  workspaceSource.includes('buildIntensiveReadingDocument'),
  'workspace should load drafts created from the Hub',
)
assert.ok(
  workspaceSource.includes('getTranslationDocumentKnowledge'),
  'workspace should restore persisted document knowledge from the backend before falling back to local drafts',
)
assert.ok(
  workspaceSource.includes('getTranslationDocumentFileUrl'),
  'workspace should be able to rebuild a stable backend PDF file URL from the document id',
)
assert.ok(
  workspaceSource.includes('saveTranslationDocumentWorkspaceState'),
  'workspace should persist notes, bookmarks, collapsed outline state, and resume context to the backend',
)
assert.ok(
  workspaceSource.includes('completeLearningSession') && workspaceSource.includes('flushWorkspaceStateSave'),
  'workspace should flush the current learning state before leaving the learning session',
)
assert.ok(
  workspaceSource.includes('downloadTranslationDocumentWithBookmarks'),
  'workspace should export a PDF copy with the current learning bookmarks',
)
assert.ok(
  workspaceSource.includes('createTranslationWorkspaceDraftFromParsedDocument'),
  'workspace should convert backend document knowledge into the same reading model used after upload',
)
assert.ok(
  workspaceSource.includes('restoreWorkspaceState'),
  'workspace should restore workspaceState returned by persisted document knowledge',
)
assert.ok(
  workspaceSource.includes('if (workspaceStateRestoring) return') && workspaceSource.includes('syncDocumentDefaultPage'),
  'workspace should not let document-load defaults overwrite the restored PDF page during refresh',
)
assert.ok(
  workspaceSource.includes('state.currentPage') && workspaceSource.includes('syncActiveBlockToPdfPage(restoredPage)'),
  'workspace should restore the active block from the persisted current PDF page when no block id is available',
)
assert.ok(
  workspaceSource.includes('syncActiveBlockToPdfPage(page)') && workspaceSource.includes('handlePdfPageChange(page: number)'),
  'workspace should keep the active block aligned when the user changes PDF pages',
)
assert.ok(
  workspaceSource.includes('focusRouteStudyNote'),
  'workspace should focus a previous note when the Hub opens it with a note id',
)
assert.ok(
  workspaceSource.includes('route.query.noteId'),
  'workspace should read noteId from route query when returning to a previous note',
)
assert.ok(
  workspaceSource.includes('resolvePersistedPdfPreviewUrl'),
  'workspace should prefer persisted backend file URLs when restoring the PDF canvas',
)
assert.ok(
  workspaceSource.includes('applyLocalDraftDisplayOverrides'),
  'workspace should preserve user-renamed imported titles when restoring persisted documents',
)
assert.ok(
  workspaceSource.includes('workspaceLoading'),
  'workspace should expose a loading state while persisted document knowledge is being restored',
)
assert.ok(
  workspaceSource.includes('activeBlockId'),
  'workspace should track the active document block for agent context',
)
assert.ok(
  workspaceSource.includes('studyAssetPipeline'),
  'workspace should render the session learning asset pipeline',
)
assert.ok(
  workspaceSource.includes('PdfLearningCanvas'),
  'workspace should expose a dedicated PDF learning canvas container',
)
assert.ok(
  workspaceSource.includes('selectedPdfText'),
  'workspace should keep selected PDF text as the agent context',
)
assert.ok(
  workspaceSource.includes('selectedPdfContext'),
  'workspace should keep structured PDF selection context for source-aware agent questions',
)
assert.ok(
  workspaceSource.includes('answerTranslationDocumentQuestion'),
  'workspace should call the backend source-grounded Agent answer endpoint',
)
assert.ok(
  workspaceSource.includes('agentAnswerLoading'),
  'workspace should expose a loading state while waiting for source-grounded Agent answers',
)
assert.ok(
  workspaceSource.includes('message.citations') && workspaceSource.includes('引用'),
  'workspace should render source citations returned by the Agent answer endpoint',
)
assert.ok(
  workspaceSource.includes('jumpToCitation'),
  'workspace should let users click citations and jump back to the PDF source page',
)
assert.ok(
  workspaceSource.includes('citation.pageNumber') && workspaceSource.includes('citation.elementId'),
  'workspace citations should keep pageNumber and elementId visible to the user',
)
assert.ok(
  workspaceSource.includes('handlePdfSelectionChange'),
  'workspace should normalize PDF selection payloads before sending them to the Agent area',
)
assert.ok(
  workspaceSource.includes('buildDocumentSelectionContext'),
  'workspace should build source context with documentId, pageNumber, elementId, and bbox',
)
assert.ok(
  workspaceSource.includes("documentView.value = 'pdf-canvas'"),
  'workspace should open PDF documents in the learning canvas by default',
)
assert.ok(
  workspaceSource.includes('workspace-outline-panel'),
  'workspace should expose a left outline panel for page and heading navigation',
)
assert.ok(
  workspaceSource.includes('workspace-canvas-panel'),
  'workspace should keep the PDF canvas in the dominant center panel',
)
assert.ok(
  workspaceSource.includes('workspace-agent-panel'),
  'workspace should keep agent analysis in a dedicated right panel',
)
assert.ok(
  workspaceSource.includes('outlineItems'),
  'workspace should derive the left navigation from document outline items instead of raw parsed blocks',
)
assert.ok(
  !workspaceSource.includes('`P${block.order}`'),
  'workspace outline should not expose raw paragraph block numbers as document outline entries',
)
assert.ok(
  workspaceSource.includes('targetPdfPage'),
  'workspace should synchronize outline navigation with the PDF canvas page',
)
assert.ok(
  workspaceSource.includes('grid-template-rows: auto minmax(0, 1fr) auto'),
  'workspace page should use a full-page grid instead of a centered content column',
)
assert.ok(
  workspaceSource.includes('height: 100vh'),
  'workspace page should occupy the full viewport height',
)
assert.ok(
  workspaceSource.includes('width: 100%'),
  'workspace shell should stretch across the available page width',
)
assert.ok(
  !workspaceSource.includes('width: min(1880px, 100%)'),
  'workspace shell should not be capped to a centered max width',
)
assert.ok(
  !workspaceSource.includes('position: fixed;\n  left: 50%;\n  bottom: 16px'),
  'learning asset bar should not float over the PDF workspace',
)
assert.ok(
  workspaceSource.includes('workspaceShellRef'),
  'workspace should keep a shell ref for draggable column resizing',
)
assert.ok(
  workspaceSource.includes('outlineColumnWidth'),
  'workspace should expose a controlled outline column width',
)
assert.ok(
  workspaceSource.includes('agentColumnWidth'),
  'workspace should expose a controlled agent column width',
)
assert.ok(
  workspaceSource.includes('startWorkspaceResize'),
  'workspace should start resizing from pointer drag handles',
)
assert.ok(
  workspaceSource.includes('resizeWorkspacePanels'),
  'workspace should calculate resized panel widths from pointer movement',
)
assert.ok(
  workspaceSource.includes('workspace-resizer'),
  'workspace should render draggable splitters between the three columns',
)
assert.ok(
  workspaceSource.includes('var(--outline-column-width, 300px)'),
  'workspace grid should use the user-controlled outline width variable',
)
assert.ok(
  workspaceSource.includes('var(--agent-column-width, 430px)'),
  'workspace grid should use the user-controlled agent width variable',
)
assert.ok(
  workspaceSource.includes('isOutlineCollapsed'),
  'workspace should track whether the left outline drawer is collapsed',
)
assert.ok(
  workspaceSource.includes('isAgentCollapsed'),
  'workspace should track whether the right agent drawer is collapsed',
)
assert.ok(
  workspaceSource.includes('toggleOutlineDrawer'),
  'workspace should expose a control to collapse and expand the outline drawer',
)
assert.ok(
  workspaceSource.includes('collapsedOutlineItemIds'),
  'workspace should track collapsed document outline nodes',
)
assert.ok(
  workspaceSource.includes('outlineTreeItems'),
  'workspace should derive tree metadata from flat document outline items',
)
assert.ok(
  workspaceSource.includes('toggleOutlineNode'),
  'workspace should let chapter and section outline nodes collapse independently',
)
assert.ok(
  workspaceSource.includes('createUserBookmark') && workspaceSource.includes('renameActiveUserBookmark') && workspaceSource.includes('deleteActiveUserBookmark'),
  'workspace should let users create, rename, and delete their own learning bookmarks',
)
assert.ok(
  workspaceSource.includes('activeOutlineItemId') && workspaceSource.includes('bookmarkId'),
  'workspace notes should bind back to the selected outline/bookmark entry',
)
assert.ok(
  workspaceSource.includes(':active-note-id="activeNoteId"'),
  'workspace should pass the active note id into the PDF canvas for anchored note focus',
)
assert.ok(
  workspaceSource.includes('bbox: note.bbox') && workspaceSource.includes('active: note.id === activeNoteId'),
  'workspace note anchors should preserve the saved PDF bbox and active note state',
)
assert.ok(
  workspaceSource.includes("activeSidePanel.value = 'notes'")
    && workspaceSource.includes('isAgentCollapsed.value = false')
    && workspaceSource.includes('@note-selection="startNoteFromPdfSelection"'),
  'workspace should open the note workspace immediately when the user creates a note from a PDF selection',
)
const notePanelIndex = workspaceSource.indexOf('class="study-note-panel"')
const agentContextIndex = workspaceSource.indexOf('class="agent-context"')
assert.ok(
  notePanelIndex !== -1 && agentContextIndex !== -1 && notePanelIndex < agentContextIndex,
  'workspace should keep the anchored note editor at the top of the right panel before context and translation cards',
)
assert.ok(
  workspaceSource.includes('study-note-panel--composer-active')
    && workspaceSource.includes('study-note-selected-text')
    && workspaceSource.includes('ref="noteContentInputRef"'),
  'workspace should make the active anchored note editor explicit with selected text preview and focused note input',
)
assert.ok(
  workspaceSource.includes('note-agent-compose')
    && workspaceSource.includes('noteAgentPrompt')
    && workspaceSource.includes('askAgentToAppendNote')
    && workspaceSource.includes('appendAgentAnswerToNoteComposer'),
  'workspace note editor should let users ask Agent and append the answer into the active anchored note',
)
assert.ok(
  workspaceSource.includes('追加到当前笔记')
    && workspaceSource.includes("noteComposer.mode !== 'idle'")
    && workspaceSource.includes('@click="appendAgentAnswerToNoteComposer(message.content)"'),
  'workspace should let users append an existing Agent answer into the note they are editing',
)
assert.ok(
  workspaceSource.includes("label: '笔记来源'") && workspaceSource.includes('jumpToStudyNote(note)'),
  'workspace should re-highlight the original PDF selection when opening an anchored note',
)
assert.ok(
  workspaceSource.includes('isStudyNoteInActiveContext'),
  'workspace should include the active PDF anchored note in the right-side note panel even when it is not bound to the current text block',
)
assert.ok(
  workspaceSource.includes('outline-node-toggle') && workspaceSource.includes('aria-expanded'),
  'workspace should render accessible expand/collapse controls for outline tree nodes',
)
assert.ok(
  workspaceSource.includes('toggleAgentDrawer'),
  'workspace should expose a control to collapse and expand the agent drawer',
)
assert.ok(
  workspaceSource.includes('workspace-shell--outline-collapsed'),
  'workspace grid should have a collapsed state for the outline drawer',
)
assert.ok(
  workspaceSource.includes('workspace-shell--agent-collapsed'),
  'workspace grid should have a collapsed state for the agent drawer',
)
assert.ok(
  workspaceSource.includes('workspace-activity-bar')
    && workspaceSource.includes('sidePanelOptions')
    && workspaceSource.includes('selectSidePanel'),
  'workspace should use an IDE-style activity bar to switch outline, notes, assets, and search drawers',
)
assert.ok(
  workspaceSource.includes('grid-template-columns:\n    48px\n    minmax(220px, var(--outline-column-width, 300px))')
    && workspaceSource.includes('grid-template-columns:\n    48px\n    0\n    0\n    minmax(560px, 1fr)'),
  'workspace grid should keep a narrow activity bar while allowing the left drawer to collapse without taking space',
)
assert.ok(
  workspaceSource.includes('@media (max-width: 1440px)')
    && workspaceSource.includes('grid-template-columns:\n      44px\n      minmax(190px, 240px)')
    && workspaceSource.includes('minmax(360px, 1fr)')
    && workspaceSource.includes('minmax(300px, 340px)'),
  'workspace should use a compact desktop grid before the mobile breakpoint so narrow browser windows do not overflow',
)
assert.ok(
  workspaceSource.includes('.side-drawer-switcher {\n    grid-template-columns: repeat(3, minmax(0, 1fr));')
    && workspaceSource.includes('.outline-filter-tabs {\n    grid-template-columns: 1fr;')
    && workspaceSource.includes('.outline-quick-actions {\n    grid-template-columns: 1fr;'),
  'workspace left drawer controls should stack in compact desktop layouts instead of overlapping',
)
assert.ok(
  workspaceSource.includes('grid-template-rows: auto auto auto minmax(0, 1fr);')
    && workspaceSource.includes('.side-drawer-panel {\n  grid-row: 3 / 5;')
    && workspaceSource.includes('.outline-list {\n  grid-row: 4;'),
  'workspace left drawer should reserve explicit rows so controls and the scrollable outline list cannot overlap',
)
assert.ok(
  workspaceSource.includes("v-else-if=\"activeSidePanel === 'assets'\"")
    && workspaceSource.includes('side-asset-board')
    && workspaceSource.includes('side-asset-card'),
  'workspace should move the learning asset pipeline into the left drawer instead of a permanent bottom panel',
)
assert.ok(
  workspaceSource.includes('workspace-status-bar')
    && workspaceSource.includes('workspace-status-bar--ide')
    && workspaceSource.includes("selectSidePanel('assets')")
    && !workspaceSource.includes('<footer v-if="readingDocument" class="asset-pipeline"'),
  'workspace should replace the large bottom asset pipeline with a compact IDE status bar',
)
assert.ok(
  workspaceSource.includes('workspace-ide-titlebar')
    && workspaceSource.includes('workspace-tabs')
    && workspaceSource.includes('workspace-explorer')
    && workspaceSource.includes('workspace-editor-area')
    && workspaceSource.includes('workspace-status-bar--ide'),
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
assert.ok(
  workspaceSource.includes('workspace-resource-actions')
    && workspaceSource.includes('导入 PDF')
    && workspaceSource.includes('新建专题'),
  'workspace Explorer should expose import/new note/topic actions without duplicating opened resources',
)
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
assert.ok(
  !workspaceSource.includes('回答会追加到上面的笔记正文')
    && workspaceSource.includes('Agent 的补充会先出现在这里，确认后再追加到笔记。'),
  'workspace should present Agent output as confirmable note candidates instead of automatic note-body writes',
)
assert.ok(
  workspaceSource.includes('--ide-bg')
    && workspaceSource.includes('--reader-bg')
    && workspaceSource.includes('.workspace-tab.active'),
  'workspace should keep the learning IDE shell variables centralized',
)
assert.ok(
  workspaceSource.includes('--ide-bg: #f3f6fa')
    && workspaceSource.includes('--ide-panel: #ffffff')
    && workspaceSource.includes('--reader-bg: #f5f8fb')
    && workspaceSource.includes('background: var(--ide-bg);')
    && workspaceSource.includes('.workspace-editor-toolbar')
    && workspaceSource.includes('background: var(--ide-panel);'),
  'workspace should render as a light learning IDE instead of a dark shell',
)
assert.ok(
  !/background:\s*#(?:11161d|171b20|1b1f24|3f4548|3d3520);/.test(workspaceSource),
  'workspace light theme should not leave hard-coded dark background blocks in the IDE chrome',
)
assert.ok(
  extractCssBlock(workspaceSource, '.workspace-command-center input').includes('background: #ffffff;')
    && extractCssBlock(workspaceSource, '.workspace-activity-bar {').includes('background: #ffffff;')
    && extractCssBlock(workspaceSource, '.outline-search input').includes('background: #ffffff;'),
  'command search, activity bar, and outline search should render as light IDE surfaces',
)
assert.ok(
  pdfCanvasSource.includes('getToken'),
  'PDF canvas should read the current auth token before loading a protected backend PDF file',
)
assert.ok(
  pdfCanvasSource.includes('httpHeaders') && pdfCanvasSource.includes('Authorization'),
  'PDF canvas should pass the bearer token to pdf.js document requests',
)

console.log('translation-workspace-page-ok')
