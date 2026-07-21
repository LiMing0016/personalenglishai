import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

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
const legacyDashboardUrl = new URL('../src/views/DashboardView.vue', import.meta.url)
const assistantRouteBlock = routerSource.slice(
  routerSource.indexOf("path: 'assistant'"),
  routerSource.indexOf("path: 'vocabulary'"),
)
const defaultAppRouteBlock = routerSource.slice(
  routerSource.indexOf("path: ''", routerSource.indexOf("path: '/app'")),
  routerSource.indexOf("path: 'stage-setup'"),
)

assert.ok(appLayoutSource.includes('AppRail'), 'app layout should render the shared app rail')
assert.ok(appRailSource.includes("label: '学习助手'"), 'app rail should include 学习助手')
assert.ok(appRailSource.includes("to: '/app/assistant'"), 'app rail should link to /app/assistant')
assert.ok(
  defaultAppRouteBlock.includes("name: 'LearningAssistant'"),
  '/app should redirect to the named LearningAssistant route',
)
assert.ok(
  defaultAppRouteBlock.includes('query: to.query'),
  '/app redirect should preserve query parameters',
)
assert.ok(
  defaultAppRouteBlock.includes('hash: to.hash'),
  '/app redirect should preserve the URL hash',
)
assert.ok(
  !defaultAppRouteBlock.includes('component:'),
  '/app should not render a second homepage component',
)
assert.ok(
  !routerSource.includes('DashboardView.vue'),
  'router should not import the legacy dashboard page',
)
assert.ok(
  !routerSource.includes("name: 'Dashboard'"),
  'router should not register the legacy Dashboard route name',
)
assert.equal(
  existsSync(legacyDashboardUrl),
  false,
  'legacy DashboardView.vue should be deleted',
)
assert.ok(
  routerSource.includes("path: 'stage-setup'"),
  'stage setup route should remain registered',
)
assert.ok(
  routerSource.includes("if (stageCache.value === '')"),
  'users without a study stage should still trigger the stage guard',
)
assert.ok(
  routerSource.includes("next({ path: '/app/stage-setup', query: { redirect: to.fullPath } })"),
  'stage guard should preserve the original business entry as its redirect target',
)
assert.ok(routerSource.includes("path: 'assistant'"), 'router should register /app/assistant child path')
assert.ok(
  assistantRouteBlock.includes("name: 'LearningAssistant'"),
  'router should register LearningAssistant route name',
)
assert.ok(
  assistantRouteBlock.includes('meta: { immersive: true }'),
  'assistant route should hide the global top nav because assistant navigation lives in the sidebar',
)

console.log('assistant-routing-ok')
