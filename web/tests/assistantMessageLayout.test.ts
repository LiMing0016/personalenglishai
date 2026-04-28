import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const chatViewSource = readFileSync(new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url), 'utf8')

assert.ok(!chatViewSource.includes('message-role'), 'message role labels should not render in chat bubbles')
assert.ok(chatViewSource.includes('message-row--assistant'))
assert.ok(chatViewSource.includes('justify-content: flex-start;'))
assert.ok(chatViewSource.includes('message-row--user'))
assert.ok(chatViewSource.includes('justify-content: flex-end;'))
assert.ok(!chatViewSource.includes('background: linear-gradient(135deg, #047857'))
assert.ok(!chatViewSource.includes('box-shadow: 0 12px 30px'))
assert.ok(chatViewSource.includes('background: transparent;'))
