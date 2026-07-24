import assert from 'node:assert/strict'
import test from 'node:test'

import { toAssistantAgentRequest } from './assistantRequestBuilder.ts'
import type { AgentMode } from '../types/assistantRequest.ts'

test('forwards the selected agent runtime mode', () => {
  const request = toAssistantAgentRequest({
    input: 'hive 是什么意思？',
    conversationId: 'conv-1',
    agentMode: 'single_agent_raw',
    attachments: [],
  })

  assert.equal(request.agentMode, 'single_agent_raw')
})

// @ts-expect-error 联网能力属于原始模型，不再暴露第三种运行模式
const removedToolMode: AgentMode = 'single_agent_tools'
void removedToolMode
