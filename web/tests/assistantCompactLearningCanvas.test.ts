import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stateModuleUrl = new URL('../src/pages/app/assistantSidebarState.ts', import.meta.url)
const state = await import(stateModuleUrl.href)
const page = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const canvas = readFileSync(
  new URL('../src/components/assistant/LearningAssetCanvas.vue', import.meta.url),
  'utf8',
)

assert.equal(state.COMPACT_LEARNING_CANVAS_WIDTH, 1024)
assert.equal(state.shouldUseCompactLearningCanvas?.(1024), true)
assert.equal(state.shouldUseCompactLearningCanvas?.(1025), false)

for (const text of [
  '学习成果',
  'compactLearningCanvas',
  'compactLearningCanvasOpen',
  'aria-controls="learning-asset-canvas"',
  ':aria-expanded="learningCanvasVisible"',
  'assistant-page--compact-learning-canvas',
]) {
  assert.ok(page.includes(text), `missing compact learning canvas contract: ${text}`)
}

assert.ok(canvas.includes('id="learning-asset-canvas"'))
assert.ok(canvas.includes('@media (max-width: 1024px)'))
assert.ok(canvas.includes('width: min(100vw, 420px)'))

console.log('assistant-compact-learning-canvas-ok')
