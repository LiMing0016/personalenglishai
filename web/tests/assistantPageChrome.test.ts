import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantPageSource = readFileSync(
  new URL('../src/pages/app/AssistantPage.vue', import.meta.url),
  'utf8',
)

const chatPanelSource = readFileSync(
  new URL('../src/components/writing/panels/ChatPanel.vue', import.meta.url),
  'utf8',
)
const composerSource = readFileSync(
  new URL('../src/components/assistant/AssistantComposer.vue', import.meta.url),
  'utf8',
)

assert.ok(assistantPageSource.includes('今天想练什么？'), 'assistant page should show the empty-state greeting')
assert.ok(assistantPageSource.includes('学习助手'), 'assistant page should include the page title')
assert.ok(assistantPageSource.includes('AssistantComposer'), 'assistant page should render the AssistantComposer')
assert.ok(
  assistantPageSource.includes('composerDocked'),
  'assistant page should dock the composer at the bottom of the layout',
)
assert.ok(
  assistantPageSource.includes('position: fixed;'),
  'assistant page should pin the composer to the viewport bottom',
)
assert.ok(
  assistantPageSource.includes('bottom: 0;'),
  'assistant page should anchor the composer to the page bottom edge',
)
assert.ok(
  assistantPageSource.includes('handleFileSelect'),
  'assistant page should wire attachment selection into the page shell',
)
assert.ok(
  assistantPageSource.includes('newFolderName'),
  'assistant page should use an in-page dialog for creating folders during move',
)
assert.ok(
  assistantPageSource.includes('folderDialogMode'),
  'assistant page should distinguish create-only and create-and-move folder dialogs',
)
assert.ok(
  assistantPageSource.includes('openCreateFolderOnlyDialog'),
  'assistant page should support creating a folder from the folder section',
)
assert.ok(
  assistantPageSource.includes('handleMoveConversationToFolder'),
  'assistant page should support direct move-to-folder menu actions',
)
assert.ok(
  assistantPageSource.includes('folderConversationGroups'),
  'assistant page should build visible conversation groups for each created folder',
)
assert.ok(
  assistantPageSource.includes('conversation.projectId === null'),
  'assistant page should keep filed conversations out of the recent section',
)
assert.ok(!assistantPageSource.includes('移动到项目'), 'assistant page copy should use 文件夹 instead of 项目')
assert.ok(
  chatPanelSource.includes('AI 对话（本地 mock，后续接 GPT）'),
  'writing page chat panel should stay untouched',
)
assert.ok(composerSource.includes('type="file"'), 'assistant composer should support file/photo selection')
assert.ok(composerSource.includes('附件'), 'assistant composer should render attachment UI copy')

console.log('assistant-page-chrome-ok')
