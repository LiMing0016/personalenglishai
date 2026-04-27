import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const appLayoutSource = readFileSync(
  new URL('../src/layouts/AppLayout.vue', import.meta.url),
  'utf8',
)

const routerSource = readFileSync(
  new URL('../src/router/index.ts', import.meta.url),
  'utf8',
)

assert.ok(appLayoutSource.includes("label: '学习助手'"), 'app nav should include 学习助手')
assert.ok(appLayoutSource.includes("to: '/app/assistant'"), 'app nav should link to /app/assistant')
assert.ok(routerSource.includes("path: 'assistant'"), 'router should register /app/assistant child path')
assert.ok(
  routerSource.includes("name: 'LearningAssistant'"),
  'router should register LearningAssistant route name',
)

console.log('assistant-routing-ok')
