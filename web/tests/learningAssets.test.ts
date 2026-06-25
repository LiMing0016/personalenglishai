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
assert.ok(draft.draftId, 'learning asset draft should have a stable local draft id for tabs')

const fullAssistantMessage = [
  '### window 这个词怎么学',
  '',
  '> window',
  '',
  '## 1) 核心意思',
  '',
  'window 最常见的意思是“窗户”。',
  '',
  '## 2) 常见用法',
  '',
  '| 用法 | 含义 | 例子 |',
  '|---|---|---|',
  '| a window | 一扇窗 | Please open the window. |',
  '',
  '## 3) 常见搭配',
  '',
  '- open the window：打开窗户',
].join('\n')

const draftFromFullMessage = createLearningAssetDraft({
  conversationId: 'conv-2',
  messageId: 'msg-2',
  title: 'window',
  selectedText: 'window',
  contextText: fullAssistantMessage,
})

assert.equal(draftFromFullMessage.contextText, 'window 最常见的意思是“窗户”。')
assert.ok(draftFromFullMessage.contentMarkdown.includes('**原句：** window 最常见的意思是“窗户”。'))
assert.ok(!draftFromFullMessage.contentMarkdown.includes('## 2) 常见用法'))
assert.ok(!draftFromFullMessage.contentMarkdown.includes('Please open the window.'))

const grammarDraft = createLearningAssetDraft({
  conversationId: 'conv-3',
  messageId: 'msg-3',
  type: 'grammar',
  title: 'a window of opportunity',
  selectedText: 'a window of opportunity',
  contextText: 'This is a window of opportunity to improve your speaking.',
})

assert.equal(grammarDraft.type, 'grammar')
assert.ok(grammarDraft.contentMarkdown.includes('# a window of opportunity'))
assert.ok(grammarDraft.contentMarkdown.includes('**类型：** 语法笔记'))
assert.ok(grammarDraft.contentMarkdown.includes('## 结构拆解'))
assert.ok(grammarDraft.contentMarkdown.includes('**原句：** This is a window of opportunity to improve your speaking.'))

console.log('learning-assets-ok')
