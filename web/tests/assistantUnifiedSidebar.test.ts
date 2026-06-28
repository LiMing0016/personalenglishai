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
  'collapsed-brand-logo',
  'collapsed-brand-icon',
  'sidebar-primary-actions',
  'sidebar-new-chat-button',
  'workspace-nav-grid',
  'workspace-nav-link',
  'workspace-nav-icon',
  '其他应用',
  '学习助手对话',
  'PEAI',
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

console.log('assistant-unified-sidebar-ok')
