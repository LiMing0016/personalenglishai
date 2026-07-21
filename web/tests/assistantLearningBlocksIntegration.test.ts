import assert from 'node:assert/strict'

import { fromRemoteConversation } from '../src/pages/app/assistantConversationBlocks.ts'
import { normalizeAssistantBlocks } from '../src/components/assistant/learning-blocks/registry.ts'

const reorderBlock = {
  id: 'reorder-1',
  type: 'sentence_reorder',
  version: 1,
  fallbackMarkdown: '请将 **I / like / English** 排成句子。',
  data: {
    activityId: 'activity-1',
    items: [{
      id: 'item-1',
      instruction: '组成句子',
      tokens: [
        { id: 'i', text: 'I' },
        { id: 'like', text: 'like' },
        { id: 'english', text: 'English' },
      ],
      initialOrder: ['english', 'i', 'like'],
      acceptedOrders: [['i', 'like', 'english']],
    }],
  },
}

{
  const streamed = normalizeAssistantBlocks([reorderBlock])
  const refreshed = fromRemoteConversation({
    id: 'conv-1',
    title: '练习',
    pinned: false,
    archived: false,
    messages: [{
      id: 'assistant-1',
      role: 'assistant',
      content: '开始练习。',
      status: 'done',
      parts: [reorderBlock],
    }],
  })

  assert.deepEqual(refreshed.messages[0]?.parts, streamed)
}

{
  const diagnostics: string[] = []
  const conversation = fromRemoteConversation({
    id: 'conv-legacy',
    title: '旧对话',
    pinned: false,
    archived: false,
    messages: [
      { id: 'plain', role: 'assistant', content: '只有 Markdown。', status: 'done' },
      {
        id: 'future',
        role: 'assistant',
        content: '正文仍需保留。',
        status: 'done',
        parts: [{
          id: 'future-1',
          type: 'future_card',
          version: 9,
          fallbackMarkdown: '## 可读降级内容',
          data: {},
        }],
      },
      {
        id: 'malformed',
        role: 'assistant',
        content: '坏卡片不能删除正文。',
        status: 'done',
        parts: { invalid: true },
      },
    ],
  }, (diagnostic) => diagnostics.push(diagnostic.reason))

  assert.equal(conversation.messages[0]?.parts?.length ?? 0, 0)
  assert.equal(conversation.messages[1]?.parts?.[0]?.type, '__fallback__')
  assert.equal(conversation.messages[2]?.content, '坏卡片不能删除正文。')
  assert.equal(conversation.messages[2]?.parts?.length ?? 0, 0)
  assert.ok(diagnostics.includes('unknown_type'))
  assert.ok(diagnostics.includes('parts_not_array'))
}

console.log('assistant-learning-blocks-integration-ok')
