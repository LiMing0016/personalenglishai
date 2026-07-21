import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

const stateModuleUrl = new URL(
  '../src/pages/app/assistantSidebarState.ts',
  import.meta.url,
)

assert.ok(
  existsSync(stateModuleUrl),
  'assistant sidebar should expose a testable adaptive-collapse policy',
)

const { ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH, shouldAutoCollapseAssistantSidebar } = await import(
  stateModuleUrl.href
)

assert.equal(ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH, 1280)
assert.equal(
  shouldAutoCollapseAssistantSidebar({ learningCanvasOpen: true, viewportWidth: 1600 }),
  true,
  'opening the learning canvas should collapse the conversation drawer',
)
assert.equal(
  shouldAutoCollapseAssistantSidebar({ learningCanvasOpen: false, viewportWidth: 1280 }),
  true,
  'the breakpoint itself should use the compact assistant rail',
)
assert.equal(
  shouldAutoCollapseAssistantSidebar({ learningCanvasOpen: false, viewportWidth: 1281 }),
  false,
  'wide assistant pages without a learning canvas may keep the drawer open',
)

const pageSource = readFileSync(
  new URL('../src/pages/app/AssistantPage.vue', import.meta.url),
  'utf8',
)
const sidebarSource = readFileSync(
  new URL('../src/components/assistant/AssistantSidebar.vue', import.meta.url),
  'utf8',
)

for (const requiredText of [
  'viewportWidth',
  "window.addEventListener('resize'",
  "window.removeEventListener('resize'",
  'shouldAutoCollapseAssistantSidebar',
  'assistant-page--sidebar-constrained',
]) {
  assert.ok(pageSource.includes(requiredText), `assistant page should support adaptive collapse: ${requiredText}`)
}

assert.ok(sidebarSource.includes('AppNavigationMenu'), 'assistant sidebar should reuse the shared app navigation')
assert.ok(sidebarSource.includes('requestOpenSidebar'), 'compact navigation should still be expandable')
for (const removedText of [
  'appSwitcherOpen',
  ':aria-expanded="appSwitcherOpen"',
  'sidebar-app-switcher',
  'collapsed-home-link',
  'aria-label="返回首页"',
]) {
  assert.ok(!sidebarSource.includes(removedText), `assistant sidebar should remove obsolete navigation: ${removedText}`)
}

assert.ok(
  !sidebarSource.includes('@mousedown.prevent="requestOpenSidebar"'),
  'opening the collapsed rail should not forward the same pointer gesture into the PEAI app menu',
)

console.log('assistant-adaptive-sidebar-ok')
