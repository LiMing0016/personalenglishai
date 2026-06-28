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
assert.ok(chatViewSource.includes('appendToLearningAsset'))
assert.ok(chatViewSource.includes('selectionchange'))
assert.ok(chatViewSource.includes('data-learning-message-id'))
assert.ok(chatViewSource.includes('handleLearningAssetSelection(message)'))
assert.ok((chatViewSource.match(/@mouseup="handleLearningAssetSelection\(message\)"/g) ?? []).length >= 2)
assert.ok(!chatViewSource.includes('[data-assistant-message-id]'), 'learning asset selection should work for user and assistant messages')
assert.ok(!chatViewSource.includes("message.role !== 'assistant'"), 'learning asset selection should not reject user messages')
assert.ok(
  chatViewSource.includes('background: #ffffff;') && chatViewSource.includes('border: 1px solid #dbe3ea;'),
  'assistant markdown code blocks should use a light document-style surface',
)
assert.ok(!chatViewSource.includes('background: #1f2937;'), 'assistant markdown code blocks should not use a dark code surface')
assert.ok(chatViewSource.includes('resolveSelectionToolbarTop'))
assert.ok(chatViewSource.includes('selectionToolbar.messageId = message.id'))
assert.ok(chatViewSource.includes('messageId: selectionToolbar.messageId'))
assert.ok(toolbarSource.includes('+ 单词卡'))
assert.ok(toolbarSource.includes('+ 语法'))
assert.ok(toolbarSource.includes('加入当前'))
assert.ok(toolbarSource.includes('selectedText'))
assert.ok(toolbarSource.includes('v-if="selectedText"'))
assert.ok(!toolbarSource.includes('{{ selectedText }}'), 'selection toolbar should not repeat selected text')
assert.ok(!toolbarSource.includes('selection-text'), 'selection toolbar should not render selected text as a card title')
assert.ok(pageSource.includes('@create-learning-asset'))
assert.ok(pageSource.includes('@append-to-learning-asset'))
assert.ok(pageSource.includes('handleCreateLearningAsset'))
assert.ok(pageSource.includes('handleAppendToLearningAsset'))
assert.ok(pageSource.includes('LearningAssetCanvas'))

console.log('assistant-learning-asset-selection-ok')
