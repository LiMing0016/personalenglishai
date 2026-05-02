import test from 'node:test'
import assert from 'node:assert/strict'

import {
  mergeRemoteConversationListWithTransientAttachments,
  mergeTransientMessageAttachments,
} from './assistantConversationMerge.ts'
import type { AssistantAttachment, AssistantConversation } from './assistantMock.ts'

function imageAttachment(id: string): AssistantAttachment {
  return {
    id,
    name: `${id}.png`,
    size: 5,
    type: 'image/png',
    kind: 'image',
    file: new File(['image'], `${id}.png`, { type: 'image/png' }),
  }
}

function conversation(messages: AssistantConversation['messages']): AssistantConversation {
  return {
    id: 'conv-1',
    title: '新对话',
    summary: '',
    updatedAt: 1,
    messages,
  }
}

test('mergeTransientMessageAttachments preserves local attachments after remote refresh', () => {
  const local = conversation([
    {
      id: 'local-user',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
      attachments: [imageAttachment('screenshot')],
    },
    {
      id: 'local-assistant',
      role: 'assistant',
      content: '正在思考...',
      status: 'loading',
    },
  ])
  const remote = conversation([
    {
      id: 'remote-user',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
    },
    {
      id: 'remote-assistant',
      role: 'assistant',
      content: '译文',
      status: 'done',
    },
  ])

  const merged = mergeTransientMessageAttachments(local, remote)

  assert.equal(merged.messages[0]?.attachments?.length, 1)
  assert.equal(merged.messages[0]?.attachments?.[0]?.name, 'screenshot.png')
  assert.equal(merged.messages[1]?.attachments, undefined)
})

test('mergeTransientMessageAttachments matches duplicate user messages in order', () => {
  const local = conversation([
    {
      id: 'local-user-1',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
      attachments: [imageAttachment('first')],
    },
    {
      id: 'local-user-2',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
      attachments: [imageAttachment('second')],
    },
  ])
  const remote = conversation([
    {
      id: 'remote-user-1',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
    },
    {
      id: 'remote-user-2',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
    },
  ])

  const merged = mergeTransientMessageAttachments(local, remote)

  assert.equal(merged.messages[0]?.attachments?.[0]?.name, 'first.png')
  assert.equal(merged.messages[1]?.attachments?.[0]?.name, 'second.png')
})

test('mergeTransientMessageAttachments preserves metadata before blobs are hydrated', () => {
  const local = conversation([
    {
      id: 'local-user',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
      attachmentMetadata: [
        {
          id: 'screenshot',
          name: 'screenshot.png',
          size: 5,
          type: 'image/png',
          kind: 'image',
        },
      ],
    },
  ])
  const remote = conversation([
    {
      id: 'remote-user',
      role: 'user',
      content: '翻译成中文',
      status: 'done',
    },
  ])

  const merged = mergeTransientMessageAttachments(local, remote)

  assert.equal(merged.messages[0]?.attachmentMetadata?.[0]?.name, 'screenshot.png')
})

test('mergeRemoteConversationListWithTransientAttachments preserves attachments from current conversations', () => {
  const current = conversation([
    {
      id: 'local-user',
      role: 'user',
      content: '翻译一下图片内容',
      status: 'done',
      attachments: [imageAttachment('screenshot')],
    },
  ])
  const remote = conversation([
    {
      id: 'remote-user',
      role: 'user',
      content: '翻译一下图片内容',
      status: 'done',
    },
  ])

  const [merged] = mergeRemoteConversationListWithTransientAttachments([current], [remote])

  assert.equal(merged?.messages[0]?.attachments?.[0]?.name, 'screenshot.png')
})

test('mergeRemoteConversationListWithTransientAttachments keeps current messages when remote summary has no messages', () => {
  const current = conversation([
    {
      id: 'local-user',
      role: 'user',
      content: '翻译一下图片内容',
      status: 'done',
      attachments: [imageAttachment('screenshot')],
    },
  ])
  const remoteSummary = conversation([])

  const [merged] = mergeRemoteConversationListWithTransientAttachments([current], [remoteSummary])

  assert.equal(merged?.messages[0]?.content, '翻译一下图片内容')
  assert.equal(merged?.messages[0]?.attachments?.[0]?.name, 'screenshot.png')
})
