import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const appLayoutSource = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
const appRailSource = readFileSync(new URL('../src/components/AppRail.vue', import.meta.url), 'utf8')

assert.ok(appLayoutSource.includes('AppRail'), 'app layout should render the shared left rail')
assert.ok(appLayoutSource.includes('assistantDrawerOpen'), 'app layout should own shared assistant drawer visibility')
assert.ok(appLayoutSource.includes("provide('assistantDrawerOpen'"), 'app layout should provide drawer state to pages')
assert.ok(
  appLayoutSource.includes('@toggle-assistant-drawer="toggleAssistantDrawer"'),
  'app layout should let the rail logo toggle the assistant drawer',
)
assert.ok(
  appLayoutSource.includes('--app-sidebar-border'),
  'app layout should define a shared sidebar border token',
)
assert.ok(!appLayoutSource.includes('class="app-nav"'), 'top app nav should be replaced by the left rail')

assert.ok(appRailSource.includes('src="/brand/peai-logo.png"'), 'shared rail should use the project logo asset')
assert.ok(appRailSource.includes('assistantDrawerOpen'), 'shared rail should receive drawer state for the logo toggle')
assert.ok(appRailSource.includes("emit('toggleAssistantDrawer')"), 'shared rail logo should toggle the assistant drawer')
assert.ok(appRailSource.includes('打开边栏'), 'shared rail logo should expose GPT-like open sidebar copy')
assert.ok(
  appRailSource.includes('border: 1px solid var(--app-sidebar-border'),
  'shared rail logo button should use the same bordered control style as the sidebar shell',
)
for (const linkText of ['写', '助', '词', '听', '说']) {
  assert.ok(appRailSource.includes(linkText), `shared rail should include ${linkText}`)
}
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
  'shared rail logo and assistant actions should be able to open the assistant drawer',
)

console.log('app-rail-chrome-ok')
