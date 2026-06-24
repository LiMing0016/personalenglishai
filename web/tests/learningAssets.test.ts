import assert from 'node:assert/strict'

import {
  createDefaultVocabularyMarkdown,
  createLearningAssetDraft,
  isLearningAssetType,
  normalizeLearningAssetType,
} from '../src/types/learningAssets.ts'

assert.equal(normalizeLearningAssetType('VOCABULARY'), 'vocabulary')
assert.equal(normalizeLearningAssetType('grammar'), 'grammar')
assert.equal(normalizeLearningAssetType('unknown'), 'vocabulary')
assert.equal(isLearningAssetType('sentence'), true)
assert.equal(isLearningAssetType('word'), false)

const markdown = createDefaultVocabularyMarkdown({
  title: 'nuanced',
  selectedText: 'nuanced',
  contextText: 'A nuanced answer considers different sides.',
})

for (const requiredText of [
  '# nuanced',
  '**词性：**',
  '**中文释义：**',
  '**English meaning：**',
  '**原句：** A nuanced answer considers different sides.',
  '**AI 例句：**',
  '**常见搭配：**',
  '## 我的笔记',
]) {
  assert.ok(markdown.includes(requiredText), `default vocabulary markdown should include ${requiredText}`)
}

const draft = createLearningAssetDraft({
  conversationId: 'conv-1',
  messageId: 'msg-1',
  title: '  nuanced  ',
  selectedText: 'nuanced',
  contextText: 'A nuanced answer considers different sides.',
})

assert.equal(draft.type, 'vocabulary')
assert.equal(draft.title, 'nuanced')
assert.equal(draft.sourceConversationId, 'conv-1')
assert.equal(draft.sourceMessageId, 'msg-1')
assert.ok(draft.contentMarkdown.startsWith('# nuanced'))
assert.ok(draft.updatedAt > 0)

console.log('learning-assets-ok')
