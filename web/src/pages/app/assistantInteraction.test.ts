import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { toAssistantAgentRequest } from '../../api/assistantRequestBuilder.ts'
import type { AssistantInteractionContext } from '../../types/assistantRequest.ts'

const basePayload = {
  input: '这个句子为什么用过去时？',
  conversationId: 'conversation-1',
  assistantMode: 'default' as const,
  attachments: [],
}

const reorderInteraction: AssistantInteractionContext = {
  source: 'quick_action',
  uiIntent: 'start_practice',
  context: { exerciseType: 'sentence_reorder' },
}

test('ordinary questions do not acquire a UI intent', () => {
  const request = toAssistantAgentRequest(basePayload)

  assert.equal(request.interaction, undefined)
})

test('explicit practice interaction is preserved in the agent request', () => {
  const request = toAssistantAgentRequest({
    ...basePayload,
    input: '开始重组成句练习',
    interaction: reorderInteraction,
  })

  assert.deepEqual(request.interaction, reorderInteraction)
  assert.equal(request.message.text, '开始重组成句练习')
})

test('failed explicit interactions keep their context when retried', () => {
  const source = readFileSync(new URL('./assistantState.ts', import.meta.url), 'utf8')

  assert.match(source, /lastFailedInteraction/)
  assert.match(source, /sendPrompt\(lastFailedPrompt\.value, lastFailedAttachments\.value, lastFailedInteraction\.value\)/)
  assert.match(source, /interaction,\s*\n\s*attachments/)
})

test('practice goal exposes a structured reorder action instead of encoding intent in prompt', () => {
  const source = readFileSync(
    new URL('../../components/assistant/AssistantStarterCards.vue', import.meta.url),
    'utf8',
  )

  assert.match(source, /开始重组成句练习/)
  assert.match(source, /uiIntent:\s*'start_practice'/)
  assert.match(source, /exerciseType:\s*'sentence_reorder'/)
  assert.match(source, /selectedGoal === 'practice'/)
})
