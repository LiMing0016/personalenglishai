import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const assistantPageSource = readFileSync(new URL('../src/pages/app/AssistantPage.vue', import.meta.url), 'utf8')
const sidebarSource = readFileSync(new URL('../src/components/assistant/AssistantSidebar.vue', import.meta.url), 'utf8')
const chatViewSource = readFileSync(new URL('../src/components/assistant/AssistantChatView.vue', import.meta.url), 'utf8')
const composerSource = readFileSync(new URL('../src/components/assistant/AssistantComposer.vue', import.meta.url), 'utf8')

assert.ok(assistantPageSource.includes('background: #f8fafc;'))
assert.ok(sidebarSource.includes('background: #ffffff;'))
assert.ok(chatViewSource.includes('color: #0f172a;'))
assert.ok(composerSource.includes('background: #ffffff;'))
