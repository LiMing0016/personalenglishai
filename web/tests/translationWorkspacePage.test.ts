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

assert.ok(
  routerSource.includes("path: 'translation/workspace/:id'"),
  'router should expose a translation workspace route',
)
assert.ok(
  routerSource.includes("name: 'TranslationWorkspace'"),
  'router should name the translation workspace route',
)

for (const requiredCopy of [
  'AI 精读工作台',
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
  '展开左侧目录导航',
  '收起右侧 Agent',
  '展开右侧 Agent',
]) {
  assert.ok(workspaceSource.includes(requiredCopy), `workspace page should render ${requiredCopy}`)
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
  workspaceSource.includes('createTranslationWorkspaceDraftFromParsedDocument'),
  'workspace should convert backend document knowledge into the same reading model used after upload',
)
assert.ok(
  workspaceSource.includes('resolvePersistedPdfPreviewUrl'),
  'workspace should prefer persisted backend file URLs when restoring the PDF canvas',
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
  workspaceSource.includes('assetStats'),
  'workspace should render learning asset stats',
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
  workspaceSource.includes('var(--outline-column-width, 280px)'),
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
  workspaceSource.includes('workspace-drawer-rail'),
  'workspace should render slim drawer rails that can reopen collapsed side panels',
)
assert.ok(
  workspaceSource.includes('44px 0 minmax(560px, 1fr) 0 44px'),
  'workspace should allow both side drawers to collapse while keeping the center canvas dominant',
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
