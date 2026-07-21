import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const layoutSource = readFileSync(new URL('../src/layouts/AppLayout.vue', import.meta.url), 'utf8')
const pageSource = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const sidebarSource = readFileSync(
  new URL('../src/components/assistant/AssistantSidebar.vue', import.meta.url),
  'utf8',
)

for (const requiredText of [
  'isAssistantRoute',
  'v-if="!isAssistantRoute"',
  'app-layout--assistant',
]) {
  assert.ok(layoutSource.includes(requiredText), `app layout should wire assistant sidebar shell: ${requiredText}`)
}

for (const requiredText of [
  ':collapsed="!assistantDrawerOpen"',
  ':requestOpenSidebar="openAssistantDrawer"',
  '--assistant-sidebar-collapsed-width',
  '--assistant-sidebar-current-width: var(--assistant-sidebar-collapsed-width)',
]) {
  assert.ok(pageSource.includes(requiredText), `assistant page should use unified sidebar: ${requiredText}`)
}

assert.ok(!pageSource.includes('v-if="assistantDrawerOpen"'), 'assistant sidebar should render in collapsed and expanded states')
assert.ok(!pageSource.includes('--app-rail-width: 64px'), 'assistant page should not reserve the global app rail width')

for (const requiredText of [
  'collapsed: boolean',
  'requestOpenSidebar',
  'assistant-sidebar--collapsed',
  'collapsed-sidebar',
  'collapsed-sidebar-button',
  'AppNavigationMenu',
  '助手空间',
  'sidebar-primary-actions',
  'sidebar-new-chat-button',
  '学习助手对话',
  '新聊天',
  '搜索',
  '文件夹',
  '最近',
  '个人中心',
]) {
  assert.ok(sidebarSource.includes(requiredText), `assistant sidebar should expose unified shell: ${requiredText}`)
}

assert.ok(!sidebarSource.includes('AppRailSkillIcon'), 'assistant sidebar expanded app switcher should use quiet line icons')
assert.ok(!sidebarSource.includes('assistant-workspace-card'), 'assistant sidebar should not show a redundant workspace intro card')
assert.ok(sidebarSource.includes('flex: 0 0 218px'), 'expanded assistant sidebar should reserve 218px')
assert.ok(sidebarSource.includes('flex-basis: 72px'), 'compact assistant sidebar should reserve 72px')
assert.ok(!sidebarSource.includes('appSwitcherOpen'), 'assistant sidebar should remove the PEAI app dropdown state')
assert.ok(!sidebarSource.includes('collapsed-home-link'), 'assistant sidebar should remove the obsolete home shortcut')
assert.ok(!sidebarSource.includes('aria-label="返回首页"'), 'assistant sidebar should not expose a home shortcut')
assert.ok(!sidebarSource.includes('sidebar-app-switcher'), 'assistant sidebar should remove the old app switcher')

console.log('assistant-unified-sidebar-ok')
