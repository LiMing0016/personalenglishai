import assert from 'node:assert/strict'

import { createAssistantState } from '../src/pages/app/assistantState.ts'

function createMemoryStorage(): Storage {
  const entries = new Map<string, string>()
  return {
    get length() { return entries.size },
    clear() { entries.clear() },
    getItem(key) { return entries.get(key) ?? null },
    key(index) { return Array.from(entries.keys())[index] ?? null },
    removeItem(key) { entries.delete(key) },
    setItem(key, value) { entries.set(key, value) },
  }
}

const storage = createMemoryStorage()
const state = createAssistantState({ storage })
const conversation = state.createConversation()

assert.equal(typeof conversation.createdAt, 'number')
assert.ok(conversation.createdAt <= conversation.updatedAt)

const restored = createAssistantState({ storage })
assert.equal(restored.activeConversation.value.createdAt, conversation.createdAt)

console.log('assistant-conversation-created-at-ok')
