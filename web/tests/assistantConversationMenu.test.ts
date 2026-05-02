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
assert.ok(conversationListSource.includes('分享'), 'menu should keep the share action')
assert.ok(conversationListSource.includes('重命名'), 'menu should keep the rename action')
assert.ok(conversationListSource.includes('移动到文件夹'), 'menu should keep the move-to-folder action')
assert.ok(conversationListSource.includes('move-folder-submenu'), 'move action should open an inline folder submenu')
assert.ok(conversationListSource.includes('新文件夹'), 'folder submenu should allow creating a new folder')
assert.ok(conversationListSource.includes('移出文件夹'), 'folder submenu should allow moving out of folders')
assert.ok(conversationListSource.includes('folders:'), 'conversation list should receive existing folders')
assert.ok(!conversationListSource.includes('移动到项目'), 'menu copy should use 文件夹 instead of 项目')
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
