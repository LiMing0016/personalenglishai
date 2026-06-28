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

assert.ok(appRailSource.includes('src="/brand/peai-logo.png"'), 'shared rail should use the project logo asset')
assert.ok(appRailSource.includes('rail-brand-toggle-icon'), 'shared rail logo should swap to the sidebar icon on hover')
assert.ok(appRailSource.includes('collapsed: boolean'), 'shared rail should accept the global collapsed state')
assert.ok(appRailSource.includes("emit('toggleRail')"), 'shared rail logo should toggle the global rail')
assert.ok(!appRailSource.includes("emit('toggleAssistantDrawer')"), 'shared rail logo should not toggle the assistant drawer')
assert.ok(appRailSource.includes('展开导航'), 'shared rail logo should expose expand navigation copy')
assert.ok(appRailSource.includes('收起导航'), 'shared rail logo should expose collapse navigation copy')
assert.ok(appRailSource.includes("class=\"rail-content\""), 'shared rail should group non-logo content for collapsed rendering')
assert.ok(appRailSource.includes('v-if="!collapsed"'), 'collapsed shared rail should hide non-logo content')
assert.ok(appRailSource.includes('app-rail--collapsed'), 'shared rail should expose a collapsed style class')
assert.ok(appRailSource.includes('position: fixed'), 'collapsed shared rail should float the logo instead of reserving full width')
assert.ok(
  appRailSource.includes('background: inherit'),
  'shared rail should inherit the current page surface instead of forcing its own color',
)
assert.ok(
  appRailSource.includes('border-right: 1px solid var(--app-sidebar-border, #d9e2ec)'),
  'shared rail should use the shared sidebar divider token',
)
assert.ok(appRailSource.includes("label: '阅读'"), 'shared rail vocabulary route should be labeled as reading')
for (const removedShortcut of ["shortLabel: '写'", "shortLabel: '助'", "shortLabel: '词'", "shortLabel: '听'", "shortLabel: '说'"]) {
  assert.ok(!appRailSource.includes(removedShortcut), `shared rail should replace ${removedShortcut} with a logo icon`)
}
for (const iconName of ['assistant', 'writing', 'reading', 'listening', 'speaking']) {
  assert.ok(appRailSource.includes(`skillIcon: '${iconName}'`), `shared rail should include ${iconName} logo icon`)
}
assert.ok(appRailSource.includes('rail-line-icon'), 'shared rail should render lightweight line icons')
assert.ok(!appRailSource.includes('AppRailSkillIcon'), 'shared rail should not use colorful uploaded nav icon cards')
for (const linkTarget of ['/app/writing', '/app/assistant', '/app/vocabulary', '/app/listening', '/app/speaking']) {
  assert.ok(appRailSource.includes(linkTarget), `shared rail should link to ${linkTarget}`)
}
assert.ok(appRailSource.includes('/app/me'), 'shared rail should keep a personal center entry on every app page')
assert.ok(
  appRailSource.includes('rail-profile-link'),
  'shared rail personal center entry should be anchored separately from primary navigation',
)
assert.ok(
  appRailSource.includes("emit('openAssistantDrawer')"),
  'shared rail assistant actions should still open the assistant drawer',
)
console.log('app-rail-chrome-ok')
