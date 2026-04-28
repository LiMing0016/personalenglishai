import assert from 'node:assert/strict'

import { createAssistantState } from '../src/pages/app/assistantState.ts'
import { stageCache } from '../src/stores/stageCache.ts'

function createMemoryStorage(): Storage {
  const entries = new Map<string, string>()
  return {
    get length() {
      return entries.size
    },
    clear() {
      entries.clear()
    },
    getItem(key: string) {
      return entries.get(key) ?? null
    },
    key(index: number) {
      return Array.from(entries.keys())[index] ?? null
    },
    removeItem(key: string) {
      entries.delete(key)
    },
    setItem(key: string, value: string) {
      entries.set(key, value)
    },
  }
}

async function main() {
  {
    const state = createAssistantState()

    state.applyStarter('帮我把这句话润色得更高级')

    assert.equal(state.composerText.value, '帮我把这句话润色得更高级')
    assert.equal(state.activeConversation.value.messages.length, 0)
  }

  {
    const state = createAssistantState()
    const initialCount = state.conversations.value.length
    const conversation = state.createConversation()

    assert.equal(state.conversations.value.length, initialCount + 1)
    assert.equal(state.activeConversationId.value, conversation.id)
    assert.equal(conversation.messages.length, 0)
  }

  {
    const state = createAssistantState()

    await state.sendMessage()

    assert.equal(state.activeConversation.value.messages.length, 0)
  }

  {
    const state = createAssistantState()
    const photo = new File(['photo'], 'sample-photo.png', { type: 'image/png' })
    const doc = new File(['doc'], 'outline.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })

    state.addAttachments([photo, doc])

    assert.equal(state.composerAttachments.value.length, 2)
    assert.equal(state.composerAttachments.value[0]?.name, 'sample-photo.png')
    assert.equal(state.composerAttachments.value[1]?.name, 'outline.docx')

    state.removeAttachment(state.composerAttachments.value[0]!.id)

    assert.equal(state.composerAttachments.value.length, 1)
    assert.equal(state.composerAttachments.value[0]?.name, 'outline.docx')
  }

  {
    let received:
      | {
          input: string
          conversationId: string
          studyStage?: string
          assistantMode?: string
          attachments: Array<{ name: string; kind: string; type: string }>
        }
      | undefined
    stageCache.value = 'postgrad'
    const state = createAssistantState({
      buildReply: async (request) => {
        received = {
          input: request.input,
          conversationId: request.conversationId,
          studyStage: request.studyStage,
          assistantMode: request.assistantMode,
          attachments: request.attachments.map((attachment) => ({
            name: attachment.name,
            kind: attachment.kind,
            type: attachment.type,
          })),
        }
        return 'ok'
      },
    })

    state.composerText.value = '解释附件里的内容'
    state.setAssistantMode('exam')
    state.addAttachments([
      new File(['img'], 'whiteboard.png', { type: 'image/png' }),
      new File(['pdf'], 'notes.pdf', { type: 'application/pdf' }),
    ])

    await state.sendMessage()

    assert.equal(received?.input, '解释附件里的内容')
    assert.equal(received?.conversationId, state.activeConversation.value.id)
    assert.equal(received?.studyStage, 'postgrad')
    assert.equal(received?.assistantMode, 'exam')
    assert.deepEqual(received?.attachments, [
      { name: 'whiteboard.png', kind: 'image', type: 'image/png' },
      { name: 'notes.pdf', kind: 'file', type: 'application/pdf' },
    ])
    assert.equal(state.assistantMode.value, 'exam')
    stageCache.value = null
  }

  {
    const state = createAssistantState()

    assert.equal(state.assistantMode.value, 'default')

    state.setAssistantMode('exam')
    assert.equal(state.assistantMode.value, 'exam')

    state.setAssistantMode('default')
    assert.equal(state.assistantMode.value, 'default')
  }

  {
    let receivedStudyStage: string | undefined
    stageCache.value = '__error__'
    const state = createAssistantState({
      buildReply: async (request) => {
        receivedStudyStage = request.studyStage
        return 'ok'
      },
    })

    state.composerText.value = '解释这个句子'
    await state.sendMessage()

    assert.equal(receivedStudyStage, undefined)
    stageCache.value = null
  }

  {
    const state = createAssistantState({
      buildReply: async (request) => `mock-reply:${request.input}`,
    })

    state.composerText.value = '解释这个单词'
    state.addAttachments([
      new File(['photo'], 'question.png', { type: 'image/png' }),
      new File(['pdf'], 'rubric.pdf', { type: 'application/pdf' }),
    ])
    const sendPromise = state.sendMessage()

    assert.equal(state.activeConversation.value.messages.length, 2)
    assert.equal(state.activeConversation.value.messages[0]?.role, 'user')
    assert.equal(state.activeConversation.value.messages[1]?.status, 'loading')
    assert.equal(state.activeConversation.value.messages[0]?.attachments?.length, 2)

    await sendPromise

    assert.equal(state.activeConversation.value.messages.length, 2)
    assert.equal(state.activeConversation.value.messages[1]?.role, 'assistant')
    assert.equal(state.activeConversation.value.messages[1]?.content, 'mock-reply:解释这个单词')
    assert.equal(state.activeConversation.value.messages[1]?.status, 'done')
    assert.equal(state.composerText.value, '')
    assert.equal(state.composerAttachments.value.length, 0)
  }

  {
    const state = createAssistantState({
      buildReply: async (input) => `mock-reply:${input}`,
    })

    state.addAttachments([new File(['image'], 'board.jpg', { type: 'image/jpeg' })])
    await state.sendMessage()

    assert.equal(state.activeConversation.value.messages.length, 2)
    assert.equal(state.activeConversation.value.messages[0]?.attachments?.[0]?.name, 'board.jpg')
  }

  {
    let shouldFail = true
    const state = createAssistantState({
      buildReply: async (request) => {
        if (shouldFail) {
          throw new Error(`mock-fail:${request.input}`)
        }
        return `mock-retry:${request.input}`
      },
    })

    state.composerText.value = '评价这段英文表达'
    await state.sendMessage()

    assert.equal(state.errorMessage.value, 'mock-fail:评价这段英文表达')
    assert.equal(state.canRetry.value, true)
    assert.equal(state.activeConversation.value.messages.length, 1)

    shouldFail = false
    await state.retryLastMessage()

    assert.equal(state.errorMessage.value, '')
    assert.equal(state.canRetry.value, false)
    assert.equal(state.activeConversation.value.messages.length, 3)
    assert.equal(state.activeConversation.value.messages[2]?.content, 'mock-retry:评价这段英文表达')
  }

  {
    const storage = createMemoryStorage()
    const state = createAssistantState({
      storage,
      buildReply: async (request) => `mock-reply:${request.input}`,
    })

    state.composerText.value = '讲解这个长难句'
    await state.sendMessage()
    const conversationId = state.activeConversationId.value

    const restored = createAssistantState({ storage })

    assert.equal(restored.activeConversationId.value, conversationId)
    assert.equal(restored.activeConversation.value.messages.length, 2)
    assert.equal(restored.activeConversation.value.messages[0]?.content, '讲解这个长难句')
    assert.equal(restored.activeConversation.value.messages[1]?.content, 'mock-reply:讲解这个长难句')
  }

  {
    const storage = createMemoryStorage()
    const state = createAssistantState({
      storage,
      buildReply: async (request) => `mock-reply:${request.input}`,
    })

    state.composerText.value = '解释附件'
    state.addAttachments([new File(['image'], 'board.jpg', { type: 'image/jpeg' })])
    await state.sendMessage()

    const restored = createAssistantState({ storage })

    assert.equal(restored.activeConversation.value.messages.length, 2)
    assert.equal(restored.activeConversation.value.messages[0]?.content, '解释附件')
    assert.equal(restored.activeConversation.value.messages[0]?.attachments, undefined)
  }

  console.log('assistant-state-ok')
}

await main()
