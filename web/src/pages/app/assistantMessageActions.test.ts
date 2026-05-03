import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildAskAssistantPrompt,
  buildPendingAssistantSelection,
  findRetryUserMessage,
  parsePendingAssistantSelection,
} from './assistantMessageActions.ts'
import type { AssistantMessage } from './assistantMock.ts'

function message(id: string, role: AssistantMessage['role'], content: string): AssistantMessage {
  return {
    id,
    role,
    content,
    status: 'done',
  }
}

test('buildAskAssistantPrompt wraps selected text in an editable question', () => {
  const prompt = buildAskAssistantPrompt('  What is good output?  ')

  assert.equal(prompt, '请帮我解释这段内容：\n\n「What is good output?」')
})

test('buildAskAssistantPrompt returns empty string for blank selection', () => {
  assert.equal(buildAskAssistantPrompt('   \n\t  '), '')
})

test('buildPendingAssistantSelection creates page selection context', () => {
  assert.deepEqual(buildPendingAssistantSelection('  selected text  '), {
    text: 'selected text',
    source: 'page_selection',
  })
  assert.equal(buildPendingAssistantSelection('   '), null)
})

test('parsePendingAssistantSelection rejects invalid storage values', () => {
  assert.deepEqual(parsePendingAssistantSelection('{"text":"hello","source":"ignored"}'), {
    text: 'hello',
    source: 'page_selection',
  })
  assert.equal(parsePendingAssistantSelection('not-json'), null)
  assert.equal(parsePendingAssistantSelection('{"text":"   "}'), null)
})

test('findRetryUserMessage returns the closest previous user message', () => {
  const messages = [
    message('u1', 'user', 'first'),
    message('a1', 'assistant', 'first reply'),
    message('u2', 'user', 'second'),
    message('a2', 'assistant', 'second reply'),
  ]

  const retryMessage = findRetryUserMessage(messages, 'a2')

  assert.equal(retryMessage?.id, 'u2')
  assert.equal(retryMessage?.content, 'second')
})

test('findRetryUserMessage ignores loading and missing assistant messages', () => {
  const messages = [
    message('u1', 'user', 'first'),
    { ...message('a1', 'assistant', 'loading'), status: 'loading' as const },
  ]

  assert.equal(findRetryUserMessage(messages, 'a1'), null)
  assert.equal(findRetryUserMessage(messages, 'missing'), null)
})
