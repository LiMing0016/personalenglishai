import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const toolPanelSource = readFileSync(
  new URL('../src/components/writing/ToolPanel.vue', import.meta.url),
  'utf8',
)

const rewritePanelSource = readFileSync(
  new URL('../src/components/writing/panels/RewritePanel.vue', import.meta.url),
  'utf8',
)

assert.ok(
  !toolPanelSource.includes('tool-panel-header'),
  'tool panel should no longer render a title header bar',
)

assert.ok(
  !rewritePanelSource.includes('当前为考试首写锁定状态'),
  'rewrite panel should no longer show the exam first-write warning banner',
)

console.log('writing-panel-chrome-ok')
