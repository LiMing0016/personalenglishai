import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const appLayoutSource = readFileSync(
  new URL('../src/layouts/AppLayout.vue', import.meta.url),
  'utf8',
)
const appRailSource = readFileSync(
  new URL('../src/components/AppRail.vue', import.meta.url),
  'utf8',
)

const routerSource = readFileSync(
  new URL('../src/router/index.ts', import.meta.url),
  'utf8',
)
const assistantRouteBlock = routerSource.slice(
  routerSource.indexOf("path: 'assistant'"),
  routerSource.indexOf("path: 'vocabulary'"),
)

assert.ok(appLayoutSource.includes('AppRail'), 'app layout should render the shared app rail')
assert.ok(appRailSource.includes("label: '学习助手'"), 'app rail should include 学习助手')
assert.ok(appRailSource.includes("to: '/app/assistant'"), 'app rail should link to /app/assistant')
assert.ok(routerSource.includes("path: 'assistant'"), 'router should register /app/assistant child path')
assert.ok(
  routerSource.includes("name: 'LearningAssistant'"),
  'router should register LearningAssistant route name',
)
assert.ok(
  assistantRouteBlock.includes('meta: { immersive: true }'),
  'assistant route should hide the global top nav because assistant navigation lives in the sidebar',
)

console.log('assistant-routing-ok')
