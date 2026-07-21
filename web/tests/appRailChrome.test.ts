import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const appLayoutSource = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
const appRailSource = readFileSync(new URL('../src/components/AppRail.vue', import.meta.url), 'utf8')

assert.ok(appLayoutSource.includes('AppRail'), 'app layout should render the shared left rail')
assert.ok(
  appLayoutSource.includes('v-if="!isAssistantRoute"'),
  'app layout should hide the shared rail when the assistant page owns its learning sidebar',
)
assert.ok(appLayoutSource.includes('assistantDrawerOpen'), 'app layout should own shared assistant drawer visibility')
assert.ok(appLayoutSource.includes("provide('assistantDrawerOpen'"), 'app layout should provide drawer state to pages')
assert.ok(
  appLayoutSource.includes("peai:app-rail-collapsed"),
  'app layout should persist the shared rail collapse preference',
)
assert.ok(
  appLayoutSource.includes('app-layout--rail-collapsed'),
  'app layout should expose a collapsed rail state class',
)
assert.ok(
  appLayoutSource.includes('app-layout--rail-expanded'),
  'app layout should expose an expanded rail state class',
)
assert.ok(
  appLayoutSource.includes(':collapsed="railCollapsed"'),
  'app layout should pass the shared collapse state to AppRail',
)
assert.ok(
  appLayoutSource.includes('@toggle-rail="toggleRail"'),
  'app layout should let the rail logo toggle the shared rail',
)
assert.ok(
  !appLayoutSource.includes('@toggle-assistant-drawer="toggleAssistantDrawer"'),
  'app layout should not let the rail logo toggle the assistant drawer',
)
assert.ok(
  appLayoutSource.includes('localStorage.getItem') && appLayoutSource.includes('localStorage.setItem'),
  'app layout should read and write the rail collapse preference with localStorage',
)
assert.ok(
  appLayoutSource.includes('function openAssistantDrawer()'),
  'app layout should keep assistant drawer opening for assistant action buttons',
)
assert.ok(
  appLayoutSource.includes('function toggleRail()'),
  'app layout should keep global rail toggling separate from assistant drawer logic',
)
assert.ok(
  !appLayoutSource.includes('function toggleAssistantDrawer()'),
  'app layout should remove the global rail logo assistant drawer toggle',
)
assert.ok(
  appLayoutSource.includes('--app-sidebar-border'),
  'app layout should define a shared sidebar border token',
)
assert.ok(!appLayoutSource.includes('class="app-nav"'), 'top app nav should be replaced by the left rail')

assert.ok(appRailSource.includes('collapsed: boolean'), 'shared rail should accept the global collapsed state')
assert.ok(appRailSource.includes("emit('toggleRail')"), 'shared rail logo should toggle the global rail')
assert.ok(!appRailSource.includes("emit('toggleAssistantDrawer')"), 'shared rail logo should not toggle the assistant drawer')
assert.ok(appRailSource.includes('app-rail--collapsed'), 'shared rail should expose a collapsed style class')
assert.ok(appRailSource.includes('AppNavigationMenu'), 'shared rail should reuse the unified navigation menu')
assert.ok(appRailSource.includes('rail-context-section'), 'shared rail should expose a contextual app section')
assert.ok(appRailSource.includes('写作空间'), 'writing routes should expose a writing workspace section')
assert.ok(appRailSource.includes('to="/app/writing/mode"'), 'writing workspace should link to the new essay flow')
assert.ok(appRailSource.includes('to="/app/writing/dashboard"'), 'writing workspace should link to dashboard')
assert.ok(appRailSource.includes('flex: 0 0 218px'), 'expanded rail should reserve 218px')
assert.ok(appRailSource.includes('flex-basis: 72px'), 'compact rail should reserve 72px')
assert.ok(
  appRailSource.includes('border-right: 1px solid var(--app-sidebar-border, #d9e2ec)'),
  'shared rail should use the shared sidebar divider token',
)
assert.ok(!appRailSource.includes("label: '工具箱'"), 'shared rail should remove the obsolete toolbox shortcut')
assert.ok(!appRailSource.includes('AppRailSkillIcon'), 'shared rail should not use colorful uploaded nav icon cards')
assert.ok(appRailSource.includes('/app/me'), 'shared rail should keep a personal center entry on every app page')
assert.ok(
  appRailSource.includes('rail-profile-link'),
  'shared rail personal center entry should be anchored separately from primary navigation',
)
console.log('app-rail-chrome-ok')
