import assert from 'node:assert/strict'
import test from 'node:test'

import { createMemoryAssistantAttachmentBlobStore } from './assistantAttachmentStore.ts'
import { createAssistantState } from './assistantState.ts'

test('switching agent mode creates a fresh conversation', () => {
  const state = createAssistantState({
    buildReply: async () => 'unused',
    attachmentStore: createMemoryAssistantAttachmentBlobStore(),
  })
  const previousConversationId = state.activeConversationId.value

  state.setAgentMode('single_agent_raw')

  assert.equal(state.agentMode.value, 'single_agent_raw')
  assert.notEqual(state.activeConversationId.value, previousConversationId)
  assert.equal(state.activeConversation.value.messages.length, 0)
})

test('selecting the same agent mode does not create another conversation', () => {
  const state = createAssistantState({
    buildReply: async () => 'unused',
    attachmentStore: createMemoryAssistantAttachmentBlobStore(),
  })
  const previousConversationId = state.activeConversationId.value

  state.setAgentMode('multi_agent')

  assert.equal(state.activeConversationId.value, previousConversationId)
})
