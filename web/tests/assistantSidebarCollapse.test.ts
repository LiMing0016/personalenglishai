import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantPageSource = readFileSync(
  new URL('../src/pages/app/AssistantPage.vue', import.meta.url),
  'utf8',
)

const sidebarSource = readFileSync(
  new URL('../src/components/assistant/AssistantSidebar.vue', import.meta.url),
  'utf8',
)

assert.ok(
  assistantPageSource.includes('assistantDrawerOpen'),
  'assistant page should consume the shared assistant drawer state',
)
assert.ok(
  assistantPageSource.includes('--assistant-sidebar-current-width'),
  'assistant page should expose the current sidebar width for docked composer alignment',
)
assert.ok(
  assistantPageSource.includes("inject<Ref<boolean> | null>('assistantDrawerOpen'"),
  'assistant page should inject drawer visibility from the app layout',
)
assert.ok(
  assistantPageSource.includes('@close-sidebar="closeAssistantDrawer"'),
  'assistant page should let the sidebar close the drawer panel',
)
assert.ok(
  sidebarSource.includes('sidebar-panel'),
  'assistant sidebar should isolate expanded content in a hideable panel',
)
assert.ok(
  sidebarSource.includes('border-right: 1px solid var(--app-sidebar-border'),
  'assistant sidebar should share the same border style as the global rail',
)
assert.ok(
  sidebarSource.includes('sidebar-folder'),
  'assistant sidebar should organize left content into folder sections',
)
assert.ok(
  sidebarSource.includes('文件夹'),
  'assistant sidebar should expose a folder section',
)
assert.ok(!sidebarSource.includes('项目'), 'assistant sidebar should use 文件夹 terminology instead of 项目')
assert.ok(
  sidebarSource.includes('最近'),
  'assistant sidebar should place recent conversations inside a recent folder section',
)
assert.ok(
  sidebarSource.includes('recentFolderOpen'),
  'recent conversation folder should be collapsible without changing conversation state',
)
assert.ok(
  sidebarSource.includes('createFolder'),
  'folder section should expose a create-folder action',
)
assert.ok(
  sidebarSource.includes('title="创建文件夹"'),
  'folder section should provide a visible create-folder affordance',
)
assert.ok(
  sidebarSource.includes('folderGroups'),
  'folder section should render created folders instead of only exposing a create action',
)
assert.ok(
  sidebarSource.includes('暂无对话'),
  'empty folders should still show an empty state after they are created',
)
assert.ok(
  sidebarSource.includes('toggleConversationFolder'),
  'created folders should be expandable to reveal their conversations',
)
assert.ok(
  sidebarSource.includes('sidebar-brand'),
  'expanded assistant drawer should keep PEAI brand context',
)
assert.ok(
  sidebarSource.includes('to="/app/me"'),
  'expanded assistant drawer footer should link to the personal center',
)
assert.ok(
  sidebarSource.includes('sidebar-profile-link'),
  'expanded assistant drawer should render a GPT-like profile entry at the bottom',
)
assert.ok(
  !sidebarSource.includes('PEAI 学习助手'),
  'assistant drawer footer should no longer hide the personal center behind static product copy',
)
assert.ok(
  !sidebarSource.includes('sidebar-rail'),
  'assistant drawer should not duplicate the shared global rail',
)
assert.ok(
  !sidebarSource.includes('src="/brand/peai-logo.png"'),
  'assistant drawer should not own the global rail logo',
)
assert.ok(
  sidebarSource.includes('closeSidebar'),
  'assistant drawer should expose a close event for the page shell',
)
assert.ok(
  !sidebarSource.includes('background: #1f1f1f;'),
  'collapsed rail should not introduce a dark theme that clashes with PEAI',
)

console.log('assistant-sidebar-collapse-ok')
