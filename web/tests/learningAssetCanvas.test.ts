import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const canvasSource = readFileSync(
  new URL('../src/components/assistant/LearningAssetCanvas.vue', import.meta.url),
  'utf8',
)

for (const requiredText of [
  '学习资产画布',
  '单词卡',
  'textarea',
  'AI 整理',
  '调整格式',
  '保存',
  'candidateMarkdown',
  '应用候选',
  '取消候选',
  'update:contentMarkdown',
]) {
  assert.ok(canvasSource.includes(requiredText), `learning asset canvas should include ${requiredText}`)
}

assert.ok(!canvasSource.includes('来源：'), 'canvas should not expose a separate source metadata row')

console.log('learning-asset-canvas-ok')
