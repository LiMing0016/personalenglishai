import test from 'node:test'
import assert from 'node:assert/strict'

import {
  ASSISTANT_MARKDOWN_THEME_STORAGE_KEY,
  readAssistantMarkdownTheme,
  writeAssistantMarkdownTheme,
} from './assistantMarkdownTheme.ts'

function createMemoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear() {
      values.clear()
    },
    getItem(key: string) {
      return values.get(key) ?? null
    },
    key(index: number) {
      return Array.from(values.keys())[index] ?? null
    },
    removeItem(key: string) {
      values.delete(key)
    },
    setItem(key: string, value: string) {
      values.set(key, value)
    },
  }
}

test('readAssistantMarkdownTheme defaults to marktext', () => {
  assert.equal(readAssistantMarkdownTheme(createMemoryStorage()), 'marktext')
})

test('readAssistantMarkdownTheme accepts stored milkdown value', () => {
  const storage = createMemoryStorage()
  storage.setItem(ASSISTANT_MARKDOWN_THEME_STORAGE_KEY, 'milkdown')

  assert.equal(readAssistantMarkdownTheme(storage), 'milkdown')
})

test('writeAssistantMarkdownTheme persists the selected theme', () => {
  const storage = createMemoryStorage()

  writeAssistantMarkdownTheme('milkdown', storage)

  assert.equal(storage.getItem(ASSISTANT_MARKDOWN_THEME_STORAGE_KEY), 'milkdown')
})
