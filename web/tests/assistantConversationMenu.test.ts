import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const conversationListSource = readFileSync(
  new URL('../src/components/assistant/AssistantConversationList.vue', import.meta.url),
  'utf8',
)

assert.ok(
  conversationListSource.includes('conversation-menu-button'),
  'conversation actions should be collapsed behind one menu button',
)
assert.ok(
  conversationListSource.includes('conversation-action-menu'),
  'conversation list should render a popover menu for actions',
)
assert.ok(
  conversationListSource.includes('<Teleport to="body">'),
  'conversation action menu should be teleported to document.body so sidebar overflow cannot clip it',
)
assert.ok(
  conversationListSource.includes('getBoundingClientRect'),
  'conversation action menu should be positioned from the trigger button rect',
)
assert.ok(
  conversationListSource.includes('position: fixed'),
  'conversation action menu should use viewport-fixed positioning',
)
assert.ok(
  conversationListSource.includes("document.addEventListener('keydown'"),
  'conversation action menu should close on Esc',
)
assert.ok(
  conversationListSource.includes("window.addEventListener('scroll'"),
  'conversation action menu should close on page/sidebar scroll',
)
assert.ok(conversationListSource.includes('分享'), 'menu should keep the share action')
assert.ok(conversationListSource.includes('重命名'), 'menu should keep the rename action')
assert.ok(conversationListSource.includes('移动到项目'), 'menu should keep the move action')
assert.ok(conversationListSource.includes('置顶聊天'), 'menu should keep the pin action')
assert.ok(conversationListSource.includes('取消置顶'), 'menu should support unpinning pinned conversations')
assert.ok(conversationListSource.includes('归档'), 'menu should keep the archive action')
assert.ok(conversationListSource.includes('删除'), 'menu should keep the delete action')
assert.ok(
  !conversationListSource.includes('title="分享" @click="$emit'),
  'share should no longer be a row-level exposed icon button',
)
assert.ok(
  !conversationListSource.includes('title="重命名" @click="$emit'),
  'rename should no longer be a row-level exposed icon button',
)

console.log('assistant-conversation-menu-ok')
