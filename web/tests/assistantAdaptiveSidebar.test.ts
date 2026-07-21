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

const {
  ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH,
  DEFAULT_ASSISTANT_SIDEBAR_WIDTH,
  MAX_ASSISTANT_SIDEBAR_WIDTH,
  MIN_ASSISTANT_SIDEBAR_WIDTH,
  clampAssistantSidebarWidth,
  shouldAutoCollapseAssistantSidebar,
} = await import(
  stateModuleUrl.href
)

assert.equal(ASSISTANT_SIDEBAR_AUTO_COLLAPSE_WIDTH, 1280)
assert.equal(DEFAULT_ASSISTANT_SIDEBAR_WIDTH, 218)
assert.equal(MIN_ASSISTANT_SIDEBAR_WIDTH, 200)
assert.equal(MAX_ASSISTANT_SIDEBAR_WIDTH, 360)
assert.equal(clampAssistantSidebarWidth(160), 200)
assert.equal(clampAssistantSidebarWidth(284), 284)
assert.equal(clampAssistantSidebarWidth(420), 360)
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

const sidebarPanelLayoutStart = sidebarSource.indexOf('.collapsed-sidebar,\n.sidebar-panel {')
const sidebarPanelLayout = sidebarSource.slice(
  sidebarPanelLayoutStart,
  sidebarSource.indexOf('.collapsed-sidebar {', sidebarPanelLayoutStart),
)

assert.ok(
  sidebarPanelLayout.includes('min-width: 0;'),
  'expanded assistant sidebar panel should be allowed to shrink inside the resizable sidebar',
)

for (const requiredText of [
  'viewportWidth',
  "window.addEventListener('resize'",
  "window.removeEventListener('resize'",
  'shouldAutoCollapseAssistantSidebar',
  'assistant-page--sidebar-constrained',
  'assistant-sidebar-resize-handle',
  'role="separator"',
  'aria-orientation="vertical"',
  '@pointerdown="startAssistantSidebarResize"',
  "'--assistant-sidebar-width': `${assistantSidebarWidth.value}px`",
  'clampAssistantSidebarWidth',
  'cursor: col-resize',
  'background: #cbd5e1',
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
