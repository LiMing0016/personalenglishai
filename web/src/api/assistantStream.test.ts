import test from 'node:test'
import assert from 'node:assert/strict'

import { parseAssistantStreamChunk } from './assistantStream.ts'

test('parseAssistantStreamChunk parses server-sent event data lines', () => {
  const events = parseAssistantStreamChunk(
    [
      'data: {"type":"message.delta","delta":"he"}',
      '',
      'data: {"type":"message.completed","content":"hello"}',
      '',
      '',
    ].join('\n'),
  )

  assert.deepEqual(events, [
    { type: 'message.delta', delta: 'he' },
    { type: 'message.completed', content: 'hello' },
  ])
})

test('parseAssistantStreamChunk ignores malformed events', () => {
  const events = parseAssistantStreamChunk('data: not-json\n\ndata: {"type":"run.completed"}\n\n')

  assert.deepEqual(events, [{ type: 'run.completed' }])
})

test('parseAssistantStreamChunk parses CRLF server-sent event separators', () => {
  const events = parseAssistantStreamChunk(
    'data: {"type":"run.started"}\r\n\r\ndata: {"type":"message.delta","delta":"hi"}\r\n\r\n',
  )

  assert.deepEqual(events, [{ type: 'run.started' }, { type: 'message.delta', delta: 'hi' }])
})
