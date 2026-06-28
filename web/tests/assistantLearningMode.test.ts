import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const composerSource = readFileSync(
  new URL('../src/components/assistant/AssistantComposer.vue', import.meta.url),
  'utf8',
)
const pageSource = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const canvasSource = readFileSync(
  new URL('../src/components/assistant/LearningAssetCanvas.vue', import.meta.url),
  'utf8',
)
const mockSource = readFileSync(new URL('../src/pages/app/assistantMock.ts', import.meta.url), 'utf8')

assert.ok(mockSource.includes("export type AssistantMode = 'default' | 'exam' | 'learning'"))
assert.ok(composerSource.includes('学习模式'))
assert.ok(composerSource.includes('边问边整理笔记'))
assert.ok(composerSource.includes("assistantMode === 'learning'"))
assert.ok(composerSource.includes("toggleLearningMode"))
assert.ok(composerSource.includes('composerPlaceholder'))
assert.ok(composerSource.includes('placeholder?: string'), 'composer should allow pages to override placeholder copy')
assert.ok(
  composerSource.includes(':placeholder="visibleComposerPlaceholder"'),
  'composer textarea should render the page-aware placeholder',
)
assert.ok(composerSource.includes('composer-bottom'), 'composer tools should live in the bottom toolbar')
assert.ok(!composerSource.includes('attach-label'), 'composer should not show the vertical 更多 label')
assert.ok(pageSource.includes('learningCanvasOpen'))
assert.ok(pageSource.includes('handleSetAssistantMode'))
assert.ok(pageSource.includes(":draft=\"learningAssetDraft\""))
assert.ok(
  pageSource.includes(':placeholder="learningCanvasOpen ? \'\' : undefined"'),
  'learning canvas should suppress textarea placeholder copy',
)
assert.ok(
  (pageSource.match(/right: var\(--learning-canvas-current-width\);/g) ?? []).length >= 2,
  'composer dock should respect the learning canvas width at desktop and narrow viewports',
)
assert.ok(!pageSource.includes('right: 0;\n    padding: 14px 12px'), 'composer dock should not slide under the canvas on narrow viewports')
assert.ok(canvasSource.includes('draft: LearningAssetDraft | null'))
assert.ok(canvasSource.includes('canvas-empty-state'))
assert.ok(canvasSource.includes('选择左侧内容创建单词卡'))
assert.ok(canvasSource.includes('width: min(100vw, var(--canvas-width));'), 'narrow canvas width should use the same variable as the composer dock offset')
assert.ok(!canvasSource.includes('width: min(100vw, 420px);'), 'narrow canvas width should not drift from the stored canvas width')

console.log('assistant-learning-mode-ok')
