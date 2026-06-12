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

assert.ok(
  packageJson.dependencies?.['pdfjs-dist'],
  'PDF learning canvas should depend on pdfjs-dist instead of browser-native PDF embedding only',
)

for (const requiredCanvasFeature of [
  'pdfjs-dist/build/pdf.mjs',
  'pdf.worker.mjs?url',
  'pdf-canvas-layer',
  'pdf-text-layer',
  'pdf-annotation-layer',
  '高亮选区',
  '复制文本层',
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

for (const requiredWorkspaceFeature of [
  'PdfLearningCanvas',
  'PDF 学习画布',
  'selectedPdfText',
  '当前选区',
  '当前页',
]) {
  assert.ok(
    workspaceSource.includes(requiredWorkspaceFeature),
    `translation workspace should integrate ${requiredWorkspaceFeature}`,
  )
}

console.log('pdf-learning-canvas-ok')
