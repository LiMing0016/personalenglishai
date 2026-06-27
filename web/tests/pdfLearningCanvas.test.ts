import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const packageJson = JSON.parse(
  readFileSync(new URL('../package.json', import.meta.url), 'utf8'),
)

const canvasFile = new URL('../src/components/translation/PdfLearningCanvas.vue', import.meta.url)
assert.ok(existsSync(canvasFile), 'PDF workspace should use a dedicated PDF learning canvas component')

const canvasSource = readFileSync(canvasFile, 'utf8')
const workspaceSource = readFileSync(
  new URL('../src/pages/app/TranslationWorkspacePage.vue', import.meta.url),
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
  packageJson.dependencies?.['pdfjs-dist'],
  'PDF learning canvas should depend on pdfjs-dist instead of browser-native PDF embedding only',
)

for (const requiredCanvasFeature of [
  'interface PdfSelectionPayload',
  'interface PdfSourceHighlight',
  'interface GeometrySelectionBox',
  "type PdfSelectionType = 'text' | 'region'",
  'selectionType: PdfSelectionType',
  'selectedSelectionType',
  'interface TextSpanHit',
  'findTextCharacterHits',
  'mergeCharacterHitsIntoBoxes',
  'formatGeometryRectBbox',
  'parseBboxToGeometry',
  'normalizeBboxNumbers',
  'renderSourceHighlight',
  'document.createRange()',
  'range.setStart',
  'range.setEnd',
  'range.getBoundingClientRect()',
  'range.detach?.()',
  'pdfjs-dist/build/pdf.mjs',
  'pdf.worker.mjs?url',
  'pdf-canvas-layer',
  'pdf-text-layer',
  'pdf-annotation-layer',
  'selection-geometry-box',
  'region-selection-box',
  'citation-highlight-box',
  'pdfjs-dist/web/pdf_viewer.mjs',
  'TextLayerBuilder',
  'textContentParams',
  'textLayerBuilder',
  'textLayerAbortController',
  'selectionBoxes',
  'noteAnchorHighlightBoxes',
  'note-anchor-highlight-box',
  'sourceHighlight?: PdfSourceHighlight | null',
  'sourceHighlightBoxes',
  'watch(() => props.sourceHighlight',
  'createClickRegionSelection',
  'findCanvasInkBoundsNearPoint',
  'sampleCanvasInkBounds',
  'shouldExpandClickRegion',
  'isInkNearScanEdge',
  'isPointOnTextSpan',
  'clickRegionScanWidth',
  'clickRegionScanHeight',
  'clickRegionMaxScanWidth',
  'clickRegionMaxScanHeight',
  'clickRegionExpansionStep',
  'beginGeometrySelection',
  'updateGeometrySelection',
  'finishGeometrySelection',
  'cancelGeometrySelection',
  'getTextLayerPoint',
  'updateSelectionFromGeometry',
  "querySelectorAll('.textLayer span')",
  'window.getSelection()?.removeAllRanges()',
  '--total-scale-factor',
  'fit-width',
  'ResizeObserver',
  'calculateFitWidthScale',
  'requestPageRender',
  'isRenderingPage',
  'needsPageRender',
  'syncScaleFromRender',
  'handleStageWheel',
  'turnPageFromScroll',
  'targetPage',
  'pageChange',
  'resolveSelectionPayload',
  "applySelectionPayload('图表/图片区选区'",
  'resolveDocumentSelectionContextFromText',
  'documentId: props.documentId',
  'blocks: props.blocks',
  'activeBlockId: props.activeBlockId',
  'selectedText: text',
  'elementId: context?.elementId ?? null',
  'bbox: bbox ?? context?.bbox ?? null',
  "selectionType: 'region'",
  "emit('selectionChange', payload)",
]) {
  assert.ok(
    canvasSource.includes(requiredCanvasFeature),
    `PDF learning canvas should expose ${requiredCanvasFeature}`,
  )
}

assert.ok(
  !canvasSource.includes('pdf-note-rail'),
  'PDF learning canvas should not own the note rail in the VS Code-style workspace',
)
assert.ok(
  !canvasSource.includes('pdf-parsed-text-layer'),
  'PDF learning canvas should not squeeze the center page with a parsed text side rail',
)
assert.ok(
  !canvasSource.includes('pdf-text-token'),
  'PDF learning canvas should not hand-roll transparent text tokens because that causes broad inaccurate browser selections',
)
assert.ok(
  !canvasSource.includes('@mouseup="captureSelection"'),
  'PDF learning canvas should not use native mouseup selection because pdf.js text-layer DOM order can over-select later lines',
)
assert.ok(
  canvasSource.includes('user-select: none'),
  'PDF learning canvas should disable native browser selection and use geometry-based selection boxes',
)
assert.ok(
  !canvasSource.includes('transform: `scaleX('),
  'PDF learning canvas should not estimate text token width with scaleX; pdf.js TextLayer should own text positioning',
)
assert.ok(
  !canvasSource.includes('const scale = ref(1.1)'),
  'PDF learning canvas should not default to a fixed 110% zoom that leaves large dark gutters',
)
assert.ok(
  canvasSource.includes('maxScale = 3'),
  'PDF learning canvas should allow Acrobat-like fit-width zoom levels above 200%',
)
assert.ok(
  canvasSource.includes('@wheel="handleStageWheel"'),
  'PDF learning canvas should own wheel gestures so it can scroll the PDF stage before turning pages',
)
assert.ok(
  !canvasSource.includes('@wheel.passive="handleStageWheel"'),
  'PDF learning canvas should not use passive wheel handling because it must prevent parent-page scrolling',
)
assert.ok(
  canvasSource.includes('event.preventDefault()') && canvasSource.includes('stage.scrollTop = nextScrollTop'),
  'PDF learning canvas should manually scroll the active PDF stage before turning to another page',
)
assert.ok(
  !canvasSource.includes('@scroll="handleStageScroll"'),
  'PDF learning canvas should not turn pages from programmatic scroll events',
)
assert.ok(
  canvasSource.includes('pendingPageScrollPosition'),
  'PDF learning canvas should restore scroll position when turning pages from scroll',
)
assert.ok(
  canvasSource.includes("if (syncScaleFromRender || zoomMode.value !== 'manual') return"),
  'PDF learning canvas should not re-render recursively when fit-width updates the displayed zoom',
)
assert.ok(
  canvasSource.includes('if (isRenderingPage)'),
  'PDF learning canvas should serialize renders so pdf.js does not draw into the same canvas concurrently',
)
assert.ok(
  canvasSource.includes('selectionChange: [payload: PdfSelectionPayload]'),
  'PDF learning canvas should emit structured selection metadata for source-aware agent questions',
)
assert.ok(
  canvasSource.includes('noteSelection: [payload: PdfSelectionPayload]')
    && canvasSource.includes('class="selection-action-popover"')
    && canvasSource.includes('@click="emitNoteSelection"')
    && canvasSource.includes("emitAskAgent('解释当前选区')"),
  'PDF learning canvas should offer a visible selection popover for creating anchored notes or asking the agent',
)
assert.ok(
  canvasSource.includes('function emitNoteSelection')
    && canvasSource.includes("emit('noteSelection', payload)"),
  'PDF learning canvas should convert the current PDF selection into an anchored note payload',
)
assert.ok(
  canvasSource.includes(':style="selectionActionStyle"')
    && canvasSource.includes('selectionActionStyle')
    && canvasSource.includes('resolveSelectionActionRect'),
  'PDF selection note popover should follow the selected PDF region instead of being fixed at the far edge of a zoomed page',
)
assert.ok(
  canvasSource.includes('bbox?: string | null') && canvasSource.includes('active?: boolean'),
  'PDF note anchors should carry source bbox and active state for anchored note rendering',
)
assert.ok(
  canvasSource.includes('resolveNoteAnchorPinStyle') && canvasSource.includes('parseBboxToGeometry(anchor.bbox)'),
  'PDF note anchors should position the margin pin from the saved PDF selection bbox',
)
assert.ok(
  canvasSource.includes('note-anchor-pin--active') && canvasSource.includes('aria-label="打开锚点笔记"'),
  'PDF note anchors should expose a clear active state and accessible open-note action',
)
assert.ok(
  canvasSource.includes('function applyTargetPage')
    && /watch\(\(\) => props\.targetPage,[\s\S]*applyTargetPage\(page[\s\S]*\{ immediate: true \}\)/.test(canvasSource),
  'PDF learning canvas should apply the initial restored target page when it mounts',
)
assert.ok(
  !canvasSource.includes('<p>PDF 学习画布</p>')
    && !canvasSource.includes('<strong>{{ title }}</strong>'),
  'PDF toolbar should not repeat the canvas name or document title already shown by the workspace chrome',
)
assert.ok(
  extractCssBlock(canvasSource, '.pdf-canvas-toolbar').includes('background: #ffffff;')
    && extractCssBlock(canvasSource, '.pdf-canvas-controls button').includes('background: #ffffff;'),
  'PDF canvas toolbar should match the light learning IDE shell instead of rendering as a dark strip',
)
for (const removedToolbarAction of [
  '>上一页</button>',
  '>下一页</button>',
  '@click="copySelectionOrPageText"',
  '@click="highlightSelection"',
]) {
  assert.ok(!canvasSource.includes(removedToolbarAction), `PDF toolbar should remove ${removedToolbarAction}`)
}

for (const requiredWorkspaceFeature of [
  'PdfLearningCanvas',
  'PDF 学习画布',
  ':source-highlight="pdfSourceHighlight"',
  'pdfSourceHighlight',
  'buildCitationHighlight',
  "label: '引用定位'",
  'selectedPdfText',
  'selectedPdfContext',
  'selectedPdfSelectionType',
  'handlePdfSelectionChange',
  "payload.selectionType === 'region'",
  '图表/图片区选区',
  '当前选区',
  '当前页',
]) {
  assert.ok(
    workspaceSource.includes(requiredWorkspaceFeature),
    `translation workspace should integrate ${requiredWorkspaceFeature}`,
  )
}

console.log('pdf-learning-canvas-ok')
