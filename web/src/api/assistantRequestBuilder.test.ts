import assert from 'node:assert/strict'
import test from 'node:test'

import { toAssistantAgentRequest } from './assistantRequestBuilder.ts'

test('forwards the selected agent runtime mode', () => {
  const request = toAssistantAgentRequest({
    input: 'hive 是什么意思？',
    conversationId: 'conv-1',
    agentMode: 'single_agent_raw',
    attachments: [],
  })

  assert.equal(request.agentMode, 'single_agent_raw')
})
