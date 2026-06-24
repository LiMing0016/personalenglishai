import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const chatViewSource = readFileSync(
  new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url),
  'utf8',
)
const toolbarSource = readFileSync(
  new URL('../src/components/assistant/LearningAssetSelectionToolbar.vue', import.meta.url),
  'utf8',
)
const pageSource = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')

assert.ok(chatViewSource.includes('LearningAssetSelectionToolbar'))
assert.ok(chatViewSource.includes('createLearningAsset'))
assert.ok(chatViewSource.includes('selectionchange'))
assert.ok(chatViewSource.includes('selectionToolbar.messageId = message.id'))
assert.ok(chatViewSource.includes('messageId: selectionToolbar.messageId'))
assert.ok(toolbarSource.includes('新建单词卡'))
assert.ok(toolbarSource.includes('selectedText'))
assert.ok(pageSource.includes('@create-learning-asset'))
assert.ok(pageSource.includes('handleCreateLearningAsset'))
assert.ok(pageSource.includes('LearningAssetCanvas'))

console.log('assistant-learning-asset-selection-ok')
